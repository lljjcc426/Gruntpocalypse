package net.spartanb312.grunteon.obfuscator.web

import com.google.gson.JsonObject
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class PlatformTaskService(
    private val sessionService: SessionService,
    private val objectStorageService: ObjectStorageService,
    private val obfuscationService: ObfuscationService
) {
    private val tasks = ConcurrentHashMap<String, PlatformTaskRecord>()

    fun createTask(projectName: String, inputObjectKey: String, configObjectKey: String?): PlatformTaskRecord {
        val id = UUID.randomUUID().toString()
        val record = PlatformTaskRecord(
            id = id,
            projectName = projectName,
            inputObjectKey = inputObjectKey,
            configObjectKey = configObjectKey
        )
        tasks[id] = record
        record.message = "Task created"
        touch(record)
        return record
    }

    fun getTask(taskId: String): PlatformTaskRecord {
        val task = requireNotNull(tasks[taskId]) { "Task not found" }
        refreshFromSession(task)
        return task
    }

    fun listTasks(): List<PlatformTaskRecord> {
        tasks.values.forEach(::refreshFromSession)
        return tasks.values.sortedByDescending { it.createdAt }
    }

    fun startTask(taskId: String): PlatformTaskRecord {
        val task = getTask(taskId)
        require(task.status == TaskStatus.CREATED || task.status == TaskStatus.FAILED) { "Task is not in a startable state" }

        val inputFile = objectStorageService.getObject(task.inputObjectKey)
        val session = sessionService.createSession()
        sessionService.saveInput(session, inputFile)

        val configJson = task.configObjectKey
            ?.let { key -> objectStorageService.getObject(key).readText(Charsets.UTF_8) }
            ?.let { text -> com.google.gson.JsonParser.parseString(text).asJsonObject }
            ?: JsonObject()
        sessionService.saveConfig(session, configJson, "config.json")

        task.sessionId = session.id
        task.status = TaskStatus.RUNNING
        task.message = "Executing obfuscation"
        task.progress = 0
        task.currentStage = "Preparing"
        task.logs.clear()
        task.stages.clear()
        addStage(task, "Preparing", 0, "Task started")
        touch(task)

        session.onLogMessage = { line ->
            task.logs += line
            task.message = line
            touch(task)
        }
        session.onProgressUpdate = { payload ->
            val stage = extractJsonField(payload, "step") ?: task.currentStage
            val progress = extractJsonField(payload, "progress")?.toIntOrNull() ?: task.progress
            task.currentStage = stage
            task.progress = progress
            addStage(task, stage, progress, task.message.ifBlank { "running" })
            touch(task)
        }

        val config = WebConfigAdapter.toObfConfig(configJson).copy(
            input = session.inputJarPath ?: "",
            output = File(session.outputDir, "artifact-obf.jar").absolutePath,
            libs = session.getLibraryPaths()
        )

        when (obfuscationService.start(session, config, onFinish = {
            if (session.status == ObfuscationSession.Status.COMPLETED && session.outputJarPath != null) {
                val outputKey = "output/${task.id}/artifact-obf.jar"
                val outputFile = File(session.outputJarPath!!)
                if (outputFile.exists()) {
                    objectStorageService.putObject(outputKey, outputFile.readBytes())
                    task.outputObjectKey = outputKey
                    task.status = TaskStatus.COMPLETED
                    task.progress = 100
                    task.currentStage = "Completed"
                    task.message = "Task completed"
                    addStage(task, "Completed", 100, "Task completed")
                    touch(task)
                    return@start
                }
            }
            task.status = TaskStatus.FAILED
            task.message = session.errorMessage ?: "Task failed"
            addStage(task, "Failed", task.progress, task.message)
            touch(task)
        })) {
            StartResult.Started -> {}
            StartResult.Busy -> {
                task.status = TaskStatus.FAILED
                task.message = "Another obfuscation task is already running"
                addStage(task, "Failed", 0, task.message)
                touch(task)
            }
            StartResult.MissingConfig -> {
                task.status = TaskStatus.FAILED
                task.message = "No config uploaded"
                addStage(task, "Failed", 0, task.message)
                touch(task)
            }
            StartResult.MissingInput -> {
                task.status = TaskStatus.FAILED
                task.message = "No input JAR uploaded"
                addStage(task, "Failed", 0, task.message)
                touch(task)
            }
        }

        return task
    }

    fun getTaskLogs(taskId: String): List<String> {
        return getTask(taskId).logs.toList()
    }

    private fun extractJsonField(payload: String, key: String): String? {
        val marker = "\"$key\":\""
        val stringIndex = payload.indexOf(marker)
        if (stringIndex >= 0) {
            val start = stringIndex + marker.length
            val end = payload.indexOf('"', start)
            if (end > start) return payload.substring(start, end)
        }
        val numericMarker = "\"$key\":"
        val numericIndex = payload.indexOf(numericMarker)
        if (numericIndex >= 0) {
            val start = numericIndex + numericMarker.length
            val tail = payload.substring(start).trimStart()
            return tail.takeWhile { it.isDigit() }
        }
        return null
    }

    private fun touch(task: PlatformTaskRecord) {
        task.updatedAt = Instant.now().toString()
    }

    private fun refreshFromSession(task: PlatformTaskRecord) {
        val sessionId = task.sessionId ?: return
        val session = sessionService.getSession(sessionId) ?: return

        if (task.logs.size < session.consoleLogs.size) {
            session.consoleLogs.subList(task.logs.size, session.consoleLogs.size).forEach { line ->
                task.logs += line
                task.message = line
            }
            touch(task)
        }

        if (session.currentStep.isNotBlank()) {
            task.currentStage = session.currentStep
            task.progress = session.progress
            addStage(
                task,
                session.currentStep,
                session.progress,
                task.message.ifBlank { session.currentStep }
            )
        }

        when (session.status) {
            ObfuscationSession.Status.RUNNING -> {
                task.status = TaskStatus.RUNNING
            }
            ObfuscationSession.Status.COMPLETED -> {
                if (!task.outputObjectKey.isNullOrBlank()) {
                    task.status = TaskStatus.COMPLETED
                    task.progress = 100
                    task.currentStage = "Completed"
                }
            }
            ObfuscationSession.Status.ERROR -> {
                task.status = TaskStatus.FAILED
                task.message = session.errorMessage ?: task.message.ifBlank { "Task failed" }
                addStage(task, "Failed", session.progress, task.message)
            }
            else -> Unit
        }
        touch(task)
    }

    private fun addStage(task: PlatformTaskRecord, stage: String, progress: Int, message: String) {
        val last = task.stages.lastOrNull()
        if (last != null && last.name == stage && last.progress == progress && last.message == message) return
        task.stages += TaskStageRecord(
            name = stage,
            progress = progress,
            message = message
        )
    }
}

data class PlatformTaskRecord(
    val id: String,
    val projectName: String,
    val inputObjectKey: String,
    val configObjectKey: String?,
    val createdAt: String = Instant.now().toString(),
    var updatedAt: String = Instant.now().toString(),
    var sessionId: String? = null,
    var outputObjectKey: String? = null,
    var status: TaskStatus = TaskStatus.CREATED,
    var currentStage: String = "",
    var progress: Int = 0,
    var message: String = "",
    val logs: MutableList<String> = CopyOnWriteArrayList(),
    val stages: MutableList<TaskStageRecord> = CopyOnWriteArrayList()
)

enum class TaskStatus {
    CREATED,
    RUNNING,
    COMPLETED,
    FAILED
}

data class TaskStageRecord(
    val name: String,
    val progress: Int,
    val message: String,
    val timestamp: String = Instant.now().toString()
)
