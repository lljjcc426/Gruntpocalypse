package net.spartanb312.grunteon.obfuscator.web

import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

class ObjectStorageService @JvmOverloads constructor(
    private val backend: ObjectStorageBackend = FilesystemObjectStorageBackend(),
    private val urlPrefix: String = "/api/v1/storage",
    private val artifactMetadataStore: ArtifactMetadataStore = NoOpArtifactMetadataStore
) {

    constructor(baseDir: File) : this(FilesystemObjectStorageBackend(baseDir.absoluteFile))

    @JvmOverloads
    fun createUploadTicket(fileName: String?, kind: String? = null): ObjectTicket {
        val safeName = sanitizeFileName(fileName ?: "artifact.bin")
        val key = buildUploadObjectKey(resolveUploadKind(kind, fileName), safeName)
        return ObjectTicket(
            objectKey = key,
            method = "PUT",
            url = "$urlPrefix/$key",
            expiresAt = Instant.now().plusSeconds(3600).toString()
        )
    }

    fun createDownloadTicket(objectKey: String): ObjectTicket {
        require(backend.exists(objectKey)) { "Object not found" }
        return ObjectTicket(
            objectKey = objectKey,
            method = "GET",
            url = "$urlPrefix/$objectKey",
            expiresAt = Instant.now().plusSeconds(3600).toString()
        )
    }

    fun putObject(objectKey: String, bytes: ByteArray) {
        val normalizedKey = normalizeObjectKey(objectKey)
        backend.putObject(normalizedKey, bytes)
        val location = backend.describe(normalizedKey)
        artifactMetadataStore.recordArtifact(
            objectKey = normalizedKey,
            artifactKind = inferArtifactKind(normalizedKey),
            fileName = File(normalizedKey).name,
            storageBackend = location.storageBackend,
            bucketName = location.bucketName,
            objectPath = location.objectPath,
            sizeBytes = bytes.size.toLong()
        )
    }

    fun getObject(objectKey: String): File {
        return backend.getObject(normalizeObjectKey(objectKey))
    }

    fun createOutputObjectKey(taskId: String, fileName: String): String {
        val safeName = sanitizeFileName(fileName.ifBlank { "artifact-obf.jar" })
        val safeTaskId = taskId.replace(Regex("[^A-Za-z0-9_-]"), "-")
        return "artifacts/output/$safeTaskId/$safeName"
    }

    fun createManagedObjectKey(fileName: String?, kind: String?): String {
        val safeName = sanitizeFileName(fileName ?: "artifact.bin")
        return buildUploadObjectKey(resolveUploadKind(kind, fileName), safeName)
    }

    private fun buildUploadObjectKey(kind: String, safeName: String): String {
        val datePath = DATE_PATH_FORMATTER.format(Instant.now().atOffset(ZoneOffset.UTC))
        return "artifacts/$kind/$datePath/${UUID.randomUUID()}/$safeName"
    }

    private fun resolveUploadKind(kind: String?, fileName: String?): String {
        val normalizedKind = kind?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (normalizedKind != null) {
            return when (normalizedKind) {
                "input", "inputs", "jar" -> "input"
                "config", "configs", "configuration" -> "config"
                "asset", "assets", "library", "libraries" -> "asset"
                else -> "asset"
            }
        }
        val name = fileName?.lowercase() ?: return "asset"
        return when {
            name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml") ||
                name.endsWith(".toml") || name.endsWith(".conf") -> "config"
            name.endsWith(".jar") || name.endsWith(".zip") -> "input"
            else -> "asset"
        }
    }

    private fun sanitizeFileName(fileName: String): String {
        val normalized = File(fileName).name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return normalized.ifBlank { "artifact.bin" }
    }

    private fun normalizeObjectKey(objectKey: String): String {
        require(objectKey.isNotBlank()) { "Invalid object key" }
        require(!objectKey.contains("..")) { "Invalid object key" }
        require(!objectKey.startsWith("/") && !objectKey.startsWith("\\")) { "Invalid object key" }
        return objectKey.replace('\\', '/')
    }

    private fun inferArtifactKind(objectKey: String): String {
        val parts = normalizeObjectKey(objectKey).split('/')
        return parts.getOrNull(1) ?: "asset"
    }

    companion object {
        private val DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd")
    }
}

data class ObjectTicket(
    val objectKey: String,
    val method: String,
    val url: String,
    val expiresAt: String
)
