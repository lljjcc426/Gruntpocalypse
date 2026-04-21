package net.spartanb312.grunteon.obfuscator.web

import java.io.File

interface ObjectStorageBackend {
    fun exists(objectKey: String): Boolean
    fun putObject(objectKey: String, bytes: ByteArray)
    fun getObject(objectKey: String): File
    fun describe(objectKey: String): ObjectStorageLocation
}
