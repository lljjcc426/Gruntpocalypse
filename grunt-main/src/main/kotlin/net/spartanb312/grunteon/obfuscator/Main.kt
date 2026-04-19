package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.logging.SimpleLogger
import net.spartanb312.grunteon.obfuscator.web.WebServer
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.Path
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
    val config = ObfConfig.read(Path(configPath))
    val instance = Grunteon.create(config)

    measureTime {
        instance.execute()
    }.toDouble(DurationUnit.MILLISECONDS).also { time ->
        if (config.printTimeUsage) {
            println("Execution time: ${"%.2f".format(time)} ms")
        }
    }
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
