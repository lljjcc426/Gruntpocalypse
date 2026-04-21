package net.spartanb312.grunteon.obfuscator.web

import java.io.File

class FilesystemObjectStorageBackend(
    private val baseDir: File = File(".state/object-store").absoluteFile
) : ObjectStorageBackend {

    init {
        baseDir.mkdirs()
    }

    override fun exists(objectKey: String): Boolean {
        return resolve(objectKey).exists()
    }

    override fun putObject(objectKey: String, bytes: ByteArray) {
        val file = resolve(objectKey)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    override fun getObject(objectKey: String): File {
        val file = resolve(objectKey)
        require(file.exists()) { "Object not found" }
        return file
    }

    override fun describe(objectKey: String): ObjectStorageLocation {
        val file = resolve(objectKey)
        return ObjectStorageLocation(
            storageBackend = "filesystem",
            bucketName = null,
            objectPath = file.absolutePath
        )
    }

    private fun resolve(key: String): File {
        require(key.isNotBlank()) { "Invalid object key" }
        require(!key.contains("..")) { "Invalid object key" }
        require(!key.startsWith("/") && !key.startsWith("\\")) { "Invalid object key" }
        val file = File(baseDir, key).absoluteFile.normalize()
        require(file.path.startsWith(baseDir.path)) { "Invalid object key" }
        return file
    }
}
