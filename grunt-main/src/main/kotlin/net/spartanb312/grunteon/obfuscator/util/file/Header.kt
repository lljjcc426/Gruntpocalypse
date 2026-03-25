package net.spartanb312.grunteon.obfuscator.util.file

import java.io.FileOutputStream
import kotlin.random.Random

fun corruptJarHeader(outputStream: FileOutputStream) {
    // Write default jar header to stream.
    outputStream.write(0x50)
    outputStream.write(0x4B)
    outputStream.write(0x03)
    outputStream.write(0x04)
    // Write random bytes to stream.
    val bytes = ByteArray(Random.nextInt(1, 25))
    Random.nextBytes(bytes)
    outputStream.write(bytes)
}
