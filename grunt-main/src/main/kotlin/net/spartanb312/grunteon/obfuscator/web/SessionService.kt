package net.spartanb312.grunteon.obfuscator.web

import com.google.gson.JsonObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SessionService(
    private val sessionRootDir: File
) {
    private val sessions = ConcurrentHashMap<String, ObfuscationSession>()

    init {
        sessionRootDir.mkdirs()
    }

    fun createSession(): ObfuscationSession {
        val sessionId = UUID.randomUUID().toString()
        return getOrCreateSession(sessionId)
    }

    fun getSession(sessionId: String): ObfuscationSession? {
        return sessions[sessionId]
    }

    fun getOrCreateSession(sessionId: String): ObfuscationSession {
        return sessions.computeIfAbsent(sessionId) { id ->
            ObfuscationSession(id, File(sessionRootDir, id))
        }
    }

    fun saveConfig(session: ObfuscationSession, jsonObject: JsonObject, fileName: String): File {
        return session.saveConfig(jsonObject, fileName)
    }

    fun saveInput(session: ObfuscationSession, file: File) {
        session.replaceInput(file)
    }

    fun saveLibraries(session: ObfuscationSession, files: List<File>) {
        session.addLibraries(files)
    }

    fun saveAssets(session: ObfuscationSession, files: List<File>) {
        session.addAssets(files)
    }
}
