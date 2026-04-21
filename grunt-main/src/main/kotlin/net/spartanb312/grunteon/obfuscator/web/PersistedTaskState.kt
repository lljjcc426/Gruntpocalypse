package net.spartanb312.grunteon.obfuscator.web

data class PersistedTaskState(
    val taskId: String,
    val projectName: String,
    val inputObjectKey: String,
    val configObjectKey: String?,
    val outputObjectKey: String?,
    val sessionId: String?,
    val policyMode: String,
    val status: String,
    val currentStage: String,
    val progress: Int,
    val message: String,
    val logs: List<String>,
    val stages: List<TaskStageRecord>,
    val recoveryPreviousStatus: String?,
    val recoveryReason: String?,
    val recoveredAt: String?,
    val createdAt: String,
    val updatedAt: String
)
