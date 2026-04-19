package net.spartanb312.grunteon.obfuscator.web

import net.spartanb312.grunteon.obfuscator.ObfConfig
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class ObfuscationService {
    private val runningSessionId = AtomicReference<String?>(null)

    fun runningSessionId(): String? = runningSessionId.get()

    fun start(
        session: ObfuscationSession,
        config: ObfConfig,
        onStart: (() -> Unit)? = null,
        onFinish: (() -> Unit)? = null
    ): StartResult {
        if (!session.hasUploadedConfig()) {
            return StartResult.MissingConfig
        }
        if (!session.hasUploadedInput()) {
            return StartResult.MissingInput
        }
        if (!runningSessionId.compareAndSet(null, session.id)) {
            return StartResult.Busy
        }

        onStart?.invoke()
        thread(name = "obfuscation-${session.id}", priority = Thread.MAX_PRIORITY) {
            try {
                session.runObfuscation(config)
            } finally {
                runningSessionId.compareAndSet(session.id, null)
                onFinish?.invoke()
            }
        }
        return StartResult.Started
    }
}

enum class StartResult {
    Started,
    Busy,
    MissingConfig,
    MissingInput
}
