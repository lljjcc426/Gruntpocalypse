package net.spartanb312.grunteon.obfuscator.web

import net.spartanb312.grunteon.obfuscator.ObfConfig
import java.io.File

object WebBridgeSupport {

    @JvmStatic
    fun buildExecutionConfig(session: ObfuscationSession): ObfConfig {
        val doc = session.loadConfigJson()
        val base = WebConfigAdapter.toObfConfig(doc)
        val outputName = sanitizeOutputFileName(base.output)
        return base.copy(
            input = session.inputJarPath ?: base.input,
            output = File(session.outputDir, outputName).absolutePath,
            libs = session.getLibraryPaths(),
            customDictionary = session.resolveAssetPath(base.customDictionary) ?: base.customDictionary
        )
    }

    @JvmStatic
    fun buildStatusMap(session: ObfuscationSession): Map<String, Any?> {
        return linkedMapOf(
            "status" to session.status.name,
            "sessionId" to session.id,
            "ownerUsername" to session.ownerUsername,
            "currentStep" to session.currentStep,
            "progress" to session.progress,
            "totalSteps" to session.totalSteps,
            "configUploaded" to session.hasUploadedConfig(),
            "inputUploaded" to session.hasUploadedInput(),
            "outputAvailable" to session.hasOutput(),
            "configFileName" to session.configDisplayName,
            "configObjectKey" to session.configObjectKey,
            "inputFileName" to session.inputDisplayName,
            "inputObjectKey" to session.inputObjectKey,
            "outputObjectKey" to session.outputObjectKey,
            "libraryCount" to session.getLibraryNames().size,
            "libraryFiles" to session.getLibraryNames(),
            "libraryObjectRefs" to session.getLibraryObjectRefs(),
            "assetCount" to session.getAssetNames().size,
            "assetFiles" to session.getAssetNames(),
            "assetObjectRefs" to session.getAssetObjectRefs(),
            "policy" to linkedMapOf(
                "mode" to session.accessProfile.name,
                "allowProjectPreview" to session.accessProfile.allowProjectPreview,
                "allowSourcePreview" to session.accessProfile.allowSourcePreview,
                "allowDetailedLogs" to session.accessProfile.allowDetailedLogs
            ),
            "planes" to linkedMapOf(
                "control" to session.controlPlane,
                "worker" to session.workerPlane
            ),
            "error" to session.errorMessage
        ).filterValues { it != null }
    }

    @JvmStatic
    fun buildStatusMap(state: PersistedSessionState): Map<String, Any?> {
        val profile = SessionAccessProfile.parseOrNull(state.policyMode) ?: SessionAccessProfile.SECURE
        return linkedMapOf(
            "status" to state.status,
            "sessionId" to state.sessionId,
            "ownerUsername" to state.ownerUsername,
            "currentStep" to state.currentStep,
            "progress" to state.progress,
            "totalSteps" to state.totalSteps,
            "configUploaded" to (!state.configObjectKey.isNullOrBlank() || !state.configFileName.isNullOrBlank()),
            "inputUploaded" to (!state.inputObjectKey.isNullOrBlank() || !state.inputFileName.isNullOrBlank()),
            "outputAvailable" to (!state.outputObjectKey.isNullOrBlank() || !state.outputFileName.isNullOrBlank()),
            "configFileName" to state.configFileName,
            "configObjectKey" to state.configObjectKey,
            "inputFileName" to state.inputFileName,
            "inputObjectKey" to state.inputObjectKey,
            "outputObjectKey" to state.outputObjectKey,
            "libraryCount" to state.libraryFiles.size.coerceAtLeast(state.libraryObjectRefs.size),
            "libraryFiles" to state.libraryFiles.sorted(),
            "libraryObjectRefs" to state.libraryObjectRefs.toSortedMap(),
            "assetCount" to state.assetFiles.size.coerceAtLeast(state.assetObjectRefs.size),
            "assetFiles" to state.assetFiles.sorted(),
            "assetObjectRefs" to state.assetObjectRefs.toSortedMap(),
            "policy" to linkedMapOf(
                "mode" to profile.name,
                "allowProjectPreview" to profile.allowProjectPreview,
                "allowSourcePreview" to profile.allowSourcePreview,
                "allowDetailedLogs" to profile.allowDetailedLogs
            ),
            "planes" to linkedMapOf(
                "control" to state.controlPlane,
                "worker" to state.workerPlane
            ),
            "error" to state.errorMessage
        ).filterValues { it != null }
    }

