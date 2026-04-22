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
    var configObjectKey: String? = null

    @Volatile
    var inputJarPath: String? = null

    @Volatile
    var inputObjectKey: String? = null

    @Volatile
    var outputJarPath: String? = null

    @Volatile
    var outputObjectKey: String? = null

    @Volatile
    var inputDisplayName: String? = null

    @Volatile
    var configDisplayName: String? = null

    @Volatile
    var inputClassList: List<String>? = null

    @Volatile
    var finalClassList: List<String>? = null

    @Volatile
    var accessProfile: SessionAccessProfile = SessionAccessProfile.SECURE

    @Volatile
    var controlPlane: String = "embedded-web"

    @Volatile
    var workerPlane: String = "local-worker"

    @Volatile
    var ownerUsername: String? = null

    val consoleLogs = CopyOnWriteArrayList<String>()

    private val decompiledCache = ConcurrentHashMap<String, String>()
    private val libraryFiles = CopyOnWriteArrayList<String>()
    private val assetFiles = ConcurrentHashMap<String, String>()
    private val libraryObjectRefs = ConcurrentHashMap<String, String>()
    private val assetObjectRefs = ConcurrentHashMap<String, String>()

    var onLogMessage: ((String) -> Unit)? = null
    var onProgressUpdate: ((String) -> Unit)? = null
    var onStateChanged: ((ObfuscationSession) -> Unit)? = null

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
        notifyStateChanged()
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
        notifyStateChanged()
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
        notifyStateChanged()
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
        notifyStateChanged()
    }

    fun replaceOutput(file: File) {
        val source = file.absoluteFile
        val copied = File(outputDir, source.name).absoluteFile
        val sameTarget = source.absolutePath == copied.absolutePath

        if (sameTarget) {
            require(source.exists()) { "Output file not found: ${source.absolutePath}" }
            outputDir.listFiles()
                ?.filter { it.absolutePath != source.absolutePath }
                ?.forEach { it.deleteRecursively() }
        } else {
            clearDir(outputDir)
            source.copyTo(copied, overwrite = true)
        }

        outputJarPath = copied.absolutePath
        finalClassList = readJarClasses(copied)
        clearCachedSources(ProjectScope.OUTPUT)
        notifyStateChanged()
    }

    fun getLibraryPaths(): List<String> = libraryFiles.toList()

    fun getLibraryNames(): List<String> = (libraryFiles.map { File(it).name } + libraryObjectRefs.keys).distinct().sorted()

    fun getAssetNames(): List<String> = (assetFiles.keys + assetObjectRefs.keys).distinct().sorted()

    fun getLibraryObjectRefs(): Map<String, String> = libraryObjectRefs.toSortedMap()

    fun getAssetObjectRefs(): Map<String, String> = assetObjectRefs.toSortedMap()

    fun resolveAssetPath(name: String?): String? {
        if (name.isNullOrBlank()) return null
        return assetFiles[name]
    }

    fun hasUploadedConfig(): Boolean = configFilePath?.let { File(it).exists() } == true

    fun hasUploadedInput(): Boolean = inputJarPath?.let { File(it).exists() } == true

    fun hasOutput(): Boolean = outputJarPath?.let { File(it).exists() } == true

    fun discardPreviousResult(nextStatus: Status = status) {
        outputJarPath = null
        outputObjectKey = null
        finalClassList = null
        clearCachedSources(ProjectScope.OUTPUT)
        clearDir(outputDir)
        errorMessage = null
        if (status != Status.RUNNING) {
            status = nextStatus
        }
        notifyStateChanged()
    }

    fun setInputClasses(classes: List<String>) {
        inputClassList = classes.sorted()
        clearCachedSources(ProjectScope.INPUT)
        notifyStateChanged()
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

    fun configureControlPlane(
        accessProfile: SessionAccessProfile,
        controlPlane: String,
        workerPlane: String,
        ownerUsername: String? = this.ownerUsername
    ) {
        this.accessProfile = accessProfile
        this.controlPlane = controlPlane
        this.workerPlane = workerPlane
        this.ownerUsername = ownerUsername
        notifyStateChanged()
    }

    fun restorePersistedState(state: PersistedSessionState) {
        accessProfile = SessionAccessProfile.parseOrNull(state.policyMode) ?: SessionAccessProfile.SECURE
        controlPlane = state.controlPlane
        workerPlane = state.workerPlane
        ownerUsername = state.ownerUsername
        status = runCatching { Status.valueOf(state.status) }.getOrElse { Status.IDLE }
        currentStep = state.currentStep
        progress = state.progress
        totalSteps = state.totalSteps
        errorMessage = state.errorMessage
        configDisplayName = state.configFileName
        inputDisplayName = state.inputFileName
        configObjectKey = state.configObjectKey
        inputObjectKey = state.inputObjectKey
        outputObjectKey = state.outputObjectKey
        outputJarPath = resolvePersistedPath(outputDir, state.outputFileName)
        inputJarPath = resolvePersistedPath(inputDir, state.inputFileName)
        configFilePath = resolvePersistedPath(configDir, state.configFileName)

        libraryObjectRefs.clear()
        libraryObjectRefs.putAll(state.libraryObjectRefs)
        assetObjectRefs.clear()
        assetObjectRefs.putAll(state.assetObjectRefs)

        libraryFiles.removeIf { true }
        state.libraryFiles.forEach { name ->
            resolvePersistedPath(librariesDir, name)?.let { libraryFiles.add(it) }
        }

        assetFiles.clear()
        state.assetFiles.forEach { name ->
            resolvePersistedPath(assetsDir, name)?.let { assetFiles[name] = it }
        }
    }

    fun applyExternalState(
        status: String,
        currentStep: String,
        progress: Int,
        totalSteps: Int,
        errorMessage: String?,
        outputObjectKey: String?,
        logs: List<String>
    ) {
        var changed = false

        logs.drop(consoleLogs.size).forEach { line ->
            consoleLogs.add(line)
            onLogMessage?.invoke(line)
            changed = true
        }

        val parsedStatus = runCatching { Status.valueOf(status) }.getOrNull()
        if (parsedStatus != null && this.status != parsedStatus) {
            this.status = parsedStatus
            changed = true
        }
        if (this.currentStep != currentStep) {
            this.currentStep = currentStep
            changed = true
        }
        if (this.progress != progress) {
            this.progress = progress
            changed = true
        }
        if (this.totalSteps != totalSteps) {
            this.totalSteps = totalSteps
            changed = true
        }
        if (this.errorMessage != errorMessage) {
            this.errorMessage = errorMessage
            changed = true
        }
        if (!outputObjectKey.isNullOrBlank() && this.outputObjectKey != outputObjectKey) {
            this.outputObjectKey = outputObjectKey
            changed = true
        }

        if (changed) {
            onProgressUpdate?.invoke(
                """{"step":"${escapeJson(this.currentStep)}","current":0,"total":${this.totalSteps},"progress":${this.progress},"status":"${this.status.name}"}"""
            )
            notifyStateChanged()
        }
    }

    fun bindConfigObjectKey(objectKey: String?) {
        configObjectKey = objectKey
        notifyStateChanged()
    }

    fun bindInputObjectKey(objectKey: String?) {
        inputObjectKey = objectKey
        notifyStateChanged()
    }

    fun bindOutputObjectKey(objectKey: String?) {
        outputObjectKey = objectKey
        notifyStateChanged()
    }

    fun putLibraryObjectRef(fileName: String, objectKey: String) {
        libraryObjectRefs[fileName] = objectKey
        notifyStateChanged()
    }

    fun putAssetObjectRef(fileName: String, objectKey: String) {
        assetObjectRefs[fileName] = objectKey
        notifyStateChanged()
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
        notifyStateChanged()
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
            notifyStateChanged()
            emitProgress("Completed", 100)
        } catch (throwable: Throwable) {
            status = Status.ERROR
            errorMessage = throwable.message ?: throwable::class.java.simpleName ?: "Unknown error"
            log("ERROR: ${errorMessage}")
            throwable.printStackTrace()
            notifyStateChanged()
            onProgressUpdate?.invoke(
                """{"step":"Error","error":"${escapeJson(errorMessage ?: "Unknown error")}"}"""
            )
        } finally {
            Logger = previousLogger
        }
    }

    private fun emitProgress(step: String, percentage: Int) {
        notifyStateChanged()
        onProgressUpdate?.invoke(
            """{"step":"${escapeJson(step)}","current":0,"total":$totalSteps,"progress":$percentage,"status":"${status.name}"}"""
        )
    }

    private fun notifyStateChanged() {
        onStateChanged?.invoke(this)
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

    private fun resolvePersistedPath(dir: File, fileName: String?): String? {
        if (fileName.isNullOrBlank()) return null
        val file = File(dir, fileName).absoluteFile
        return file.absolutePath.takeIf { file.exists() }
    }
}
