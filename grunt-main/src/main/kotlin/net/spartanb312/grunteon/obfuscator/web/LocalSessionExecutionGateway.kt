package net.spartanb312.grunteon.obfuscator.web

class LocalSessionExecutionGateway(
    private val obfuscationService: ObfuscationService
) : SessionExecutionGateway {

    override fun startSession(
        session: ObfuscationSession,
        onFinish: (() -> Unit)?,
        onStart: (() -> Unit)?
    ): StartResult {
        return obfuscationService.start(
            session,
            WebBridgeSupport.buildExecutionConfig(session),
            onStart = onStart,
            onFinish = onFinish
        )
    }
}
