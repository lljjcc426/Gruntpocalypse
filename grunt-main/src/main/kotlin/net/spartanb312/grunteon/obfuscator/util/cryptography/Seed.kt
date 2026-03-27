package net.spartanb312.grunteon.obfuscator.util.cryptography

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.Transformer
import org.apache.commons.math3.random.Well19937c
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

context(instance: Grunteon)
fun Transformer<*>.getSeed(vararg append: String): ByteArray {
    return MessageDigest.getInstance("SHA-256")
        .digest(append.fold(transformerSeed, String::plus).toByteArray(StandardCharsets.UTF_8))
}

context(instance: Grunteon)
fun getSeed(vararg append: String): ByteArray {
    return MessageDigest.getInstance("SHA-256")
        .digest(append.fold(instance.baseSeed, String::plus).toByteArray(StandardCharsets.UTF_8))
}

private fun ByteArray.toInts(): IntArray {
    val intCount = (this.size + 3) / 4
    val ints = IntArray(intCount)
    for (i in this.indices) {
        val intIndex = i / 4
        val shift = (i % 4) * 8
        ints[intIndex] = ints[intIndex] or ((this[i].toInt() and 0xFF) shl shift)
    }
    return ints
}

fun ByteArray.toRandom() = Well19937c().apply { setSeed(toInts()) }