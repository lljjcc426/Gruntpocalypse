package net.spartanb312.grunteon.obfuscator.util.numerical

fun Double.asLong(): Long = java.lang.Double.doubleToRawLongBits(this)

fun Float.asInt(): Int = java.lang.Float.floatToRawIntBits(this)