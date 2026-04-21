package net.spartanb312.grunteon.obfuscator.web

interface TaskMetadataQuery {
    fun loadTasks(): List<PersistedTaskState>

    fun findTask(taskId: String): PersistedTaskState?
}

object NoOpTaskMetadataQuery : TaskMetadataQuery {
    override fun loadTasks(): List<PersistedTaskState> = emptyList()

    override fun findTask(taskId: String): PersistedTaskState? = null
}
