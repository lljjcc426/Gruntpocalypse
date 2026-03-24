package net.spartanb312.grunteon.obfuscator.config.number

import net.spartanb312.grunteon.obfuscator.config.MutableSetting
import net.spartanb312.grunteon.obfuscator.lang.MultiText
import java.text.DecimalFormat

abstract class NumberSetting<T>(
    nameMulti: MultiText,
    value: T,
    val range: ClosedRange<T>,
    val step: T,
    description: MultiText,
    visibility: (() -> Boolean) = { true }
) : MutableSetting<T>(nameMulti, value, description, visibility)
        where T : Number, T : Comparable<T> {

    abstract val width: T
    open var format = DecimalFormat("0")

    abstract fun parseString(string: String)
    abstract fun getDisplay(percent: Float = 1.0f): String
    abstract fun getPercentBar(): Float
    abstract fun setByPercent(percent: Float)
    abstract fun increaseStep(): Boolean
    abstract fun decreaseStep(): Boolean

    abstract val defaultPercentBar: Float

    fun getMin(): Double {
        return range.start.toDouble()
    }

    fun getMax(): Double {
        return range.endInclusive.toDouble()
    }

}

fun <T> NumberSetting<T>.format(format: String): NumberSetting<T> where T : Number, T : Comparable<T> {
    this.format = DecimalFormat(format)
    return this
}