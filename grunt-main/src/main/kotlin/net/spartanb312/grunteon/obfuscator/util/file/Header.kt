package net.spartanb312.grunteon.obfuscator.util.file

import net.spartanb312.grunteon.obfuscator.util.cryptography.nextInt
import org.apache.commons.math3.random.RandomGenerator
import java.io.FileOutputStream

fun corruptJarHeader(randomGen: RandomGenerator, outputStream: FileOutputStream) {
    // Write default jar header to stream.
    outputStream.write(0x50)
    outputStream.write(0x4B)
    outputStream.write(0x03)
    outputStream.write(0x04)
    // Write random bytes to stream.
    val bytes = ByteArray(randomGen.nextInt(1, 25))
    randomGen.nextBytes(bytes)
    outputStream.write(bytes)
}
