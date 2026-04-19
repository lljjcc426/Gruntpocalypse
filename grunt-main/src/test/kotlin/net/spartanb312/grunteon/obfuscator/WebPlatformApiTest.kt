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
import net.spartanb312.grunteon.obfuscator.web.WebServer
import net.spartanb312.grunteon.obfuscator.web.grunteonWebModule
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebPlatformApiTest {

    @Test
    fun platformTaskHappyPath() = testApplication {
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
        assertEquals(HttpStatusCode.OK, putInputRes.status)

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

    private fun prepareInputJar(): Path {
        val classRoot = Path.of("..", "grunt-testcase", "build", "classes", "java", "main").normalize().toAbsolutePath()
        require(classRoot.exists()) { "缺少 grunt-testcase 编译输出: ${classRoot.absolutePathString()}" }
        val classFile = classRoot.resolve(
            "net/spartanb312/grunteon/testcase/methodrename/functional/Basic.class"
        )
        require(classFile.exists()) { "缺少测试 class 文件: ${classFile.absolutePathString()}" }

        val jar = Files.createTempFile("grunteon-platform-", ".jar")
        JarOutputStream(Files.newOutputStream(jar)).use { jos ->
            jos.putNextEntry(JarEntry("net/spartanb312/grunteon/testcase/methodrename/functional/Basic.class"))
            classFile.inputStream().use { input -> input.copyTo(jos) }
            jos.closeEntry()
        }
        return jar
    }

    private suspend fun io.ktor.client.statement.HttpResponse.jsonObject(): JsonObject {
        return JsonParser.parseString(bodyAsText()).asJsonObject
    }
}
