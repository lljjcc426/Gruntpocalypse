package net.spartanb312.grunteon.obfuscator.web

data class PersistedArtifactBinding(
    val objectKey: String,
    val ownerType: String,
    val ownerId: String,
    val ownerRole: String,
    val createdAt: String?,
    val updatedAt: String?
)
