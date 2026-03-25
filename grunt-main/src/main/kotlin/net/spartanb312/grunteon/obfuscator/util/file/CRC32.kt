package net.spartanb312.grunteon.obfuscator.util.file

import java.util.zip.CRC32
import java.util.zip.ZipOutputStream
import kotlin.random.Random

fun ZipOutputStream.corruptCRC32() {
    val field = ZipOutputStream::class.java.getDeclaredField("crc")
    field.isAccessible = true
    field[this] = object : CRC32() {
        override fun update(bytes: ByteArray, i: Int, length: Int) {}
        override fun getValue(): Long {
            return Random.nextInt(0, Int.MAX_VALUE).toLong()
        }
    }
}