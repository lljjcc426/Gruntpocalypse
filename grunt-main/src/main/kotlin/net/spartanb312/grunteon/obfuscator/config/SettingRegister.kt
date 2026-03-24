package net.spartanb312.grunteon.obfuscator.config

import net.spartanb312.grunteon.obfuscator.config.number.DoubleSetting
import net.spartanb312.grunteon.obfuscator.config.number.FloatSetting
import net.spartanb312.grunteon.obfuscator.config.number.IntegerSetting
import net.spartanb312.grunteon.obfuscator.config.number.LongSetting
import net.spartanb312.grunteon.obfuscator.config.primitive.BooleanSetting
import net.spartanb312.grunteon.obfuscator.config.primitive.EnumSetting
import net.spartanb312.grunteon.obfuscator.config.primitive.StringSetting
import net.spartanb312.grunteon.obfuscator.lang.MultiText

interface SettingRegister<T> {

    fun T.setting(
        nameMulti: MultiText,
        value: Double,
        range: ClosedFloatingPointRange<Double> = Double.MIN_VALUE..Double.MAX_VALUE,
        step: Double = 0.1,
        description: MultiText,
        visibility: () -> Boolean = { true },
    ) = setting(DoubleSetting(nameMulti, value, range, step, description, visibility))

    fun T.setting(
        nameMulti: MultiText,
        value: Float,
        range: ClosedFloatingPointRange<Float> = Float.MIN_VALUE..Float.MAX_VALUE,
        step: Float = 0.1F,
        description: MultiText,
        visibility: () -> Boolean = { true },
    ) = setting(FloatSetting(nameMulti, value, range, step, description, visibility))

    fun T.setting(
        nameMulti: MultiText,
        value: Int,
        range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
        step: Int = 1,
        description: MultiText,
        visibility: () -> Boolean = { true },
    ) = setting(IntegerSetting(nameMulti, value, range, step, description, visibility))

    fun T.setting(
        nameMulti: MultiText,
        value: Long,
        range: LongRange = Long.MIN_VALUE..Long.MAX_VALUE,
        step: Long = 1L,
        description: MultiText,
        visibility: () -> Boolean = { true },
    ) = setting(LongSetting(nameMulti, value, range, step, description, visibility))

    fun T.setting(
        nameMulti: MultiText,
        value: Boolean,
        description: MultiText,
        visibility: () -> Boolean = { true },
    ) = setting(BooleanSetting(nameMulti, value, description, visibility))

    fun <E : Enum<E>> T.setting(
        nameMulti: MultiText,
        value: E,
        description: MultiText,
        visibility: () -> Boolean = { true },
    ) = setting(EnumSetting(nameMulti, value, description, visibility))

    fun T.setting(
        nameMulti: MultiText,
        value: String,
        description: MultiText,
        visibility: () -> Boolean = { true },
    ) = setting(StringSetting(nameMulti, value, description, visibility))

    fun T.setting(
        nameMulti: MultiText,
        value: List<String>,
        description: MultiText,
        visibility: () -> Boolean = { true },
    ) = setting(ListSetting(nameMulti, value, description, visibility))

    fun <S : AbstractSetting<*>> T.setting(setting: S): S

}

fun <T> AbstractSetting<T>.invisible(): AbstractSetting<T> = this.apply {
    visibilities.clear()
    visibilities.add { false }
}

inline infix fun <T> AbstractSetting<T>.at(crossinline block: (T) -> Boolean): AbstractSetting<T> = this.apply {
    visibilities.add { block(this.value) }
}

fun <T> AbstractSetting<T>.atValue(value: T): AbstractSetting<T> = this.apply {
    visibilities.add { this.value == value }
}

fun <T> AbstractSetting<T>.whenTrue(setting: AbstractSetting<Boolean>): AbstractSetting<T> = this.apply {
    visibilities.add { setting.value }
}

fun <T> AbstractSetting<T>.whenFalse(setting: AbstractSetting<Boolean>): AbstractSetting<T> = this.apply {
    visibilities.add { !setting.value }
}

fun <U, T : Enum<T>> AbstractSetting<U>.atMode(setting: AbstractSetting<T>, value: Enum<T>) = this.apply {
    visibilities.add { setting.value == value }
}

fun <U, T : Enum<T>> AbstractSetting<U>.allowedModes(setting: AbstractSetting<T>, vararg modes: Enum<T>) = this.apply {
    visibilities.add { modes.any { it == setting.value } }
}

fun <T : Comparable<T>> AbstractSetting<T>.inRange(range: ClosedRange<T>) = this.apply {
    visibilities.add { this.value in range }
}