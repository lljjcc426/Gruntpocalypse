package net.spartanb312.grunteon.obfuscator.config.number

import com.google.gson.JsonObject
import net.spartanb312.grunteon.obfuscator.lang.MultiText
import java.text.DecimalFormat
import kotlin.math.roundToInt

class FloatSetting(
    nameMulti: MultiText,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    description: MultiText,
    visibility: (() -> Boolean) = { true }
) : NumberSetting<Float>(nameMulti, value, range, step, description, visibility) {

    override var format = DecimalFormat("0.00")
    override val displayValue: String get() = format.format(value)

    override fun saveValue(jsonObject: JsonObject) = jsonObject.addProperty(nameString, value)
    override fun readValue(jsonObject: JsonObject) {
        value = (jsonObject[nameString]?.asFloat ?: value).coerceIn(range)
    }

    override val width = range.endInclusive - range.start

    override fun parseString(string: String) {
        try {
            value = string.toFloat().coerceIn(range)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun setByPercent(percent: Float) {
        value = (range.start + ((range.endInclusive - range.start) * percent / step).roundToInt() * step)
            .coerceIn(range)
    }

    override fun getDisplay(percent: Float): String {
        return if (percent == 1f) format.format(value)
        else format.format(range.start + ((range.endInclusive - range.start) * percent / step).roundToInt() * step)
    }

    override fun getPercentBar(): Float {
        return ((value - range.start) / (range.endInclusive - range.start))
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
        get() = (defaultValue - range.start) / (range.endInclusive - range.start)

}