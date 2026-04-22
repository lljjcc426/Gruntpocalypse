package net.spartanb312.grunteon.obfuscator.web

data class PersistedSessionState(
    val sessionId: String,
    val ownerUsername: String?,
    val policyMode: String,
    val controlPlane: String,
    val workerPlane: String,
    val status: String,
    val currentStep: String,
    val progress: Int,
    val totalSteps: Int,
    val errorMessage: String?,
    val configFileName: String?,
    val inputFileName: String?,
    val outputFileName: String?,
    val configObjectKey: String?,
    val inputObjectKey: String?,
    val outputObjectKey: String?,
    val libraryFiles: List<String>,
    val assetFiles: List<String>,
    val libraryObjectRefs: Map<String, String>,
    val assetObjectRefs: Map<String, String>
)
