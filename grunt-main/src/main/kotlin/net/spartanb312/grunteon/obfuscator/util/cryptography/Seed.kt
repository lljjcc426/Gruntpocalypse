package net.spartanb312.grunteon.obfuscator.util.cryptography

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.Transformer

context(instance: Grunteon)
fun Transformer<*>.getSeed(vararg append: String): Long {
    var seedStr = transformerSeed
    append.forEach { seedStr += it }
    return sha256ToLong(seedStr)
}