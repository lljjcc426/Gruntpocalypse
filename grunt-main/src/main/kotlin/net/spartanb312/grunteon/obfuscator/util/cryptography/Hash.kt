package net.spartanb312.grunteon.obfuscator.util.cryptography

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

// simple
fun longHash(s: String): Long {
    var hash = 0L
    for (c in s) {
        hash = 31 * hash + c.code.toLong()
    }
    return hash
}

// FNV-1a
private const val FNV1A_64_INIT = -0x340d631b7bddd9bbL
private const val FNV1A_64_PRIME = 0x100000001b3L

fun fnv1a64(s: String): Long {
    var hash = FNV1A_64_INIT
    val bytes = s.toByteArray()
    for (b in bytes) {
        hash = hash xor (b.toLong() and 0xFFL)
        hash *= FNV1A_64_PRIME
    }
    return hash
}

// MurmurHash64
private const val MURMUR_SEED = 0xe17a1465L

private fun fmix64(k: Long): Long {
    var k = k
    k = k xor (k ushr 33)
    k *= -0xae502812aa7333L
    k = k xor (k ushr 33)
    k *= -0x3b314601e57a13adL
    k = k xor (k ushr 33)
    return k
}

fun murmur64(s: String): Long {
    val data = s.toByteArray()
    val length = data.size
    var h64 = MURMUR_SEED

    var i = 0
    while (i <= length - 8) {
        var k = ((data[i].toLong() and 0xFFL)
                or ((data[i + 1].toLong() and 0xFFL) shl 8)
                or ((data[i + 2].toLong() and 0xFFL) shl 16)
                or ((data[i + 3].toLong() and 0xFFL) shl 24)
                or ((data[i + 4].toLong() and 0xFFL) shl 32)
                or ((data[i + 5].toLong() and 0xFFL) shl 40)
                or ((data[i + 6].toLong() and 0xFFL) shl 48)
                or ((data[i + 7].toLong() and 0xFFL) shl 56))

        k *= -0x783c846eeebdac2bL
        k = java.lang.Long.rotateLeft(k, 31)
        k *= 0x4cf5ad432745937fL
        h64 = h64 xor k
        h64 = java.lang.Long.rotateLeft(h64, 27) * 5 + 0x52dce729
        i += 8
    }

    var k1: Long = 0
    for (j in length - 1 downTo i) {
        k1 = k1 xor (data[j].toLong() and (0xFF shl ((j - i) * 8)).toLong())
    }

    if (k1 != 0L) {
        k1 *= -0x783c846eeebdac2bL
        k1 = java.lang.Long.rotateLeft(k1, 31)
        k1 *= 0x4cf5ad432745937fL
        h64 = h64 xor k1
    }

    h64 = h64 xor length.toLong()
    h64 = fmix64(h64)
    return h64
}

// sha256
fun sha256ToLong(input: String): Long {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
    return (0 until 8).fold(0L) { acc, i -> acc shl 8 or (bytes[i].toLong() and 0xFF) }
}