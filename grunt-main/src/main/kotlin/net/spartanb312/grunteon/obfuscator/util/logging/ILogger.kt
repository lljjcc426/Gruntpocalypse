package net.spartanb312.grunteon.obfuscator.util.logging

interface ILogger {

    fun trace(msg: String) {
    }

    fun debug(msg: String) {
    }

    fun info(msg: String) {
    }

    fun warn(msg: String) {
    }

    fun error(msg: String) {
    }

    fun fatal(msg: String) {
    }

    fun raw(msg: String, level: String) {
    }

    class NoOp : ILogger
}