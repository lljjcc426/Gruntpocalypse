package net.spartanb312.grunteon.obfuscator.web

data class PersistedArtifactRecord(
    val objectKey: String,
    val artifactKind: String,
    val fileName: String,
    val storageBackend: String,
    val bucketName: String?,
    val objectPath: String,
    val sizeBytes: Long,
    val artifactStatus: String,
    val createdAt: String?,
    val updatedAt: String?,
    val bindings: List<PersistedArtifactBinding> = emptyList()
)
