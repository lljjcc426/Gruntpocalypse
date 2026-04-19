package net.spartanb312.grunteon.obfuscator

import net.spartanb312.everett.bootstrap.Main
import net.spartanb312.grunteon.obfuscator.web.WebServer

// for dev mode only
fun main() = Main.main(arrayOf())

object ClientEntry {

    init {
        println("Client entry")
    }

}

object ServerEntry {

    init {
        val port = Main.args.firstOrNull { it.startsWith("--port=") }
            ?.substringAfter("=")
            ?.toIntOrNull()
            ?: 8080
        WebServer.start(port)
    }

}
