package net.spartanb312.grunteon.obfuscator.web

interface SessionExecutionGateway {

    fun startSession(
        session: ObfuscationSession,
        onFinish: (() -> Unit)? = null,
        onStart: (() -> Unit)? = null
    ): StartResult
}
