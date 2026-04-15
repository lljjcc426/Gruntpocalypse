package net.spartanb312.grunteon.obfuscator

import kotlin.io.path.Path
import kotlin.io.path.readText

/**
 * Grunteon
 * A JVM bytecode obfuscator
 * 3rd generation of Grunt
 */
const val VERSION = "3.0.0"
const val SUBTITLE = "build 260415"
const val GITHUB = "https://github.com/SpartanB312/Grunt"

fun main(args: Array<String>) {
    val config = ObfConfig.read(Path("config.json").readText())
    val instance = Grunteon.create(config)
    instance.execute()
}