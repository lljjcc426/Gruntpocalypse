package net.spartanb312.grunteon.obfuscator.web

import net.spartanb312.grunteon.obfuscator.util.logging.ILogger
import java.text.SimpleDateFormat
import java.util.*

class SessionLogger(
    private val session: ObfuscationSession,
    private val delegate: ILogger? = null
) : ILogger {

    override fun trace(msg: String) = raw(msg, "TRACE")

    override fun debug(msg: String) = raw(msg, "DEBUG")

    override fun info(msg: String) = raw(msg, "INFO")

    override fun warn(msg: String) = raw(msg, "WARN")

    override fun error(msg: String) = raw(msg, "ERROR")

    override fun fatal(msg: String) = raw(msg, "FATAL")

    override fun raw(msg: String, level: String) {
        val text = "[${SimpleDateFormat("MM-dd HH:mm:ss").format(Date())}][${Thread.currentThread().name}/$level][Grunteon] $msg"
        session.log(text)
        delegate?.raw(msg, level)
    }
}
