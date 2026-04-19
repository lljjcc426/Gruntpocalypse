package net.spartanb312.grunteon.obfuscator.web

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.ObfConfig
import net.spartanb312.grunteon.obfuscator.util.Logger
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
        IDLE, READY, RUNNING, COMPLETED, ERROR
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

    fun log(message: String) {
        consoleLogs.add(message)
        onLogMessage?.invoke(message)
    }

    fun saveConfig(jsonObject: JsonObject, fileName: String = "config.json"): File {
        val file = File(configDir, fileName).absoluteFile
        file.parentFile?.mkdirs()
        file.writeText(gsonPretty.toJson(jsonObject), Charsets.UTF_8)
        configFilePath = file.absolutePath
        configDisplayName = file.name
        discardPreviousResult(Status.READY)
        return file
    }

    fun loadConfigJson(): JsonObject {
        val path = configFilePath ?: throw IllegalStateException("No config uploaded")
        val file = File(path)
        require(file.exists()) { "Config file not found" }
        return JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
    }

    fun replaceInput(file: File) {
        val source = file.absoluteFile
        val copied = File(inputDir, source.name).absoluteFile
        val sameTarget = source.absolutePath == copied.absolutePath

        if (sameTarget) {
            require(source.exists()) { "Uploaded input file not found: ${source.absolutePath}" }
            inputDir.listFiles()
                ?.filter { it.absolutePath != source.absolutePath }
                ?.forEach { it.deleteRecursively() }
        } else {
            clearDir(inputDir)
            source.copyTo(copied, overwrite = true)
        }

        inputJarPath = copied.absolutePath
        inputDisplayName = copied.name
        setInputClasses(readJarClasses(copied))
        discardPreviousResult(Status.READY)
    }

    fun addLibraries(files: List<File>) {
        files.forEach { file ->
            val target = File(librariesDir, file.name)
            if (file.absolutePath != target.absolutePath) {
                file.copyTo(target, overwrite = true)
            }
            libraryFiles.removeIf { File(it).name == target.name }
            libraryFiles.add(target.absolutePath)
        }
        discardPreviousResult(Status.READY)
    }

    fun addAssets(files: List<File>) {
        files.forEach { file ->
            val target = File(assetsDir, file.name)
            if (file.absolutePath != target.absolutePath) {
                file.copyTo(target, overwrite = true)
            }
            assetFiles[target.name] = target.absolutePath
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
        val normalized = normalizeClassName(className)
        val cacheKey = "${scope.name}:$normalized"
        decompiledCache[cacheKey]?.let { return it }

        val jarPath = when (scope) {
            ProjectScope.INPUT -> inputJarPath
            ProjectScope.OUTPUT -> outputJarPath
        } ?: throw IllegalStateException("No ${scope.name.lowercase()} JAR available")

        val jarFile = File(jarPath)
        require(jarFile.exists()) { "JAR file not found: $jarPath" }

        val allClasses = readAllClassBytes(jarFile)
        val classBytes = allClasses[normalized] ?: throw NoSuchElementException("Class not found")
        return Decompiler.decompile(normalized, classBytes, allClasses).also {
            decompiledCache[cacheKey] = it
        }
    }

    fun runObfuscation(config: ObfConfig) {
        status = Status.RUNNING
        currentStep = "Preparing"
        progress = 0
        errorMessage = null
        finalClassList = null
        clearCachedSources(ProjectScope.OUTPUT)
        clearDir(outputDir)
        consoleLogs.clear()
        emitProgress("Preparing", 0)

        val previousLogger = Logger
        Logger = SessionLogger(this, previousLogger)

        try {
            val enabledTransformers = config.transformerConfigs.size
            totalSteps = enabledTransformers + 2
            currentStep = "Creating instance"
            progress = 10
            emitProgress(currentStep, progress)

            val totalTime = measureTimeMillis {
                val instance = Grunteon.create(config)
                currentStep = "Obfuscating"
                progress = if (enabledTransformers == 0) 75 else 35
                emitProgress(currentStep, progress)
                instance.execute()
            }

            currentStep = "Indexing output"
            progress = 95
            emitProgress(currentStep, progress)

            outputJarPath = config.output
            outputJarPath?.let { path ->
                finalClassList = readJarClasses(File(path))
            }

            if (config.printTimeUsage) {
                log("Finished in $totalTime ms")
            }
            currentStep = "Completed"
            progress = 100
            status = Status.COMPLETED
            emitProgress("Completed", 100)
        } catch (throwable: Throwable) {
            status = Status.ERROR
            errorMessage = throwable.message ?: throwable::class.java.simpleName ?: "Unknown error"
            log("ERROR: ${errorMessage}")
            throwable.printStackTrace()
            onProgressUpdate?.invoke(
                """{"step":"Error","error":"${escapeJson(errorMessage ?: "Unknown error")}"}"""
            )
        } finally {
            Logger = previousLogger
        }
    }

    private fun emitProgress(step: String, percentage: Int) {
        onProgressUpdate?.invoke(
            """{"step":"${escapeJson(step)}","current":0,"total":$totalSteps,"progress":$percentage,"status":"${status.name}"}"""
        )
    }

    private fun normalizeClassName(className: String): String {
        val trimmed = className.trim()
        require(trimmed.isNotEmpty()) { "Class name is empty" }
        require(!trimmed.contains("..") && !trimmed.contains(":")) { "Illegal class name" }
        require(!trimmed.startsWith("/") && !trimmed.startsWith("\\")) { "Illegal class name" }
        val withoutSuffix = trimmed.removeSuffix(".class")
        return if (withoutSuffix.contains('/')) withoutSuffix else withoutSuffix.replace('.', '/')
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
                    result[entry.name.removeSuffix(".class")] = jar.getInputStream(entry).use { it.readBytes() }
                }
        }
        return result
    }

    private fun clearCachedSources(scope: ProjectScope) {
        val prefix = "${scope.name}:"
        decompiledCache.keys.removeIf { it.startsWith(prefix) }
    }

    private fun clearDir(dir: File) {
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.deleteRecursively() }
        } else {
            dir.mkdirs()
        }
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }
}
