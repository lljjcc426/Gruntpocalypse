package net.spartanb312.grunteon.obfuscator

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import net.spartanb312.grunteon.obfuscator.web.FilesystemObjectStorageBackend
import net.spartanb312.grunteon.obfuscator.web.ObjectStorageService
import net.spartanb312.grunteon.obfuscator.web.PlatformTaskService
import net.spartanb312.grunteon.obfuscator.web.SessionExecutionGateway
import net.spartanb312.grunteon.obfuscator.web.SessionService
import net.spartanb312.grunteon.obfuscator.web.WebServer
import net.spartanb312.grunteon.obfuscator.web.grunteonWebModule
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebPlatformApiTest {

    @Test
    fun platformTaskHappyPath() {
        val previousMinioEnabled = System.getProperty("grunteon.minio.enabled")
        System.setProperty("grunteon.minio.enabled", "false")
        val previousWebServerState = overrideWebServerObjectStorageForTest()
        try {
            testApplication {
                application {
                    grunteonWebModule()
                }

                val inputJar = prepareInputJar()
                val configText = """{"Settings":{"Output":"artifact-obf.jar"}}"""

                val uploadTicketRes = client.post("/api/v1/artifacts/upload-url") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"fileName":"input.jar"}""")
                }
                assertEquals(HttpStatusCode.OK, uploadTicketRes.status)
                val uploadTicket = uploadTicketRes.jsonObject()
                val inputObjectKey = uploadTicket.get("objectKey").asString
                val uploadUrl = uploadTicket.get("uploadUrl").asString

                val putInputRes = client.put(uploadUrl) {
                    contentType(ContentType.Application.OctetStream)
                    setBody(Files.readAllBytes(inputJar))
                }
                val putInputBody = putInputRes.bodyAsText()
                println("PUT input response: ${putInputRes.status} :: $putInputBody")
                assertEquals(HttpStatusCode.OK, putInputRes.status, putInputBody)

                val configUploadTicketRes = client.post("/api/v1/artifacts/upload-url") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"fileName":"config.json"}""")
                }
                assertEquals(HttpStatusCode.OK, configUploadTicketRes.status)
                val configUploadTicket = configUploadTicketRes.jsonObject()
                val configObjectKey = configUploadTicket.get("objectKey").asString
                val configUploadUrl = configUploadTicket.get("uploadUrl").asString

                val putConfigRes = client.put(configUploadUrl) {
                    contentType(ContentType.Application.OctetStream)
                    setBody(configText)
                }
                assertEquals(HttpStatusCode.OK, putConfigRes.status)

                val createTaskRes = client.post("/api/v1/tasks") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "projectName":"Grunteon Platform Task",
                          "inputObjectKey":"$inputObjectKey",
                          "configObjectKey":"$configObjectKey"
                        }
                        """.trimIndent()
                    )
                }
                assertEquals(HttpStatusCode.OK, createTaskRes.status)
                val createTaskJson = createTaskRes.jsonObject()
                val taskId = createTaskJson.getAsJsonObject("task").get("id").asString

                val startTaskRes = client.post("/api/v1/tasks/$taskId/start") {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
                assertEquals(HttpStatusCode.OK, startTaskRes.status)

                var latestTask = startTaskRes.jsonObject().getAsJsonObject("task")
                repeat(120) {
                    if (latestTask.get("status").asString == "COMPLETED") return@repeat
                    Thread.sleep(250)
                    latestTask = client.get("/api/v1/tasks/$taskId").jsonObject().getAsJsonObject("task")
                }
                assertEquals("COMPLETED", latestTask.get("status").asString)
                assertEquals(100, latestTask.get("progress").asInt)
                assertTrue(latestTask.get("outputObjectKey").asString.isNotBlank())
                assertTrue(latestTask.getAsJsonObject("session").get("outputAvailable").asBoolean)
                assertTrue(latestTask.get("inputClassCount").asInt >= 1)
                assertTrue(latestTask.get("outputClassCount").asInt >= 1)

                val listTasksRes = client.get("/api/v1/tasks")
                assertEquals(HttpStatusCode.OK, listTasksRes.status)
                val listedTasks = listTasksRes.jsonObject().getAsJsonArray("tasks")
                assertTrue(listedTasks.any { it.asJsonObject.get("id").asString == taskId })

                val stagesRes = client.get("/api/v1/tasks/$taskId/stages")
                assertEquals(HttpStatusCode.OK, stagesRes.status)
                val stages = stagesRes.jsonObject().getAsJsonArray("stages")
                assertTrue(stages.size() >= 2)
                assertEquals("Completed", stages.last().asJsonObject.get("name").asString)

                val logsRes = client.get("/api/v1/tasks/$taskId/logs")
                assertEquals(HttpStatusCode.OK, logsRes.status)
                val logs = logsRes.jsonObject().getAsJsonArray("logs")
                assertTrue(logs.size() >= 1)

                val inputMetaRes = client.get("/api/v1/tasks/$taskId/project/meta?scope=input")
                assertEquals(HttpStatusCode.OK, inputMetaRes.status)
                val inputMeta = inputMetaRes.jsonObject()
                assertTrue(inputMeta.get("available").asBoolean)
                assertTrue(inputMeta.get("classCount").asInt >= 1)

                val outputTreeRes = client.get("/api/v1/tasks/$taskId/project/tree?scope=output")
                assertEquals(HttpStatusCode.OK, outputTreeRes.status)
                val outputTree = outputTreeRes.jsonObject()
                val classes = outputTree.getAsJsonArray("classes")
                assertTrue(classes.any { it.asString == "net/spartanb312/grunteon/testcase/methodrename/functional/Basic" })

                val inputSourceRes = client.get(
                    "/api/v1/tasks/$taskId/project/source?scope=input&class=net/spartanb312/grunteon/testcase/methodrename/functional/Basic"
                )
                assertEquals(HttpStatusCode.OK, inputSourceRes.status)
                val inputSource = inputSourceRes.jsonObject()
                assertTrue(inputSource.get("code").asString.contains("class Basic"))

                val downloadTicketRes = client.get("/api/v1/tasks/$taskId/download-url")
                assertEquals(HttpStatusCode.OK, downloadTicketRes.status)
                val downloadTicket = downloadTicketRes.jsonObject()
                val downloadUrl = downloadTicket.get("downloadUrl").asString

                val downloadRes = client.get(downloadUrl)
                assertEquals(HttpStatusCode.OK, downloadRes.status)
                assertTrue(downloadRes.body<ByteArray>().isNotEmpty())

                val directDownloadRes = client.get("/api/v1/tasks/$taskId/download")
                assertEquals(HttpStatusCode.OK, directDownloadRes.status)
                assertTrue(directDownloadRes.body<ByteArray>().isNotEmpty())
            }
        } finally {
            restoreWebServerState(previousWebServerState)
            if (previousMinioEnabled == null) {
                System.clearProperty("grunteon.minio.enabled")
            } else {
                System.setProperty("grunteon.minio.enabled", previousMinioEnabled)
            }
        }
    }

    private fun prepareInputJar(): Path {
        val classResourcePath = "net/spartanb312/grunteon/testcase/methodrename/functional/Basic.class"
        val classBytes = checkNotNull(javaClass.classLoader.getResourceAsStream(classResourcePath)) {
            "Missing testcase class on test runtime classpath: $classResourcePath"
        }.use { it.readBytes() }

        val jar = Files.createTempFile("grunteon-platform-", ".jar")
        JarOutputStream(Files.newOutputStream(jar)).use { jos ->
            jos.putNextEntry(JarEntry(classResourcePath))
            jos.write(classBytes)
            jos.closeEntry()
        }
        return jar
    }

    private fun overrideWebServerObjectStorageForTest(): Pair<ObjectStorageService, PlatformTaskService> {
        val replacementStorage = ObjectStorageService(FilesystemObjectStorageBackend())
        val sessionService = getWebServerField<SessionService>("sessionService")
        val sessionExecutionGateway = getWebServerField<SessionExecutionGateway>("sessionExecutionGateway")
        val replacementPlatformTaskService = PlatformTaskService(
            sessionService,
            replacementStorage,
            sessionExecutionGateway
        )

        val previousStorage = getWebServerField<ObjectStorageService>("objectStorageService")
        val previousPlatformTaskService = getWebServerField<PlatformTaskService>("platformTaskService")

        setWebServerField("objectStorageService", replacementStorage)
        setWebServerField("platformTaskService", replacementPlatformTaskService)
        return previousStorage to previousPlatformTaskService
    }

    private fun restoreWebServerState(previous: Pair<ObjectStorageService, PlatformTaskService>) {
        setWebServerField("objectStorageService", previous.first)
        setWebServerField("platformTaskService", previous.second)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getWebServerField(name: String): T {
        val field = WebServer::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(WebServer) as T
    }

    private fun setWebServerField(name: String, value: Any) {
        val field = WebServer::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(WebServer, value)
    }

    private suspend fun io.ktor.client.statement.HttpResponse.jsonObject(): JsonObject {
        return JsonParser.parseString(bodyAsText()).asJsonObject
    }
}