    @JvmStatic
    fun buildTaskMap(task: PlatformTaskRecord, sessionService: SessionService): Map<String, Any?> {
        val base = linkedMapOf<String, Any?>(
            "id" to task.id,
            "ownerUsername" to task.ownerUsername,
            "projectName" to task.projectName,
            "inputObjectKey" to task.inputObjectKey,
            "configObjectKey" to task.configObjectKey,
            "outputObjectKey" to task.outputObjectKey,
            "sessionId" to task.sessionId,
            "policyMode" to task.accessProfile.name,
            "createdAt" to task.createdAt,
            "updatedAt" to task.updatedAt,
            "status" to task.status.name,
            "stage" to task.currentStage,
            "progress" to task.progress,
            "message" to task.message,
            "recovery" to task.recoveryReason?.let {
                linkedMapOf(
                    "previousStatus" to task.recoveryPreviousStatus,
                    "recoveredStatus" to task.status.name,
                    "reason" to task.recoveryReason,
                    "recoveredAt" to task.recoveredAt
                ).filterValues { value -> value != null }
            }
        ).filterValues { it != null }.toMutableMap()

        val session = task.sessionId?.let(sessionService::getSession)
        if (session != null) {
            base["session"] = buildStatusMap(session)
            base["inputClassCount"] = session.inputClassList?.size ?: 0
            base["outputClassCount"] = session.finalClassList?.size ?: 0
        }
        return base
    }

    @JvmStatic
    fun buildTaskMap(task: PersistedTaskState, session: PersistedSessionState?): Map<String, Any?> {
        val base = linkedMapOf<String, Any?>(
            "id" to task.taskId,
            "ownerUsername" to task.ownerUsername,
            "projectName" to task.projectName,
            "inputObjectKey" to task.inputObjectKey,
            "configObjectKey" to task.configObjectKey,
            "outputObjectKey" to task.outputObjectKey,
            "sessionId" to task.sessionId,
            "policyMode" to task.policyMode,
            "createdAt" to task.createdAt,
            "updatedAt" to task.updatedAt,
            "status" to task.status,
            "stage" to task.currentStage,
            "progress" to task.progress,
            "message" to task.message,
            "recovery" to task.recoveryReason?.let {
                linkedMapOf(
                    "previousStatus" to task.recoveryPreviousStatus,
                    "recoveredStatus" to task.status,
                    "reason" to task.recoveryReason,
                    "recoveredAt" to task.recoveredAt
                ).filterValues { value -> value != null }
            }
        ).filterValues { it != null }.toMutableMap()

        if (session != null) {
            base["session"] = buildStatusMap(session)
        }
        return base
    }

    @JvmStatic
    fun buildTaskStageMap(stage: TaskStageRecord): Map<String, Any?> {
        return linkedMapOf(
            "name" to stage.name,
            "progress" to stage.progress,
            "message" to stage.message,
            "timestamp" to stage.timestamp
        )
    }

    @JvmStatic
    fun buildArtifactMap(artifact: PersistedArtifactRecord): Map<String, Any?> {
        return linkedMapOf(
            "objectKey" to artifact.objectKey,
            "artifactKind" to artifact.artifactKind,
            "fileName" to artifact.fileName,
            "storageBackend" to artifact.storageBackend,
            "bucketName" to artifact.bucketName,
            "objectPath" to artifact.objectPath,
            "sizeBytes" to artifact.sizeBytes,
            "artifactStatus" to artifact.artifactStatus,
            "createdAt" to artifact.createdAt,
            "updatedAt" to artifact.updatedAt,
            "bindings" to artifact.bindings.map(::buildArtifactBindingMap)
        ).filterValues { it != null }
    }

    @JvmStatic
    fun buildArtifactBindingMap(binding: PersistedArtifactBinding): Map<String, Any?> {
        return linkedMapOf(
            "objectKey" to binding.objectKey,
            "ownerType" to binding.ownerType,
            "ownerId" to binding.ownerId,
            "ownerRole" to binding.ownerRole,
            "createdAt" to binding.createdAt,
            "updatedAt" to binding.updatedAt
        ).filterValues { it != null }
    }

    @JvmStatic
    fun parseProjectScope(rawScope: String?): ObfuscationSession.ProjectScope? {
        return when (rawScope?.lowercase()) {
            "input" -> ObfuscationSession.ProjectScope.INPUT
            "output" -> ObfuscationSession.ProjectScope.OUTPUT
            else -> null
        }
    }

    @JvmStatic
    fun isValidClassName(className: String): Boolean {
        if (className.isBlank()) return false
        if (className.contains("..")) return false
        if (className.startsWith("/") || className.startsWith("\\")) return false
        if (className.contains(":")) return false
        return Regex("^[A-Za-z0-9_/$.\\-]+$").matches(className)
    }

    @JvmStatic
    fun sanitizeOutputFileName(configuredValue: String?): String {
        val raw = File(configuredValue?.trim().takeUnless { it.isNullOrBlank() } ?: "output.jar").name
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return if (cleaned.isBlank()) "output.jar" else cleaned
    }
}
