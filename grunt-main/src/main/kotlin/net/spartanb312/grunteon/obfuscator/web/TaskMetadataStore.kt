package net.spartanb312.grunteon.obfuscator.web

interface TaskMetadataStore {
    fun saveTask(task: PlatformTaskRecord)
}

object NoOpTaskMetadataStore : TaskMetadataStore {
    override fun saveTask(task: PlatformTaskRecord) {
    }
}
