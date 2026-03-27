package net.spartanb312.grunteon.obfuscator.util.file

import org.apache.commons.rng.UniformRandomProvider
import java.util.zip.CRC32
import java.util.zip.ZipOutputStream

fun ZipOutputStream.corruptCRC32(randomGen: UniformRandomProvider) {
    val field = ZipOutputStream::class.java.getDeclaredField("crc")
    field.isAccessible = true
    field[this] = object : CRC32() {
        override fun update(bytes: ByteArray, i: Int, length: Int) {}
        override fun getValue(): Long {
            return randomGen.nextInt(Int.MAX_VALUE - 1).toLong()
        }
    }
}