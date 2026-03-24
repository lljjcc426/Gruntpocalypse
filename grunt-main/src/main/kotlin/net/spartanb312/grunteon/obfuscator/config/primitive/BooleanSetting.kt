package net.spartanb312.grunteon.obfuscator.config.primitive

import com.google.gson.JsonObject
import net.spartanb312.grunteon.obfuscator.config.MutableSetting
import net.spartanb312.grunteon.obfuscator.lang.MultiText

open class BooleanSetting(
    nameMulti: MultiText,
    value: Boolean,
    description: MultiText,
    visibility: (() -> Boolean) = { true }
) : MutableSetting<Boolean>(nameMulti, value, description, visibility) {
    override val displayValue: String get() = value.toString()
    override fun saveValue(jsonObject: JsonObject) = jsonObject.addProperty(nameString, value)
    override fun readValue(jsonObject: JsonObject) {
        value = jsonObject[nameString]?.asBoolean ?: value
    }
}