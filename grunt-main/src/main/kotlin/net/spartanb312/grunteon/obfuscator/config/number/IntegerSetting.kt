package net.spartanb312.grunteon.obfuscator.config.number

import com.google.gson.JsonObject
import net.spartanb312.grunteon.obfuscator.lang.MultiText

class IntegerSetting(
    nameMulti: MultiText,
    value: Int,
    range: IntRange,
    step: Int,
    description: MultiText,
    visibility: (() -> Boolean) = { true }
) : NumberSetting<Int>(nameMulti, value, range, step, description, visibility) {

    override val displayValue: String get() = format.format(value)

    override fun saveValue(jsonObject: JsonObject) = jsonObject.addProperty(nameString, value)
    override fun readValue(jsonObject: JsonObject) {
        value = (jsonObject[nameString]?.asInt ?: value).coerceIn(range)
    }

    override val width = range.last - range.first

    override fun parseString(string: String) {
        try {
            value = string.toInt().coerceIn(range)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun setByPercent(percent: Float) {
        value = (range.start + ((range.endInclusive - range.start) * percent / step).toInt() * step)
            .coerceIn(range)
    }

    override fun getDisplay(percent: Float): String {
        return if (percent == 1f) value.toString()
        else (range.start + ((range.endInclusive - range.start) * percent / step).toInt() * step).toString()
    }

    override fun getPercentBar(): Float {
        return (value - range.start) / (range.endInclusive - range.start).toFloat()
    }

    override fun increaseStep(): Boolean {
        val targetValue = value + step
        if (targetValue in range) {
            value = targetValue
            return true
        } else {
            value = range.endInclusive
            return false
        }
    }

    override fun decreaseStep(): Boolean {
        val targetValue = value - step
        if (targetValue in range) {
            value = targetValue
            return true
        } else {
            value = range.start
            return false
        }
    }

    override val defaultPercentBar: Float
        get() = (defaultValue - range.start) / (range.endInclusive - range.start).toFloat()

}