package net.spartanb312.grunt.web

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.event.events.FinalizeEvent
import net.spartanb312.grunt.event.events.ProcessEvent
import net.spartanb312.grunt.event.events.TransformerEvent
import net.spartanb312.grunt.process.Transformers
import net.spartanb312.grunt.process.resource.ResourceCache
import net.spartanb312.grunt.process.transformers.PostProcessTransformer
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.jar.JarFile
import kotlin.system.measureTimeMillis

class ObfuscationSession(
    val id: String,
    rootDir: File
) {

    enum class ProjectScope {
        INPUT, OUTPUT
    }

    enum class Status {
        IDLE, UPLOADING, READY, RUNNING, COMPLETED, ERROR
    }

    companion object {
        private val gsonPretty = GsonBuilder().setPrettyPrinting().create()
    }

    val sessionDir: File = rootDir.absoluteFile
    val configDir = File(sessionDir, "config")
    val inputDir = File(sessionDir, "input")
    val librariesDir = File(sessionDir, "libraries")
    val assetsDir = File(sessionDir, "assets")
    val outputDir = File(sessionDir, "output")

    @Volatile
    var status: Status = Status.IDLE

    @Volatile
    var currentStep: String = ""

    @Volatile
    var progress: Int = 0

    @Volatile
    var totalSteps: Int = 0

    @Volatile
    var errorMessage: String? = null

    @Volatile
    var configFilePath: String? = null

    @Volatile
    var inputJarPath: String? = null

    @Volatile
    var outputJarPath: String? = null

    @Volatile
    var inputDisplayName: String? = null

    @Volatile
    var configDisplayName: String? = null

    @Volatile
    var inputClassList: List<String>? = null

    @Volatile
    var finalClassList: List<String>? = null

    val consoleLogs = CopyOnWriteArrayList<String>()

    private val decompiledCache = ConcurrentHashMap<String, String>()
    private val libraryFiles = CopyOnWriteArrayList<String>()
    private val assetFiles = ConcurrentHashMap<String, String>()

    var onLogMessage: ((String) -> Unit)? = null
    var onProgressUpdate: ((String) -> Unit)? = null

    init {
        configDir.mkdirs()
        inputDir.mkdirs()
        librariesDir.mkdirs()
        assetsDir.mkdirs()
        outputDir.mkdirs()
    }

    fun log(msg: String) {
        consoleLogs.add(msg)
        onLogMessage?.invoke(msg)
    }

    fun saveConfig(jsonObject: JsonObject, fileName: String = "config.json"): File {
        val file = File(configDir, fileName).absoluteFile
        if (!file.parentFile.exists()) file.parentFile.mkdirs()
        file.writeText(gsonPretty.toJson(jsonObject), Charsets.UTF_8)
        configFilePath = file.absolutePath
        configDisplayName = file.name
        discardPreviousResult(Status.READY)
        return file
    }

    fun loadConfigJson(): JsonObject {
        val path = configFilePath ?: throw IllegalStateException("No config uploaded")
        val file = File(path)
        if (!file.exists()) throw IllegalStateException("Config file not found")
        return JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
    }

    fun replaceInput(file: File) {
        val target = if (file.parentFile?.absolutePath == inputDir.absolutePath) {
            inputDir.listFiles()?.forEach { existing ->
                if (existing.absolutePath != file.absolutePath) {
                    existing.deleteRecursively()
                }
            }
            file.absoluteFile
        } else {
            clearDir(inputDir)
            val copied = File(inputDir, file.name)
            file.copyTo(copied, overwrite = true)
            copied.absoluteFile
        }
        inputJarPath = target.absolutePath
        inputDisplayName = target.name
        setInputClasses(readJarClasses(target))
        discardPreviousResult(Status.READY)
    }

    fun addLibraries(files: List<File>) {
        files.forEach { file ->
            val stored = File(librariesDir, file.name).absolutePath
            libraryFiles.removeIf { File(it).name == file.name }
            libraryFiles.add(stored)
        }
        discardPreviousResult(Status.READY)
    }

    fun addAssets(files: List<File>) {
        files.forEach { file ->
            assetFiles[file.name] = file.absolutePath
        }
        discardPreviousResult(Status.READY)
    }

    fun getLibraryNames(): List<String> = libraryFiles.map { File(it).name }.sorted()

    fun getLibraryPaths(): List<String> = libraryFiles.toList()

    fun getAssetNames(): List<String> = assetFiles.keys.sorted()

    fun resolveAssetPath(name: String?): String? {
        if (name.isNullOrBlank()) return null
        return assetFiles[name]
    }

    fun hasUploadedConfig(): Boolean = configFilePath?.let { File(it).exists() } == true

    fun hasUploadedInput(): Boolean = inputJarPath?.let { File(it).exists() } == true

    fun hasOutput(): Boolean = outputJarPath?.let { File(it).exists() } == true

    fun discardPreviousResult(nextStatus: Status = status) {
        outputJarPath = null
        finalClassList = null
        clearCachedSources(ProjectScope.OUTPUT)
        clearDir(outputDir)
        errorMessage = null
        if (status != Status.RUNNING) {
            status = nextStatus
        }
    }

    fun setInputClasses(classes: List<String>) {
        inputClassList = classes.sorted()
        clearCachedSources(ProjectScope.INPUT)
    }

    fun getProjectClasses(scope: ProjectScope): List<String>? {
        return when (scope) {
            ProjectScope.INPUT -> inputClassList
            ProjectScope.OUTPUT -> finalClassList
        }
    }

    fun decompileClass(scope: ProjectScope, className: String): String {
        val normalizedClass = normalizeClassName(className)
        val cacheKey = "${scope.name}:$normalizedClass"
        decompiledCache[cacheKey]?.let { return it }

        val jarPath = when (scope) {
            ProjectScope.INPUT -> inputJarPath
            ProjectScope.OUTPUT -> outputJarPath
        } ?: throw IllegalStateException("No ${scope.name.lowercase()} JAR available")

        val jarFile = File(jarPath)
        if (!jarFile.exists()) {
            throw IllegalStateException("JAR file not found: $jarPath")
        }

        val allClasses = readAllClassBytes(jarFile)
        val classBytes = allClasses[normalizedClass] ?: throw NoSuchElementException("Class not found")
        return Decompiler.decompile(normalizedClass, classBytes, allClasses).also {
            decompiledCache[cacheKey] = it
        }
    }

    fun runObfuscation(prepareConfig: () -> Unit) {
        status = Status.RUNNING
        currentStep = "Preparing..."
        progress = 0
        totalSteps = 0
        errorMessage = null
        finalClassList = null
        clearCachedSources(ProjectScope.OUTPUT)
        consoleLogs.clear()

        try {
            prepareConfig()
            ProcessEvent.Before.post()
            val totalTime = measureTimeMillis {
                val inputPath = inputJarPath ?: throw IllegalStateException("No input JAR uploaded")
                ResourceCache(inputPath, Configs.Settings.libraries).apply {
                    log("Reading JAR: $inputPath")
                    currentStep = "Reading JAR..."
                    readJar()

                    val enabledTransformers = Transformers.sortedBy { it.order }
                        .filter { it.enabled && it != PostProcessTransformer }
                    totalSteps = enabledTransformers.size + 1

                    val timeUsage = mutableMapOf<String, Long>()
                    val obfTime = measureTimeMillis {
                        log("Processing...")

                        enabledTransformers.forEachIndexed { index, transformer ->
                            val preEvent = TransformerEvent.Before(transformer, this)
                            preEvent.post()
                            if (preEvent.cancelled) return@forEachIndexed

                            val actualTransformer = preEvent.transformer
                            currentStep = actualTransformer.name
                            progress = ((index + 1).toFloat() / totalSteps * 100).toInt()
                            log("Running transformer: ${actualTransformer.name} (${index + 1}/$totalSteps)")
                            onProgressUpdate?.invoke(
                                """{"step":"${actualTransformer.name}","current":${index + 1},"total":$totalSteps,"progress":$progress}"""
                            )

                            val startTime = System.currentTimeMillis()
                            with(actualTransformer) { transform() }
                            timeUsage[actualTransformer.name] = System.currentTimeMillis() - startTime

                            TransformerEvent.After(actualTransformer, this).post()
                        }

                        currentStep = "PostProcess"
                        progress = 95
                        log("Running PostProcess...")
                        onProgressUpdate?.invoke(
                            """{"step":"PostProcess","current":$totalSteps,"total":$totalSteps,"progress":95}"""
                        )
                        with(PostProcessTransformer) {
                            transform()
                            FinalizeEvent.Before(this@apply).post()
                            finalize()
                            FinalizeEvent.After(this@apply).post()
                        }
                    }

                    log("Took $obfTime ms to process!")
                    if (Configs.Settings.timeUsage) {
                        timeUsage.forEach { (name, duration) ->
                            log("   $name $duration ms")
                        }
                    }

                    finalClassList = classes.keys.sorted()

                    val outputPath = Configs.Settings.output
                    log("Dumping to $outputPath")
                    currentStep = "Dumping..."
                    progress = 98
                    dumpJar(outputPath)
                    outputJarPath = outputPath
                }
            }
            ProcessEvent.After.post()
            log("Finished in $totalTime ms!")
            currentStep = "Completed"
            progress = 100
            status = Status.COMPLETED
            onProgressUpdate?.invoke(
                """{"step":"Completed","current":$totalSteps,"total":$totalSteps,"progress":100}"""
            )
        } catch (e: Exception) {
            status = Status.ERROR
            errorMessage = e.message ?: "Unknown error"
            log("ERROR: ${e.message}")
            e.printStackTrace()
            onProgressUpdate?.invoke(
                """{"step":"Error","error":"${e.message?.replace("\\", "\\\\")?.replace("\"", "\\\"")}"}"""
            )
        }
    }

    private fun normalizeClassName(className: String): String {
        val trimmed = className.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("Class name is empty")
        if (trimmed.contains("..") || trimmed.contains(":") || trimmed.startsWith("/") || trimmed.startsWith("\\")) {
            throw IllegalArgumentException("Illegal class name")
        }

        val withoutSuffix = trimmed.removeSuffix(".class")
        val normalized = if (withoutSuffix.contains('/')) withoutSuffix else withoutSuffix.replace('.', '/')
        if (normalized.isBlank()) throw IllegalArgumentException("Illegal class name")
        return normalized
    }

    private fun readJarClasses(jarFile: File): List<String> {
        return JarFile(jarFile).use { jar ->
            jar.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".class") }
                .map { it.name.removeSuffix(".class") }
                .sorted()
                .toList()
        }
    }

    private fun readAllClassBytes(jarFile: File): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        JarFile(jarFile).use { jar ->
            jar.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".class") }
                .forEach { entry ->
                    val className = entry.name.removeSuffix(".class")
                    val bytes = jar.getInputStream(entry).use { it.readBytes() }
                    result[className] = bytes
                }
        }
        return result
    }

    private fun clearCachedSources(scope: ProjectScope) {
        val prefix = "${scope.name}:"
        decompiledCache.keys.toList().forEach { key ->
            if (key.startsWith(prefix)) {
                decompiledCache.remove(key)
            }
        }
    }

    private fun clearDir(dir: File) {
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.deleteRecursively() }
        } else {
            dir.mkdirs()
        }
    }
}
