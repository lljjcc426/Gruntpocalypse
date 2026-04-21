package net.spartanb312.grunteon.obfuscator.web

import com.google.gson.JsonObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SessionService(
    private val sessionRootDir: File,
    private val sessionMetadataStore: SessionMetadataStore = NoOpSessionMetadataStore,
    private val artifactMetadataStore: ArtifactMetadataStore = NoOpArtifactMetadataStore,
    private val sessionMetadataQuery: SessionMetadataQuery = NoOpSessionMetadataQuery
) {
    private val sessions = ConcurrentHashMap<String, ObfuscationSession>()

    init {
        sessionRootDir.mkdirs()
    }

    fun createSession(
        accessProfile: SessionAccessProfile = SessionAccessProfile.SECURE,
        controlPlane: String = "embedded-web",
        workerPlane: String = "local-worker"
    ): ObfuscationSession {
        val sessionId = UUID.randomUUID().toString()
        return getOrCreateSession(sessionId).also {
            it.configureControlPlane(accessProfile, controlPlane, workerPlane)
            sessionMetadataStore.saveSession(it)
        }
    }

    fun getSession(sessionId: String): ObfuscationSession? {
        return sessions[sessionId]
    }

    fun getOrCreateSession(sessionId: String): ObfuscationSession {
        return sessions.computeIfAbsent(sessionId) { id ->
            managedSession(id, saveInitial = true)
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

    fun linkConfigArtifact(session: ObfuscationSession, objectKey: String?) {
        session.bindConfigObjectKey(objectKey)
        bindArtifact(session, objectKey, "CONFIG")
        sessionMetadataStore.saveSession(session)
    }

    fun linkInputArtifact(session: ObfuscationSession, objectKey: String?) {
        session.bindInputObjectKey(objectKey)
        bindArtifact(session, objectKey, "INPUT")
        sessionMetadataStore.saveSession(session)
    }

    fun linkLibraryArtifact(session: ObfuscationSession, fileName: String, objectKey: String?) {
        if (!objectKey.isNullOrBlank()) {
            session.putLibraryObjectRef(fileName, objectKey)
            bindArtifact(session, objectKey, "LIBRARY")
            sessionMetadataStore.saveSession(session)
        }
    }

    fun linkAssetArtifact(session: ObfuscationSession, fileName: String, objectKey: String?) {
        if (!objectKey.isNullOrBlank()) {
            session.putAssetObjectRef(fileName, objectKey)
            bindArtifact(session, objectKey, "ASSET")
            sessionMetadataStore.saveSession(session)
        }
    }

    fun linkOutputArtifact(session: ObfuscationSession, objectKey: String?) {
        session.bindOutputObjectKey(objectKey)
        bindArtifact(session, objectKey, "OUTPUT")
        sessionMetadataStore.saveSession(session)
    }

    fun preloadPersistedSessions() {
        sessionMetadataQuery.loadSessions().forEach(::restoreSession)
    }

    fun listSessionIds(): List<String> {
        return sessions.keys().toList()
    }

    private fun bindArtifact(session: ObfuscationSession, objectKey: String?, ownerRole: String) {
        if (objectKey.isNullOrBlank()) return
        artifactMetadataStore.bindArtifact(
            objectKey = objectKey,
            ownerType = "SESSION",
            ownerId = session.id,
            ownerRole = ownerRole
        )
    }

    private fun managedSession(sessionId: String, saveInitial: Boolean): ObfuscationSession {
        return ObfuscationSession(sessionId, File(sessionRootDir, sessionId)).also { session ->
            session.onStateChanged = { changed -> sessionMetadataStore.saveSession(changed) }
            if (saveInitial) {
                sessionMetadataStore.saveSession(session)
            }
        }
    }

    private fun restoreSession(state: PersistedSessionState): ObfuscationSession {
        return sessions.computeIfAbsent(state.sessionId) { id ->
            managedSession(id, saveInitial = false).also { session ->
                session.restorePersistedState(state)
            }
        }
    }
}
