package net.spartanb312.grunt.web

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import net.spartanb312.grunt.config.Configs
import net.spartanb312.grunt.gui.util.LoggerPrinter
import net.spartanb312.grunt.utils.logging.Logger
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.time.Duration
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

object WebServer {

    private val gson = Gson()
    private val sessions = ConcurrentHashMap<String, ObfuscationSession>()
    private val wsConsoleSessions = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val wsProgressSessions = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val classNamePattern = Regex("^[A-Za-z0-9_/$.\\-]+$")
    private val executionLock = Any()
    private val sessionRootDir = File(".state/web").absoluteFile

    @Volatile
    private var activeExecutionSessionId: String? = null

    fun start(port: Int = 8080, configPath: String = "config.json") {
        sessionRootDir.mkdirs()

        val originalOut = System.out
        val capturePrinter = LoggerPrinter(originalOut) { line ->
            if (line.isBlank()) return@LoggerPrinter
            activeExecutionSessionId?.let { sessionId ->
                sessions[sessionId]?.log(line)
            }
        }
        System.setOut(capturePrinter)

        Logger.info("Starting web server on port $port...")
        Logger.info("Startup config path: ${File(configPath).absolutePath}")

        val server = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                gson {
                    setPrettyPrinting()
                }
            }
            install(CORS) {
                anyHost()
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Options)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
            }
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(15)
                timeout = Duration.ofSeconds(30)
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }

            routing {
                staticResources()
                apiRoutes()
                wsRoutes()
            }
        }

        server.start(wait = false)
        Logger.info("Web server started at http://localhost:$port/login")

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI("http://localhost:$port/login"))
                Logger.info("Browser opened automatically")
            }
        } catch (_: Exception) {
            Logger.info("Please open http://localhost:$port/login in your browser")
        }

        Thread.currentThread().join()
    }

    private fun Routing.staticResources() {
        suspend fun ApplicationCall.respondHtml(name: String) {
            val resource = this::class.java.classLoader.getResource("web/$name")
            if (resource != null) {
                respondText(resource.readText(), ContentType.Text.Html)
            } else {
                respondText("Web UI not found", status = HttpStatusCode.NotFound)
            }
        }

        get("/") { call.respondHtml("login.html") }
        get("/index.html") { call.respondHtml("index.html") }
        get("/login") { call.respondHtml("login.html") }
        get("/login.html") { call.respondHtml("login.html") }

        get("/css/{file}") {
            val fileName = call.parameters["file"] ?: return@get
            val resource = this::class.java.classLoader.getResource("web/css/$fileName")
            if (resource != null) {
                call.respondText(resource.readText(), ContentType.Text.CSS)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
        get("/js/{file}") {
            val fileName = call.parameters["file"] ?: return@get
            val resource = this::class.java.classLoader.getResource("web/js/$fileName")
            if (resource != null) {
                call.respondText(resource.readText(), ContentType.Application.JavaScript)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
        get("/schema/config-editor.schema.json") {
            call.respondText(buildEditorSchemaJson(), ContentType.Application.Json)
        }
        get("/schema/{file...}") {
            val pathParts = call.parameters.getAll("file")
            if (pathParts.isNullOrEmpty()) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            if (pathParts.any { it.contains("..") || it.contains("\\") || it.contains(":") }) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val filePath = pathParts.joinToString("/")
            val resource = this::class.java.classLoader.getResource("web/schema/$filePath")
            if (resource != null) {
                call.respondText(resource.readText(), ContentType.Application.Json)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
        get("/fonts/{file...}") {
            val pathParts = call.parameters.getAll("file")
            if (pathParts.isNullOrEmpty()) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            if (pathParts.any { it.contains("..") || it.contains("\\") || it.contains(":") }) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val filePath = pathParts.joinToString("/")
            val resource = this::class.java.classLoader.getResource("web/fonts/$filePath")
            if (resource != null) {
                val contentType = when {
                    filePath.endsWith(".woff2", ignoreCase = true) -> ContentType.parse("font/woff2")
                    filePath.endsWith(".woff", ignoreCase = true) -> ContentType.parse("font/woff")
                    else -> ContentType.Application.OctetStream
                }
                call.respondBytes(resource.readBytes(), contentType)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    private fun Routing.apiRoutes() {
        route("/api") {
            post("/session/create") {
                val session = createSession()
                val result = JsonObject().apply {
                    addProperty("status", "ok")
                    addProperty("sessionId", session.id)
                    add("session", buildStatusResponse(session))
                }
                call.respondText(gson.toJson(result), ContentType.Application.Json)
            }

            route("/session/{sessionId}") {
                post("/config") {
                    val session = resolveSession(call) ?: return@post
                    if (!ensureEditable(call, session)) return@post
                    try {
                        val multipart = call.receiveMultipart()
                        var originalName = "config.json"
                        var parsedConfig: JsonObject? = null
                        multipart.forEachPart { part ->
                            if (part is PartData.FileItem) {
                                originalName = sanitizeFileName(part.originalFileName, "config.json")
                                val text = part.streamProvider().bufferedReader(Charsets.UTF_8).use { it.readText() }
                                parsedConfig = JsonParser.parseString(text).asJsonObject
                            }
                            part.dispose()
                        }
                        val json = parsedConfig ?: throw IllegalStateException("No config file uploaded")
                        session.saveConfig(json, originalName)
                        val result = JsonObject().apply {
                            addProperty("status", "ok")
                            addProperty("fileName", originalName)
                            add("config", json)
                            add("session", buildStatusResponse(session))
                        }
                        call.respondText(gson.toJson(result), ContentType.Application.Json)
                    } catch (e: Exception) {
                        respondError(call, HttpStatusCode.BadRequest, e.message ?: "Invalid config file")
                    }
                }

                post("/input") {
                    val session = resolveSession(call) ?: return@post
                    if (!ensureEditable(call, session)) return@post
                    handleInputUpload(call, session)
                }

                post("/libraries") {
                    val session = resolveSession(call) ?: return@post
                    if (!ensureEditable(call, session)) return@post
                    try {
                        val files = receiveUploadedFiles(call.receiveMultipart(), session.librariesDir, multiple = true)
                        if (files.isEmpty()) {
                            respondError(call, HttpStatusCode.BadRequest, "No library files uploaded")
                            return@post
                        }
                        session.addLibraries(files)
                        val result = JsonObject().apply {
                            addProperty("status", "ok")
                            addProperty("count", files.size)
                            add("files", JsonArray().apply { session.getLibraryNames().forEach { add(it) } })
                            add("session", buildStatusResponse(session))
                        }
                        call.respondText(gson.toJson(result), ContentType.Application.Json)
                    } catch (e: Exception) {
                        respondError(call, HttpStatusCode.InternalServerError, e.message ?: "Failed to upload libraries")
                    }
                }

                post("/assets") {
                    val session = resolveSession(call) ?: return@post
                    if (!ensureEditable(call, session)) return@post
                    try {
                        val files = receiveUploadedFiles(call.receiveMultipart(), session.assetsDir, multiple = true)
                        if (files.isEmpty()) {
                            respondError(call, HttpStatusCode.BadRequest, "No asset files uploaded")
                            return@post
                        }
                        session.addAssets(files)
                        val result = JsonObject().apply {
                            addProperty("status", "ok")
                            addProperty("count", files.size)
                            add("files", JsonArray().apply { session.getAssetNames().forEach { add(it) } })
                            add("session", buildStatusResponse(session))
                        }
                        call.respondText(gson.toJson(result), ContentType.Application.Json)
                    } catch (e: Exception) {
                        respondError(call, HttpStatusCode.InternalServerError, e.message ?: "Failed to upload assets")
                    }
                }

                post("/obfuscate") {
                    val session = resolveSession(call) ?: return@post
                    handleSessionObfuscate(call, session)
                }

                get("/status") {
                    val session = resolveSession(call) ?: return@get
                    call.respondText(gson.toJson(buildStatusResponse(session)), ContentType.Application.Json)
                }

                get("/logs") {
                    val session = resolveSession(call) ?: return@get
                    call.respondText(gson.toJson(session.consoleLogs), ContentType.Application.Json)
                }

                get("/download") {
                    val session = resolveSession(call) ?: return@get
                    handleDownload(call, session)
                }

                get("/project/meta") {
                    val session = resolveSession(call) ?: return@get
                    handleProjectMeta(call, session)
                }

                get("/project/tree") {
                    val session = resolveSession(call) ?: return@get
                    handleProjectTree(call, session)
                }

                get("/project/source") {
                    val session = resolveSession(call) ?: return@get
                    handleProjectSource(call, session)
                }
            }
        }
    }

    private fun Routing.wsRoutes() {
        webSocket("/ws/console") {
            val session = resolveSessionFromSocket(this) ?: return@webSocket
            val set = socketSet(wsConsoleSessions, session.id)
            set.add(this)
            try {
                session.consoleLogs.forEach { log ->
                    send(Frame.Text("""{"type":"log","message":"${escapeJson(log)}"}"""))
                }
                for (frame in incoming) {
                    if (frame is Frame.Close) break
                }
            } finally {
                set.remove(this)
            }
        }

        webSocket("/ws/progress") {
            val session = resolveSessionFromSocket(this) ?: return@webSocket
            val set = socketSet(wsProgressSessions, session.id)
            set.add(this)
            try {
                val statusJson = JsonObject().apply {
                    addProperty("step", session.currentStep)
                    addProperty("progress", session.progress)
                    addProperty("status", session.status.name)
                }
                send(Frame.Text(gson.toJson(statusJson)))
                for (frame in incoming) {
                    if (frame is Frame.Close) break
                }
            } finally {
                set.remove(this)
            }
        }
    }

    private fun createSession(): ObfuscationSession {
        val sessionId = UUID.randomUUID().toString()
        return getOrCreateSession(sessionId)
    }

    private fun getOrCreateSession(sessionId: String): ObfuscationSession {
        return sessions.computeIfAbsent(sessionId) { id ->
            ObfuscationSession(id, File(sessionRootDir, id))
        }
    }

    private suspend fun resolveSession(call: ApplicationCall): ObfuscationSession? {
        val sessionId = call.parameters["sessionId"]?.trim().orEmpty()
        if (sessionId.isBlank()) {
            respondError(call, HttpStatusCode.BadRequest, "Missing sessionId")
            return null
        }
        val session = sessions[sessionId]
        if (session == null) {
            respondError(call, HttpStatusCode.NotFound, "Session not found")
            return null
        }
        return session
    }

    private suspend fun resolveSessionFromSocket(socket: DefaultWebSocketServerSession): ObfuscationSession? {
        val sessionId = socket.call.request.queryParameters["sessionId"]?.trim().orEmpty()
        if (sessionId.isBlank()) {
            socket.send(Frame.Text("""{"status":"error","message":"Missing sessionId"}"""))
            socket.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Missing sessionId"))
            return null
        }
        val session = sessions[sessionId]
        if (session == null) {
            socket.send(Frame.Text("""{"status":"error","message":"Session not found"}"""))
            socket.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Session not found"))
            return null
        }
        return session
    }

    private fun socketSet(
        store: ConcurrentHashMap<String, MutableSet<WebSocketSession>>,
        sessionId: String
    ): MutableSet<WebSocketSession> {
        return store.computeIfAbsent(sessionId) { Collections.synchronizedSet(mutableSetOf()) }
    }

    private suspend fun ensureEditable(call: ApplicationCall, session: ObfuscationSession): Boolean {
        if (session.status == ObfuscationSession.Status.RUNNING) {
            respondError(call, HttpStatusCode.Conflict, "Session is currently running")
            return false
        }
        return true
    }

    private suspend fun handleInputUpload(call: ApplicationCall, session: ObfuscationSession) {
        try {
            val files = receiveUploadedFiles(call.receiveMultipart(), session.inputDir, multiple = false)
            val file = files.firstOrNull()
            if (file == null) {
                respondError(call, HttpStatusCode.BadRequest, "No JAR uploaded")
                return
            }
            session.replaceInput(file)
            val result = JsonObject().apply {
                addProperty("status", "ok")
                addProperty("fileName", file.name)
                addProperty("classCount", session.inputClassList?.size ?: 0)
                add("classes", JsonArray().apply { session.inputClassList?.forEach { add(it) } })
                add("session", buildStatusResponse(session))
            }
            call.respondText(gson.toJson(result), ContentType.Application.Json)
        } catch (e: Exception) {
            respondError(call, HttpStatusCode.InternalServerError, e.message ?: "Failed to upload input JAR")
        }
    }

    private suspend fun handleSessionObfuscate(call: ApplicationCall, session: ObfuscationSession) {
        val error = synchronized(executionLock) {
            when {
                activeExecutionSessionId != null -> HttpStatusCode.Conflict to "Another obfuscation task is already running"
                !session.hasUploadedConfig() -> HttpStatusCode.BadRequest to "No config uploaded"
                !session.hasUploadedInput() -> HttpStatusCode.BadRequest to "No input JAR uploaded"
                else -> {
                    activeExecutionSessionId = session.id
                    attachSessionCallbacks(session)
                    thread(name = "obfuscation-${session.id}", priority = Thread.MAX_PRIORITY) {
                        try {
                            session.runObfuscation {
                                applySessionConfig(session)
                            }
                        } finally {
                            activeExecutionSessionId = null
                        }
                    }
                    null
                }
            }
        }

        if (error != null) {
            respondError(call, error.first, error.second)
            return
        }

        call.respondText("""{"status":"started"}""", ContentType.Application.Json)
    }

    private fun attachSessionCallbacks(session: ObfuscationSession) {
        session.onLogMessage = { msg ->
            broadcastConsole(session.id, """{"type":"log","message":"${escapeJson(msg)}"}""")
        }
        session.onProgressUpdate = { progressJson ->
            broadcastProgress(session.id, progressJson)
        }
    }

    private fun applySessionConfig(session: ObfuscationSession) {
        val json = session.loadConfigJson()
        Configs.resetConfig()
        Configs.applyConfig(json)

        val inputPath = session.inputJarPath ?: throw IllegalStateException("No input JAR uploaded")
        Configs.Settings.input = inputPath

        val outputName = sanitizeOutputFileName(Configs.Settings.output)
        Configs.Settings.output = File(session.outputDir, outputName).absolutePath

        Configs.Settings.libraries = session.getLibraryPaths()

        session.resolveAssetPath(Configs.Settings.customDictionary)?.let { assetPath ->
            Configs.Settings.customDictionary = assetPath
        }
    }

    private suspend fun handleProjectMeta(call: ApplicationCall, session: ObfuscationSession) {
        val scope = parseProjectScope(call.parameters["scope"])
        if (scope == null) {
            respondError(call, HttpStatusCode.BadRequest, "Invalid scope")
            return
        }
        val classes = session.getProjectClasses(scope)
        val result = JsonObject().apply {
            addProperty("status", "ok")
            addProperty("scope", scope.name.lowercase())
            addProperty("available", classes != null)
            addProperty("classCount", classes?.size ?: 0)
        }
        call.respondText(gson.toJson(result), ContentType.Application.Json)
    }

    private suspend fun handleProjectTree(call: ApplicationCall, session: ObfuscationSession) {
        val scope = parseProjectScope(call.parameters["scope"])
        if (scope == null) {
            respondError(call, HttpStatusCode.BadRequest, "Invalid scope")
            return
        }
        val classList = session.getProjectClasses(scope)
        if (classList == null) {
            val err = JsonObject().apply {
                addProperty("status", "error")
                addProperty("scope", scope.name.lowercase())
                addProperty("message", "No ${scope.name.lowercase()} class structure available")
            }
            call.respondText(gson.toJson(err), ContentType.Application.Json, HttpStatusCode.NotFound)
            return
        }
        val result = JsonObject().apply {
            addProperty("status", "ok")
            addProperty("scope", scope.name.lowercase())
            addProperty("classCount", classList.size)
            add("classes", JsonArray().apply { classList.forEach { add(it) } })
        }
        call.respondText(gson.toJson(result), ContentType.Application.Json)
    }

    private suspend fun handleProjectSource(call: ApplicationCall, session: ObfuscationSession) {
        val scope = parseProjectScope(call.parameters["scope"])
        if (scope == null) {
            respondError(call, HttpStatusCode.BadRequest, "Invalid scope")
            return
        }

        val className = call.parameters["class"]?.trim().orEmpty()
        if (!isValidClassName(className)) {
            respondError(call, HttpStatusCode.BadRequest, "Invalid class name")
            return
        }

        try {
            val code = session.decompileClass(scope, className)
            val result = JsonObject().apply {
                addProperty("status", "ok")
                addProperty("scope", scope.name.lowercase())
                addProperty("class", className)
                addProperty("language", "java")
                addProperty("code", code)
            }
            call.respondText(gson.toJson(result), ContentType.Application.Json)
        } catch (_: NoSuchElementException) {
            respondError(call, HttpStatusCode.NotFound, "Class not found", className)
        } catch (_: IllegalStateException) {
            respondError(call, HttpStatusCode.NotFound, "No ${scope.name.lowercase()} JAR available", className)
        } catch (_: IllegalArgumentException) {
            respondError(call, HttpStatusCode.BadRequest, "Invalid class name", className)
        } catch (e: Exception) {
            respondError(call, HttpStatusCode.InternalServerError, e.message ?: "Failed to decompile class", className)
        }
    }

    private suspend fun handleDownload(call: ApplicationCall, session: ObfuscationSession) {
        val outputPath = session.outputJarPath
        if (outputPath == null || !File(outputPath).exists()) {
            val err = JsonObject()
            err.addProperty("error", "No output file available")
            call.respondText(gson.toJson(err), ContentType.Application.Json, HttpStatusCode.NotFound)
            return
        }
        val file = File(outputPath)
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName,
                file.name
            ).toString()
        )
        call.respondFile(file)
    }

    private fun buildStatusResponse(session: ObfuscationSession): JsonObject {
        return JsonObject().apply {
            addProperty("status", session.status.name)
            addProperty("sessionId", session.id)
            addProperty("currentStep", session.currentStep)
            addProperty("progress", session.progress)
            addProperty("totalSteps", session.totalSteps)
            addProperty("configUploaded", session.hasUploadedConfig())
            addProperty("inputUploaded", session.hasUploadedInput())
            addProperty("outputAvailable", session.hasOutput())
            session.configDisplayName?.let { addProperty("configFileName", it) }
            session.inputDisplayName?.let { addProperty("inputFileName", it) }
            addProperty("libraryCount", session.getLibraryNames().size)
            add("libraryFiles", JsonArray().apply { session.getLibraryNames().forEach { add(it) } })
            addProperty("assetCount", session.getAssetNames().size)
            add("assetFiles", JsonArray().apply { session.getAssetNames().forEach { add(it) } })
            session.errorMessage?.let { addProperty("error", it) }
        }
    }

    private suspend fun respondError(
        call: ApplicationCall,
        status: HttpStatusCode,
        message: String,
        className: String? = null
    ) {
        val err = JsonObject().apply {
            addProperty("status", "error")
            addProperty("message", message)
            className?.let { addProperty("class", it) }
        }
        call.respondText(gson.toJson(err), ContentType.Application.Json, status)
    }

    private fun parseProjectScope(rawScope: String?): ObfuscationSession.ProjectScope? {
        return when (rawScope?.lowercase()) {
            "input" -> ObfuscationSession.ProjectScope.INPUT
            "output" -> ObfuscationSession.ProjectScope.OUTPUT
            else -> null
        }
    }

    private fun isValidClassName(className: String): Boolean {
        if (className.isBlank()) return false
        if (className.contains("..")) return false
        if (className.startsWith("/") || className.startsWith("\\")) return false
        if (className.contains(":")) return false
        return classNamePattern.matches(className)
    }

    private suspend fun receiveUploadedFiles(
        multipart: MultiPartData,
        targetDir: File,
        multiple: Boolean
    ): List<File> {
        targetDir.mkdirs()
        val files = mutableListOf<File>()
        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                val safeName = sanitizeFileName(part.originalFileName, "upload.bin")
                val target = uniqueFile(targetDir, safeName)
                part.streamProvider().use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                files.add(target.absoluteFile)
            }
            part.dispose()
        }
        return if (multiple) files else files.take(1)
    }

    private fun uniqueFile(dir: File, preferredName: String): File {
        val base = File(preferredName).name.ifBlank { "upload.bin" }
        val dot = base.lastIndexOf('.')
        val name = if (dot > 0) base.substring(0, dot) else base
        val ext = if (dot > 0) base.substring(dot) else ""
        var candidate = File(dir, base)
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$name-$index$ext")
            index++
        }
        return candidate
    }

    private fun sanitizeFileName(name: String?, fallback: String): String {
        val raw = File(name?.trim().takeUnless { it.isNullOrBlank() } ?: fallback).name
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return if (cleaned.isBlank()) fallback else cleaned
    }

    private fun sanitizeOutputFileName(configuredValue: String?): String {
        val raw = File(configuredValue?.trim().takeUnless { it.isNullOrBlank() } ?: "output.jar").name
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return if (cleaned.isBlank()) "output.jar" else cleaned
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }

    private fun broadcastConsole(sessionId: String, message: String) {
        val targets = wsConsoleSessions[sessionId] ?: return
        synchronized(targets) {
            targets.forEach { ws ->
                try {
                    kotlinx.coroutines.runBlocking { ws.send(Frame.Text(message)) }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun broadcastProgress(sessionId: String, payload: String) {
        val targets = wsProgressSessions[sessionId] ?: return
        synchronized(targets) {
            targets.forEach { ws ->
                try {
                    kotlinx.coroutines.runBlocking { ws.send(Frame.Text(payload)) }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun buildEditorSchemaJson(): String {
        val resource = this::class.java.classLoader.getResource("web/schema/config-schema.json")
            ?: return "{}"
        val root = JsonParser.parseString(resource.readText()).asJsonObject
        val sections = root["sections"]?.asJsonArray ?: return gson.toJson(root)
        sections.forEach { sectionElement ->
            val fields = sectionElement.asJsonObject["fields"]?.asJsonArray ?: return@forEach
            fields.forEach fieldLoop@{ fieldElement ->
                val field = fieldElement.asJsonObject
                val pathKey = field["path"]?.asJsonArray?.joinToString(".") { it.asString } ?: return@fieldLoop
                when (pathKey) {
                    "Settings.Input" -> {
                        field.addProperty("description", "由顶部“上传主 JAR”按钮控制，远程执行时会自动映射到服务器会话目录。")
                        field.addProperty("fileBinding", "input")
                        field.addProperty("readOnly", true)
                    }
                    "Settings.Output" -> {
                        field.addProperty("description", "保留配置中的输出文件名，服务器会将结果写入当前会话的 output 目录。")
                    }
                    "Settings.Libraries" -> {
                        field.addProperty("description", "由顶部“上传依赖”按钮维护，远程执行时会自动替换为服务器上的依赖文件列表。")
                        field.addProperty("fileBinding", "libraries")
                        field.addProperty("readOnly", true)
                    }
                    "Settings.CustomDictionaryFile" -> {
                        field.addProperty("description", "上传附件后可在此绑定字典文件；执行时会自动替换为服务器中的附件路径。")
                        field.addProperty("fileBinding", "asset")
                    }
                }
            }
        }
        return gson.toJson(root)
    }
}
