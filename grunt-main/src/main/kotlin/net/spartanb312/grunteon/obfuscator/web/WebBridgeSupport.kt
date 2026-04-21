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
    fun buildTaskMap(task: PlatformTaskRecord, sessionService: SessionService): Map<String, Any?> {
        val base = linkedMapOf<String, Any?>(
            "id" to task.id,
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
            "message" to task.message
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
    fun buildTaskStageMap(stage: TaskStageRecord): Map<String, Any?> {
        return linkedMapOf(
            "name" to stage.name,
            "progress" to stage.progress,
            "message" to stage.message,
            "timestamp" to stage.timestamp
        )
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
