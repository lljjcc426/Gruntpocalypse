package net.spartanb312.grunteon.obfuscator.web

interface ArtifactMetadataQuery {
    fun findArtifact(objectKey: String): PersistedArtifactRecord?

    fun loadArtifactsForOwner(ownerType: String, ownerId: String): List<PersistedArtifactRecord>

    fun loadBindingsForObject(objectKey: String): List<PersistedArtifactBinding>
}

object NoOpArtifactMetadataQuery : ArtifactMetadataQuery {
    override fun findArtifact(objectKey: String): PersistedArtifactRecord? = null

    override fun loadArtifactsForOwner(ownerType: String, ownerId: String): List<PersistedArtifactRecord> = emptyList()

    override fun loadBindingsForObject(objectKey: String): List<PersistedArtifactBinding> = emptyList()
}
