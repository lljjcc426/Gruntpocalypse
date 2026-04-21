package net.spartanb312.grunteon.obfuscator.web

interface TaskMetadataStore {
    fun saveTask(task: PlatformTaskRecord)

    fun recoverInterruptedTask(taskId: String, previousStatus: String, reason: String, recoveredAt: String): Boolean
}

object NoOpTaskMetadataStore : TaskMetadataStore {
    override fun saveTask(task: PlatformTaskRecord) {
    }

    override fun recoverInterruptedTask(taskId: String, previousStatus: String, reason: String, recoveredAt: String): Boolean = false
}
