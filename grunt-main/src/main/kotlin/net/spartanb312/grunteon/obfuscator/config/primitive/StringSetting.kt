package net.spartanb312.grunteon.obfuscator.config.primitive

import com.google.gson.JsonObject
import net.spartanb312.grunteon.obfuscator.config.MutableSetting
import net.spartanb312.grunteon.obfuscator.lang.MultiText

class StringSetting(
    nameMulti: MultiText,
    value: String,
    description: MultiText,
    visibility: (() -> Boolean) = { true }
) : MutableSetting<String>(nameMulti, value, description, visibility) {
    override val displayValue get(): String = value
    override fun saveValue(jsonObject: JsonObject) = jsonObject.addProperty(nameString, value)
    override fun readValue(jsonObject: JsonObject) {
        value = jsonObject[nameString]?.asString ?: value
    }
}