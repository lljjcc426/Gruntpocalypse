package net.spartanb312.grunteon.obfuscator

import com.google.gson.JsonParser
import net.spartanb312.everett.bootstrap.ExternalClassLoader
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.logging.SimpleLogger
import net.spartanb312.grunteon.obfuscator.web.WebServer
import net.spartanb312.grunteon.obfuscator.web.WebConfigAdapter
import java.io.File
import java.nio.file.Path as JPath
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.*
import java.util.jar.JarFile
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.DurationUnit
import kotlin.time.measureTime

/**
 * Grunteon
 * A JVM bytecode obfuscator
 * 3rd generation of Grunt
 */
const val VERSION = "3.0.0"
const val SUBTITLE = "build 260415"
const val GITHUB = "https://github.com/SpartanB312/Grunt"

fun main(args: Array<String>) {
    if ("--silent" !in args) {
        Logger = SimpleLogger(
            "Grunteon",
            "logs/${SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(Date())}.txt"
        )
    }
    println(
        """
             ________  __________   ____ ___   _______    ___________
            /  _____/  \______   \ |    |   \  \      \   \__    ___/
           /   \  ___   |       _/ |    |   /  /   |   \    |    |   
           \    \_\  \  |    |   \ |    |  /  /    |    \   |    |   
            \______  /  |____|_  / |______/   \____|__  /   |____|   
        """.trimIndent()
    )
    println("==========================================================")
    println(" Grunteon $VERSION [${SUBTITLE}]")
    println(" GitHub: $GITHUB")
    println("==========================================================")

    Logger.info("Initializing obfuscator...")
    initializeExternalExtensions()

    val useWeb = args.isEmpty() || args.any { it == "--web" || it == "-server" }
    if (useWeb) {
        val requestedPort = args.firstOrNull { it.startsWith("--port=") }
            ?.substringAfter("=")
            ?.toIntOrNull()
            ?: 8080
        val port = findAvailablePort(requestedPort)
        if (port != requestedPort) {
            Logger.warn("Port $requestedPort is already in use, switched to port $port")
            println("Port $requestedPort is already in use, switched to port $port")
        }
        Logger.info("Starting Grunteon Web UI on port $port")
        WebServer.start(port)
        return
    }

    val configPath = args.firstOrNull { it.endsWith(".json", ignoreCase = true) } ?: "config.json"
    val config = loadConfig(Path(configPath))
    val instance = Grunteon.create(config)

    measureTime {
        instance.execute()
    }.toDouble(DurationUnit.MILLISECONDS).also { time ->
        if (config.printTimeUsage) {
            println("Execution time: ${"%.2f".format(time)} ms")
        }
    }
}

private fun initializeExternalExtensions() {
    val loader = ExternalClassLoader("grunteon-main-extensions", Thread.currentThread().contextClassLoader)
    scanExtensions(loader, "modules", "module")
    scanExtensions(loader, "plugins", "plugin")
}

private fun scanExtensions(loader: ExternalClassLoader, directoryName: String, kind: String) {
    val directory = File(directoryName)
    if (!directory.isDirectory) return
    val jars = directory.listFiles { file -> file.isFile && file.name.endsWith(".jar", true) }
        ?.sortedBy { it.name.lowercase(Locale.getDefault()) }
        .orEmpty()
    if (jars.isEmpty()) return
    Logger.info("Scanning ${kind}s from ${directory.absolutePath}")
    jars.forEach { jar ->
        runCatching {
            loader.loadJar(jar)
            val entryPoint = readEntryPoint(jar)
            if (entryPoint.isNullOrBlank()) {
                Logger.info(" - Loaded $kind jar without entry point: ${jar.name}")
            } else {
                Logger.info(" - Initializing $kind: $entryPoint")
                ExternalClassLoader.invokeKotlinObjectField(loader.loadClass(entryPoint))
            }
        }.onFailure { throwable ->
            Logger.warn("Failed to initialize ${kind} jar ${jar.name}: ${throwable.message}")
        }
    }
}

private fun readEntryPoint(jar: File): String? {
    JarFile(jar).use { jarFile ->
        val manifest = jarFile.manifest ?: return null
        return manifest.mainAttributes.getValue("Entry-Point")
            ?: manifest.mainAttributes.getValue("EntryPoint")
    }
}

private fun loadConfig(path: JPath): ObfConfig {
    val text = path.readText().removePrefix("\uFEFF")
    val json = JsonParser.parseString(text)
    val config = if (json.isJsonObject && json.asJsonObject.has("Settings")) {
        WebConfigAdapter.toObfConfig(json.asJsonObject)
    } else {
        ObfConfig.read(path)
    }
    return resolveRelativePaths(config, path.parent ?: Path("."))
}

private fun resolveRelativePaths(config: ObfConfig, baseDir: JPath): ObfConfig {
    fun resolvePathString(value: String): String {
        val path = JPath.of(value)
        return if (path.isAbsolute) path.toString() else baseDir.resolve(path).toAbsolutePath().toString()
    }

    fun resolveOptionalPathString(value: String?): String? {
        return value?.let(::resolvePathString)
    }

    return config.copy(
        input = resolvePathString(config.input),
        output = resolveOptionalPathString(config.output),
        libs = config.libs.map(::resolvePathString),
        customDictionary = resolveCustomDictionary(config.customDictionary, baseDir)
    )
}

private fun resolveCustomDictionary(customDictionary: String, baseDir: JPath): String {
    val path = JPath.of(customDictionary)
    if (path.isAbsolute) return path.toString()
    val candidate = baseDir.resolve(path)
    return if (candidate.exists()) candidate.toAbsolutePath().toString() else customDictionary
}

private fun findAvailablePort(preferredPort: Int, attempts: Int = 20): Int {
    repeat(attempts) { offset ->
        val candidate = preferredPort + offset
        if (candidate in 1..65535 && isPortAvailable(candidate)) {
            return candidate
        }
    }
    return preferredPort
}

private fun isPortAvailable(port: Int): Boolean {
    return runCatching {
        ServerSocket(port).use {
            it.reuseAddress = true
        }
        true
    }.getOrDefault(false)
}
