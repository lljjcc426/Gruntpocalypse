package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.web.FilesystemObjectStorageBackend
import net.spartanb312.grunteon.obfuscator.web.ObjectStorageService
import net.spartanb312.grunteon.obfuscator.web.ObfuscationSession
import net.spartanb312.grunteon.obfuscator.web.PlatformTaskService
import net.spartanb312.grunteon.obfuscator.web.SessionAccessProfile
import net.spartanb312.grunteon.obfuscator.web.SessionExecutionGateway
import net.spartanb312.grunteon.obfuscator.web.SessionService
import net.spartanb312.grunteon.obfuscator.web.StartResult
import net.spartanb312.grunteon.obfuscator.web.TaskStatus
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformTaskServiceWorkerGatewayRemoteTest {

    @Test
    fun taskCompletesWhenRemoteWorkerReturnsOutputObjectKey() {
        val root = Files.createTempDirectory("grunteon-remote-task-test")
        val sessionService = SessionService(root.resolve("sessions").toFile())
        val objectStorage = ObjectStorageService(
            FilesystemObjectStorageBackend(root.resolve("objects").toFile())
        )

        val inputKey = objectStorage.createManagedObjectKey("input.jar", "input")
        objectStorage.putObject(inputKey, buildStubJarBytes())
        val configKey = objectStorage.createManagedObjectKey("config.json", "config")
        objectStorage.putObject(configKey, """{"Settings":{"Output":"artifact-obf.jar"}}""".toByteArray())

        val taskService = PlatformTaskService(
            sessionService = sessionService,
            objectStorageService = objectStorage,
            sessionExecutionGateway = RemoteSessionExecutionGatewayStub(objectStorage),
            workerPlaneName = "remote-worker"
        )

        val task = taskService.createTask(
            projectName = "Remote Worker Task",
            inputObjectKey = inputKey,
            configObjectKey = configKey,
            accessProfile = SessionAccessProfile.SECURE
        )

        val started = taskService.startTask(task.id)

        assertEquals(TaskStatus.COMPLETED, started.status)
        assertEquals(100, started.progress)
        assertEquals("Completed", started.currentStage)
        assertTrue(started.logs.any { it.contains("remote worker running") })
        assertNotNull(started.outputObjectKey)
        assertTrue(started.outputObjectKey!!.startsWith("artifacts/output/remote/"))

        val session = sessionService.getSession(started.sessionId!!)
        assertNotNull(session)
        assertEquals("remote-worker", session.workerPlane)
        assertEquals(ObfuscationSession.Status.COMPLETED, session.status)
        assertEquals(started.outputObjectKey, session.outputObjectKey)
    }

    private class RemoteSessionExecutionGatewayStub(
        private val objectStorage: ObjectStorageService
    ) : SessionExecutionGateway {

        override fun startSession(
            session: ObfuscationSession,
            onFinish: (() -> Unit)?,
            onStart: (() -> Unit)?
        ): StartResult {
            onStart?.invoke()
            session.applyExternalState(
                status = "RUNNING",
                currentStep = "Worker Running",
                progress = 55,
                totalSteps = 4,
                errorMessage = null,
                outputObjectKey = null,
                logs = listOf("remote worker running")
            )
            val outputKey = "artifacts/output/remote/${session.id}/artifact-obf.jar"
            objectStorage.putObject(outputKey, buildStubJarBytes())
            session.applyExternalState(
                status = "COMPLETED",
                currentStep = "Completed",
                progress = 100,
                totalSteps = 4,
                errorMessage = null,
                outputObjectKey = outputKey,
                logs = listOf("remote worker running", "remote worker completed")
            )
            onFinish?.invoke()
            return StartResult.Started
        }
    }

    companion object {
        private fun buildStubJarBytes(): ByteArray {
            val tempJar = Files.createTempFile("grunteon-remote-input", ".jar")
            JarOutputStream(Files.newOutputStream(tempJar)).use { jos ->
                jos.putNextEntry(JarEntry("sample/Demo.class"))
                jos.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
                jos.closeEntry()
            }
            return Files.readAllBytes(tempJar)
        }
    }
}
