package net.spartanb312.grunteon.obfuscator.config.primitive

import com.google.gson.JsonObject
import net.spartanb312.grunteon.obfuscator.config.MutableSetting
import net.spartanb312.grunteon.obfuscator.lang.MultiText
import net.spartanb312.grunteon.obfuscator.util.interfaces.DisplayEnum
import net.spartanb312.grunteon.obfuscator.util.last
import net.spartanb312.grunteon.obfuscator.util.next

class EnumSetting<T : Enum<T>>(
    nameMulti: MultiText,
    value: T,
    description: MultiText,
    visibility: (() -> Boolean) = { true }
) : MutableSetting<T>(nameMulti, value, description, visibility) {

    private val enumClass: Class<T> = value.declaringJavaClass
    val enumValues: Array<T> = enumClass.enumConstants
    override val displayValue: String
        get() {
            val value = value
            return if (value is DisplayEnum) value.displayString else nameString
        }

    override fun saveValue(jsonObject: JsonObject) = jsonObject.addProperty(nameString, value.name)
    override fun readValue(jsonObject: JsonObject) {
        jsonObject[nameString]?.asString?.let { setWithName(it) }
    }

    fun nextValue() {
        value = value.next()
    }

    fun lastValue() {
        value = value.last()
    }

    fun currentName(): String {
        return value.name
    }

    fun setWithName(nameIn: String) {
        enumValues.firstOrNull { it.name.equals(nameIn, true) }?.let {
            value = it
        }
    }

}