package net.spartanb312.grunteon.obfuscator.web

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
import net.spartanb312.grunteon.obfuscator.util.Logger
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay

object WebServer {
    private val gson = Gson()
    private val wsConsoleSessions = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val wsProgressSessions = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val sessionRootDir = File(".state/web").absoluteFile
    private val classNamePattern = Regex("^[A-Za-z0-9_/$.\\-]+$")
    private val sessionService = SessionService(sessionRootDir)
    private val projectInspectionService = ProjectInspectionService()
    private val obfuscationService = ObfuscationService()
    private val sessionExecutionGateway = LocalSessionExecutionGateway(obfuscationService)
    private val objectStorageService = ObjectStorageService()
    private val platformTaskService = PlatformTaskService(sessionService, objectStorageService, sessionExecutionGateway)

    fun start(port: Int = 8080) {
        sessionRootDir.mkdirs()

        Logger.info("Starting web server on port $port...")
        val server = embeddedServer(Netty, port = port, module = Application::grunteonWebModule)

        server.start(wait = false)
        Logger.info("Web server started at http://localhost:$port/login")

        tryOpenBrowser(port)
        Thread.currentThread().join()
    }

    private fun tryOpenBrowser(port: Int) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI("http://localhost:$port/login"))
                Logger.info("Browser opened automatically")
            }
        } catch (_: Exception) {
            Logger.info("Please open http://localhost:$port/login in your browser")
        }
    }

    internal fun Routing.staticResources() {
        suspend fun ApplicationCall.respondHtml(name: String) {
            val resource = this::class.java.classLoader.getResource("web/$name")
            if (resource != null) {
                respondText(resource.readText(), ContentType.Text.Html)
            } else {
                respondText("Web UI not found", status = HttpStatusCode.NotFound)
            }
        }

        get("/") { call.respondHtml("login.html") }
        get("/login") { call.respondHtml("login.html") }
        get("/login.html") { call.respondHtml("login.html") }
        get("/index.html") { call.respondHtml("index.html") }

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
            call.respondText(WebConfigSchema.loadSchemaText(), ContentType.Application.Json)
        }

        get("/fonts/{file...}") {
            val pathParts = call.parameters.getAll("file")
            if (pathParts.isNullOrEmpty() || pathParts.any { it.contains("..") || it.contains("\\") || it.contains(":") }) {
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

    internal fun Routing.apiRoutes() {
        route("/api") {
            post("/session/create") {
                val session = sessionService.createSession()
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
                        sessionService.saveConfig(session, json, originalName)
                        val result = JsonObject().apply {
                            addProperty("status", "ok")
                            addProperty("fileName", originalName)
                            add("config", json)
                            add("session", buildStatusResponse(session))
                        }
                        call.respondText(gson.toJson(result), ContentType.Application.Json)
                    } catch (exception: Exception) {
                        respondError(call, HttpStatusCode.BadRequest, exception.message ?: "Invalid config file")
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
                        sessionService.saveLibraries(session, files)
                        val result = JsonObject().apply {
                            addProperty("status", "ok")
                            addProperty("count", files.size)
                            add("files", JsonArray().apply { session.getLibraryNames().forEach(::add) })
                            add("session", buildStatusResponse(session))
                        }
                        call.respondText(gson.toJson(result), ContentType.Application.Json)
                    } catch (exception: Exception) {
                        respondError(call, HttpStatusCode.InternalServerError, exception.message ?: "Failed to upload libraries")
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
                        sessionService.saveAssets(session, files)
                        val result = JsonObject().apply {
                            addProperty("status", "ok")
                            addProperty("count", files.size)
                            add("files", JsonArray().apply { session.getAssetNames().forEach(::add) })
                            add("session", buildStatusResponse(session))
                        }
                        call.respondText(gson.toJson(result), ContentType.Application.Json)
                    } catch (exception: Exception) {
                        respondError(call, HttpStatusCode.InternalServerError, exception.message ?: "Failed to upload assets")
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

        route("/api/v1") {
            post("/artifacts/upload-url") {
                val payload = runCatching { call.receiveText() }.getOrDefault("{}")
                val body = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrDefault(JsonObject())
                val ticket = objectStorageService.createUploadTicket(body.get("fileName")?.asString)
                val result = JsonObject().apply {
                    addProperty("status", "ok")
                    addProperty("objectKey", ticket.objectKey)
                    addProperty("method", ticket.method)
                    addProperty("uploadUrl", ticket.url)
                    addProperty("expiresAt", ticket.expiresAt)
                }
                call.respondText(gson.toJson(result), ContentType.Application.Json)
            }

            put("/storage/{path...}") {
                val pathParts = call.parameters.getAll("path")
                if (pathParts.isNullOrEmpty()) {
                    respondError(call, HttpStatusCode.BadRequest, "Invalid object key")
                    return@put
                }
                val objectKey = pathParts.joinToString("/")
                val bytes = call.receiveStream().readBytes()
                runCatching {
                    objectStorageService.putObject(objectKey, bytes)
                }.onFailure {
                    respondError(call, HttpStatusCode.BadRequest, it.message ?: "Failed to write object")
                    return@put
                }
                val result = JsonObject().apply {
                    addProperty("status", "ok")
                    addProperty("objectKey", objectKey)
                    addProperty("size", bytes.size)
                }
                call.respondText(gson.toJson(result), ContentType.Application.Json)
            }

            get("/storage/{path...}") {
                val pathParts = call.parameters.getAll("path")
                if (pathParts.isNullOrEmpty()) {
                    respondError(call, HttpStatusCode.BadRequest, "Invalid object key")
                    return@get
                }
                val objectKey = pathParts.joinToString("/")
                val file = runCatching { objectStorageService.getObject(objectKey) }.getOrElse {
                    respondError(call, HttpStatusCode.NotFound, it.message ?: "Object not found")
                    return@get
                }
                call.respondFile(file)
            }

            post("/tasks") {
                val payload = runCatching { JsonParser.parseString(call.receiveText()).asJsonObject }.getOrDefault(JsonObject())
                val projectName = payload.get("projectName")?.asString ?: "Grunteon Task"
                val inputObjectKey = payload.get("inputObjectKey")?.asString
                if (inputObjectKey.isNullOrBlank()) {
                    respondError(call, HttpStatusCode.BadRequest, "Missing inputObjectKey")
                    return@post
                }
                val configObjectKey = payload.get("configObjectKey")?.asString
                val task = runCatching {
                    platformTaskService.createTask(projectName, inputObjectKey, configObjectKey)
                }.getOrElse {
                    respondError(call, HttpStatusCode.BadRequest, it.message ?: "Failed to create task")
                    return@post
                }
                call.respondText(gson.toJson(buildTaskResponse(task)), ContentType.Application.Json)
            }

            get("/tasks") {
                val result = JsonObject().apply {
                    addProperty("status", "ok")
                    add("tasks", JsonArray().apply {
                        platformTaskService.listTasks().forEach { add(buildTaskJson(it)) }
                    })
                }
                call.respondText(gson.toJson(result), ContentType.Application.Json)
            }

            get("/tasks/{taskId}") {
                val taskId = call.parameters["taskId"].orEmpty()
                val task = runCatching { platformTaskService.getTask(taskId) }.getOrElse {
                    respondError(call, HttpStatusCode.NotFound, it.message ?: "Task not found")
                    return@get
                }
                call.respondText(gson.toJson(buildTaskResponse(task)), ContentType.Application.Json)
            }

            get("/tasks/{taskId}/stages") {
                val taskId = call.parameters["taskId"].orEmpty()
                val task = runCatching { platformTaskService.getTask(taskId) }.getOrElse {
                    respondError(call, HttpStatusCode.NotFound, it.message ?: "Task not found")
                    return@get
                }
                val result = JsonObject().apply {
                    addProperty("status", "ok")
                    add("stages", JsonArray().apply {
                        task.stages.forEach { add(buildTaskStageJson(it)) }
                    })
                }
                call.respondText(gson.toJson(result), ContentType.Application.Json)
            }

            get("/tasks/{taskId}/logs") {
                val taskId = call.parameters["taskId"].orEmpty()
                val logs = runCatching { platformTaskService.getTaskLogs(taskId) }.getOrElse {
                    respondError(call, HttpStatusCode.NotFound, it.message ?: "Task not found")
                    return@get
                }
                val result = JsonObject().apply {
                    addProperty("status", "ok")
                    add("logs", JsonArray().apply {
                        logs.forEach { add(it) }
                    })
                }
                call.respondText(gson.toJson(result), ContentType.Application.Json)
            }

            route("/tasks/{taskId}/project") {
                get("/meta") {
                    val session = resolveTaskSession(call) ?: return@get
                    handleProjectMeta(call, session)
                }

                get("/tree") {
                    val session = resolveTaskSession(call) ?: return@get
                    handleProjectTree(call, session)
                }

                get("/source") {
                    val session = resolveTaskSession(call) ?: return@get
                    handleProjectSource(call, session)
                }
            }

            post("/tasks/{taskId}/start") {
                val taskId = call.parameters["taskId"].orEmpty()
                val task = runCatching { platformTaskService.startTask(taskId) }.getOrElse {
                    respondError(call, HttpStatusCode.BadRequest, it.message ?: "Failed to start task")
                    return@post
                }
                call.respondText(gson.toJson(buildTaskResponse(task)), ContentType.Application.Json)
            }

            get("/tasks/{taskId}/download-url") {
                val taskId = call.parameters["taskId"].orEmpty()
                val task = runCatching { platformTaskService.getTask(taskId) }.getOrElse {
                    respondError(call, HttpStatusCode.NotFound, it.message ?: "Task not found")
                    return@get
                }
                val outputObjectKey = task.outputObjectKey
                if (outputObjectKey.isNullOrBlank()) {
                    respondError(call, HttpStatusCode.NotFound, "No output object available")
                    return@get
                }
                val ticket = runCatching { objectStorageService.createDownloadTicket(outputObjectKey) }.getOrElse {
                    respondError(call, HttpStatusCode.NotFound, it.message ?: "Object not found")
                    return@get
                }
                val result = JsonObject().apply {
                    addProperty("status", "ok")
                    addProperty("objectKey", ticket.objectKey)
                    addProperty("method", ticket.method)
                    addProperty("downloadUrl", ticket.url)
                    addProperty("expiresAt", ticket.expiresAt)
                }
                call.respondText(gson.toJson(result), ContentType.Application.Json)
            }

            get("/tasks/{taskId}/download") {
                val taskId = call.parameters["taskId"].orEmpty()
                val task = runCatching { platformTaskService.getTask(taskId) }.getOrElse {
                    respondError(call, HttpStatusCode.NotFound, it.message ?: "Task not found")
                    return@get
                }
                val outputObjectKey = task.outputObjectKey
                if (outputObjectKey.isNullOrBlank()) {
                    respondError(call, HttpStatusCode.NotFound, "No output object available")
                    return@get
                }
                val file = runCatching { objectStorageService.getObject(outputObjectKey) }.getOrElse {
                    respondError(call, HttpStatusCode.NotFound, it.message ?: "Object not found")
                    return@get
                }
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, file.name).toString()
                )
                call.respondFile(file)
            }

            get("/tasks/{taskId}/events") {
                val taskId = call.parameters["taskId"].orEmpty()
                val task = runCatching { platformTaskService.getTask(taskId) }.getOrElse {
                    respondError(call, HttpStatusCode.NotFound, it.message ?: "Task not found")
                    return@get
                }
                call.response.cacheControl(CacheControl.NoCache(null))
                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    var stageCursor = 0
                    var logCursor = 0
                    while (true) {
                        val current = runCatching { platformTaskService.getTask(taskId) }.getOrElse { break }
                        while (stageCursor < current.stages.size) {
                            val stage = current.stages[stageCursor++]
                            write("data: ${gson.toJson(buildTaskEventJson(current, stage))}\n\n")
                            flush()
                        }
                        while (logCursor < current.logs.size) {
                            val log = current.logs[logCursor++]
                            val event = JsonObject().apply {
                                addProperty("type", "log")
                                addProperty("taskId", current.id)
                                addProperty("status", current.status.name)
                                addProperty("message", log)
                                addProperty("timestamp", current.updatedAt)
                            }
                            write("data: ${gson.toJson(event)}\n\n")
                            flush()
                        }
                        if (current.status == TaskStatus.COMPLETED || current.status == TaskStatus.FAILED) {
                            break
                        }
                        delay(500)
                    }
                }
            }
        }
    }

    private suspend fun resolveTaskSession(call: ApplicationCall): ObfuscationSession? {
        val taskId = call.parameters["taskId"]?.trim().orEmpty()
        if (taskId.isBlank()) {
            respondError(call, HttpStatusCode.BadRequest, "Missing taskId")
            return null
        }
        val task = runCatching { platformTaskService.getTask(taskId) }.getOrElse {
            respondError(call, HttpStatusCode.NotFound, it.message ?: "Task not found")
            return null
        }
        val sessionId = task.sessionId
        if (sessionId.isNullOrBlank()) {
            respondError(call, HttpStatusCode.NotFound, "Task session not available")
            return null
        }
        val session = sessionService.getSession(sessionId)
        if (session == null) {
            respondError(call, HttpStatusCode.NotFound, "Session not found")
            return null
        }
        return session
    }

    internal fun Routing.wsRoutes() {
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

    private suspend fun resolveSession(call: ApplicationCall): ObfuscationSession? {
        val sessionId = call.parameters["sessionId"]?.trim().orEmpty()
        if (sessionId.isBlank()) {
            respondError(call, HttpStatusCode.BadRequest, "Missing sessionId")
            return null
        }
        val session = sessionService.getSession(sessionId)
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
        val session = sessionService.getSession(sessionId)
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
            sessionService.saveInput(session, file)
            val result = JsonObject().apply {
                addProperty("status", "ok")
                addProperty("fileName", file.name)
                addProperty("classCount", session.inputClassList?.size ?: 0)
                add("classes", JsonArray().apply { session.inputClassList?.forEach(::add) })
                add("session", buildStatusResponse(session))
            }
            call.respondText(gson.toJson(result), ContentType.Application.Json)
        } catch (exception: Exception) {
            respondError(call, HttpStatusCode.InternalServerError, exception.message ?: "Failed to upload input JAR")
        }
    }

    private suspend fun handleSessionObfuscate(call: ApplicationCall, session: ObfuscationSession) {
        attachSessionCallbacks(session)
        when (obfuscationService.start(session, buildExecutionConfig(session))) {
            StartResult.Started -> {
                call.respondText("""{"status":"started"}""", ContentType.Application.Json)
            }
            StartResult.Busy -> {
                respondError(call, HttpStatusCode.Conflict, "Another obfuscation task is already running")
            }
            StartResult.MissingConfig -> {
                respondError(call, HttpStatusCode.BadRequest, "No config uploaded")
            }
            StartResult.MissingInput -> {
                respondError(call, HttpStatusCode.BadRequest, "No input JAR uploaded")
            }
        }
    }

    private fun buildExecutionConfig(session: ObfuscationSession): net.spartanb312.grunteon.obfuscator.ObfConfig {
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

    private fun attachSessionCallbacks(session: ObfuscationSession) {
        session.onLogMessage = { message ->
            broadcastConsole(session.id, """{"type":"log","message":"${escapeJson(message)}"}""")
        }
        session.onProgressUpdate = { payload ->
            broadcastProgress(session.id, payload)
        }
    }

    private suspend fun handleProjectMeta(call: ApplicationCall, session: ObfuscationSession) {
        val scope = parseProjectScope(call.parameters["scope"])
        if (scope == null) {
            respondError(call, HttpStatusCode.BadRequest, "Invalid scope")
            return
        }
        val meta = projectInspectionService.projectMeta(session, scope)
        val result = JsonObject().apply {
            addProperty("status", "ok")
            addProperty("scope", meta.scope)
            addProperty("available", meta.available)
            addProperty("classCount", meta.classCount)
        }
        call.respondText(gson.toJson(result), ContentType.Application.Json)
    }

    private suspend fun handleProjectTree(call: ApplicationCall, session: ObfuscationSession) {
        val scope = parseProjectScope(call.parameters["scope"])
        if (scope == null) {
            respondError(call, HttpStatusCode.BadRequest, "Invalid scope")
            return
        }
        val tree = runCatching {
            projectInspectionService.projectTree(session, scope)
        }.getOrElse {
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
            addProperty("scope", tree.scope)
            addProperty("classCount", tree.classCount)
            add("classes", JsonArray().apply { tree.classes.forEach(::add) })
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
            val source = projectInspectionService.projectSource(session, scope, className)
            val result = JsonObject().apply {
                addProperty("status", "ok")
                addProperty("scope", source.scope)
                addProperty("class", source.className)
                addProperty("language", source.language)
                addProperty("code", source.code)
            }
            call.respondText(gson.toJson(result), ContentType.Application.Json)
        } catch (_: NoSuchElementException) {
            respondError(call, HttpStatusCode.NotFound, "Class not found", className)
        } catch (_: IllegalStateException) {
            respondError(call, HttpStatusCode.NotFound, "No ${scope.name.lowercase()} JAR available", className)
        } catch (_: IllegalArgumentException) {
            respondError(call, HttpStatusCode.BadRequest, "Invalid class name", className)
        } catch (exception: Exception) {
            respondError(call, HttpStatusCode.InternalServerError, exception.message ?: "Failed to decompile class", className)
        }
    }

    private suspend fun handleDownload(call: ApplicationCall, session: ObfuscationSession) {
        val outputPath = session.outputJarPath
        if (outputPath == null || !File(outputPath).exists()) {
            call.respondText(gson.toJson(JsonObject().apply { addProperty("error", "No output file available") }), ContentType.Application.Json, HttpStatusCode.NotFound)
            return
        }
        val file = File(outputPath)
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, file.name).toString()
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
            add("libraryFiles", JsonArray().apply { session.getLibraryNames().forEach(::add) })
            addProperty("assetCount", session.getAssetNames().size)
            add("assetFiles", JsonArray().apply { session.getAssetNames().forEach(::add) })
            session.errorMessage?.let { addProperty("error", it) }
        }
    }

    private fun buildTaskResponse(task: PlatformTaskRecord): JsonObject {
        return JsonObject().apply {
            addProperty("status", "ok")
            add("task", buildTaskJson(task))
            add("logs", JsonArray().apply { task.logs.forEach(::add) })
        }
    }

    private fun buildTaskJson(task: PlatformTaskRecord): JsonObject {
        return JsonObject().apply {
            addProperty("id", task.id)
            addProperty("projectName", task.projectName)
            addProperty("inputObjectKey", task.inputObjectKey)
            task.configObjectKey?.let { addProperty("configObjectKey", it) }
            task.outputObjectKey?.let { addProperty("outputObjectKey", it) }
            task.sessionId?.let { addProperty("sessionId", it) }
            addProperty("createdAt", task.createdAt)
            addProperty("updatedAt", task.updatedAt)
            addProperty("status", task.status.name)
            addProperty("stage", task.currentStage)
            addProperty("progress", task.progress)
            addProperty("message", task.message)
            val session = task.sessionId?.let(sessionService::getSession)
            if (session != null) {
                add("session", buildStatusResponse(session))
                addProperty("inputClassCount", session.inputClassList?.size ?: 0)
                addProperty("outputClassCount", session.finalClassList?.size ?: 0)
            }
        }
    }

    private fun buildTaskStageJson(stage: TaskStageRecord): JsonObject {
        return JsonObject().apply {
            addProperty("name", stage.name)
            addProperty("progress", stage.progress)
            addProperty("message", stage.message)
            addProperty("timestamp", stage.timestamp)
        }
    }

    private fun buildTaskEventJson(task: PlatformTaskRecord, stage: TaskStageRecord): JsonObject {
        return JsonObject().apply {
            addProperty("type", "task.progress")
            addProperty("taskId", task.id)
            addProperty("stage", stage.name)
            addProperty("progress", stage.progress)
            addProperty("message", stage.message)
            addProperty("timestamp", stage.timestamp)
            addProperty("status", task.status.name)
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
                runCatching { kotlinx.coroutines.runBlocking { ws.send(Frame.Text(message)) } }
            }
        }
    }

    private fun broadcastProgress(sessionId: String, payload: String) {
        val targets = wsProgressSessions[sessionId] ?: return
        synchronized(targets) {
            targets.forEach { ws ->
                runCatching { kotlinx.coroutines.runBlocking { ws.send(Frame.Text(payload)) } }
            }
        }
    }
}

fun Application.grunteonWebModule() {
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

    with(WebServer) {
        routing {
            staticResources()
            apiRoutes()
            wsRoutes()
        }
    }
}
