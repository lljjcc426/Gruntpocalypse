package net.spartanb312.grunteon.obfuscator.lang

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// Desc: type.subtype.name.subname
class MultiText(val descriptor: String) : ReadOnlyProperty<Any?, String> {

    private val map = Object2ObjectOpenHashMap<Languages, String>().apply { this[Languages.Descriptor] = descriptor }
    private var currentLang = Languages.Descriptor
    var current = map[currentLang] ?: descriptor; private set

    fun addLang(language: Languages, text: String): MultiText {
        map[language] = text
        if (currentLang == language) current = text
        return this
    }

    fun getLang(language: Languages) = map[language]!!

    fun update(language: Languages): MultiText {
        if (currentLang != language) {
            current = map[language] ?: descriptor
            currentLang = language
        }
        return this
    }

    fun findOrDefault(lang: Languages) = map[lang] ?: descriptor

    override fun getValue(thisRef: Any?, property: KProperty<*>): String {
        return current
    }

    fun reg(): MultiText = apply { LanguageManager.add(this) }

}