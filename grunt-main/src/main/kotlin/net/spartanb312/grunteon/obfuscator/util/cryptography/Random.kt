package net.spartanb312.grunteon.obfuscator.util.cryptography

import org.apache.commons.math3.random.RandomGenerator

fun RandomGenerator.nextInt(start: Int, end: Int): Int {
    require(start < end) { "start must < end" }
    val range = end - start
    return start + nextInt(range)
}