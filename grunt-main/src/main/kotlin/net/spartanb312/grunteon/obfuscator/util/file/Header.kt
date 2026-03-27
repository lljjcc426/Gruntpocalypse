package net.spartanb312.grunteon.obfuscator.util.file

import org.apache.commons.rng.UniformRandomProvider
import java.io.OutputStream

fun corruptJarHeader(randomGen: UniformRandomProvider, outputStream: OutputStream) {
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
