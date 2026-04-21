package net.spartanb312.grunteon.obfuscator.web

interface SessionMetadataQuery {
    fun loadSessions(): List<PersistedSessionState>

    fun findSession(sessionId: String): PersistedSessionState?
}

object NoOpSessionMetadataQuery : SessionMetadataQuery {
    override fun loadSessions(): List<PersistedSessionState> = emptyList()

    override fun findSession(sessionId: String): PersistedSessionState? = null
}
