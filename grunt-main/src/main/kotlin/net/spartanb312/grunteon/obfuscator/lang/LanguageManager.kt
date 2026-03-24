package net.spartanb312.grunteon.obfuscator.lang

import net.spartanb312.grunteon.obfuscator.util.Logger
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object LanguageManager {

    private val textContainers = mutableListOf<MultiText>()
    private var currentLang = Languages.English

    val flag = AtomicBoolean(false)

    fun add(text: MultiText) {
        try {
            textContainers.add(text)
            text.update(currentLang)
            if (flag.get()) throw Exception("Language manager already initialized")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun check() {
        textContainers.forEach {
            println("${it.descriptor}=${it.current}")
        }
    }

    fun updateLanguage(lang: Languages) {
        if (currentLang != lang) {
            println("Language changed to ${lang.name}")
            currentLang = lang
            textContainers.forEach { it.update(lang) }
        }
    }

    fun read(path: String, lang: Languages) {
        val file = File(path)
        if (file.exists()) {
            val lines = file.readLines()
            val firstLine = lines[0]
            val fileLang = firstLine.substringAfter("Lang=")
            if (fileLang == lang.code) {
                lines.forEach { line ->
                    val desc = line.substringBefore("=")
                    textContainers.find { it.descriptor == desc }?.addLang(lang, line.substringAfter("="))
                }
            } else Logger.error("File language code does not match. Expecting ${lang.code} but found $fileLang")
        } else Logger.error("File $path does not exist")
    }

    fun dump(path: String, lang: Languages) {
        val file = File(path)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        file.writeText("Lang=${lang.code}\n")
        textContainers.sortedBy { it.descriptor }.forEach {
            file.appendText("${it.descriptor}=${it.findOrDefault(lang)}\n")
        }
    }

}