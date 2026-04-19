package net.spartanb312.grunteon.obfuscator.web

import java.io.File
import java.time.Instant
import java.util.UUID

class ObjectStorageService(
    private val baseDir: File = File(".state/object-store").absoluteFile
) {

    init {
        baseDir.mkdirs()
    }

    fun createUploadTicket(fileName: String?): ObjectTicket {
        val safeName = sanitizeFileName(fileName ?: "artifact.bin")
        val key = "input/${UUID.randomUUID()}-$safeName"
        return ObjectTicket(
            objectKey = key,
            method = "PUT",
            url = "/api/v1/storage/$key",
            expiresAt = Instant.now().plusSeconds(3600).toString()
        )
    }

    fun createDownloadTicket(objectKey: String): ObjectTicket {
        val file = resolve(objectKey)
        require(file.exists()) { "Object not found" }
        return ObjectTicket(
            objectKey = objectKey,
            method = "GET",
            url = "/api/v1/storage/$objectKey",
            expiresAt = Instant.now().plusSeconds(3600).toString()
        )
    }

    fun putObject(objectKey: String, bytes: ByteArray) {
        val file = resolve(objectKey)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    fun getObject(objectKey: String): File {
        val file = resolve(objectKey)
        require(file.exists()) { "Object not found" }
        return file
    }

    private fun resolve(key: String): File {
        require(key.isNotBlank()) { "Invalid object key" }
        require(!key.contains("..")) { "Invalid object key" }
        require(!key.startsWith("/") && !key.startsWith("\\")) { "Invalid object key" }
        val file = File(baseDir, key).absoluteFile.normalize()
        require(file.path.startsWith(baseDir.path)) { "Invalid object key" }
        return file
    }

    private fun sanitizeFileName(fileName: String): String {
        val normalized = File(fileName).name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return normalized.ifBlank { "artifact.bin" }
    }
}

data class ObjectTicket(
    val objectKey: String,
    val method: String,
    val url: String,
    val expiresAt: String
)
