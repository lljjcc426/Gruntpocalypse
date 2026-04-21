package net.spartanb312.grunteon.obfuscator.web

data class ObjectStorageLocation(
    val storageBackend: String,
    val bucketName: String?,
    val objectPath: String
)
