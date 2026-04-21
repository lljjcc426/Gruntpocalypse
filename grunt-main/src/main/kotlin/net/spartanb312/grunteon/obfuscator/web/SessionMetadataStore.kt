package net.spartanb312.grunteon.obfuscator.web

interface SessionMetadataStore {
    fun saveSession(session: ObfuscationSession)
}

object NoOpSessionMetadataStore : SessionMetadataStore {
    override fun saveSession(session: ObfuscationSession) {
    }
}
