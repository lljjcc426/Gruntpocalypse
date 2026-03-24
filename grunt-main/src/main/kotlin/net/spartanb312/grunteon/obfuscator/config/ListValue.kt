package net.spartanb312.grunteon.obfuscator.config

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.spartanb312.grunteon.obfuscator.lang.MultiText

class ListSetting(
    nameMulti: MultiText,
    value: List<String>,
    description: MultiText,
    visibility: (() -> Boolean) = { true }
) : MutableSetting<List<String>>(nameMulti, value, description, visibility) {
    override val displayValue: String get() = value.toString()

    override fun saveValue(jsonObject: JsonObject) = jsonObject.add(nameString, JsonArray().apply {
        value.forEach { add(it) }
    })

    override fun readValue(jsonObject: JsonObject) {
        value = jsonObject[nameString]?.asJsonArray?.map { it.asString } ?: value
    }

}