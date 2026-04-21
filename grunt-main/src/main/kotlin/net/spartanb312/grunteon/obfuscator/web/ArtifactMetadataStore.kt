package net.spartanb312.grunteon.obfuscator.web

interface ArtifactMetadataStore {
    fun recordArtifact(
        objectKey: String,
        artifactKind: String,
        fileName: String,
        storageBackend: String,
        bucketName: String?,
        objectPath: String,
        sizeBytes: Long,
        status: String = "AVAILABLE"
    )

    fun bindArtifact(
        objectKey: String,
        ownerType: String,
        ownerId: String,
        ownerRole: String
    )
}

object NoOpArtifactMetadataStore : ArtifactMetadataStore {
    override fun recordArtifact(
        objectKey: String,
        artifactKind: String,
        fileName: String,
        storageBackend: String,
        bucketName: String?,
        objectPath: String,
        sizeBytes: Long,
        status: String
    ) {
    }

    override fun bindArtifact(objectKey: String, ownerType: String, ownerId: String, ownerRole: String) {
    }
}
