package net.spartanb312.grunteon.obfuscator.config

import com.google.gson.JsonObject
import net.spartanb312.grunteon.obfuscator.lang.MultiText
import net.spartanb312.grunteon.obfuscator.util.interfaces.AliasNameable
import net.spartanb312.grunteon.obfuscator.util.interfaces.Nameable
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class AbstractSetting<T> : Nameable, AliasNameable, ReadWriteProperty<Any?, T> {

    abstract override val nameString: String
    abstract var defaultValue: T
    abstract var value: T
    abstract val nameMulti: MultiText
    abstract val description: MultiText
    abstract val displayName: String
    abstract val displayValue: String

    val visibilities = mutableListOf<() -> Boolean>()
    val isVisible get() = visibilities.all { it.invoke() }

    val isModified get() = this.value != this.defaultValue

    val listeners = ArrayList<() -> Unit>()
    val valueListeners = ArrayList<(prev: T, input: T) -> Unit>()

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value
    }

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (this is MutableSetting) this.value = value
    }

    abstract fun saveValue(jsonObject: JsonObject)
    abstract fun readValue(jsonObject: JsonObject)

    open fun reset() {
        value = defaultValue
    }

    infix fun listen(listener: () -> Unit): AbstractSetting<T> {
        this.listeners.add(listener)
        return this
    }

    fun valueListen(listener: (prev: T, input: T) -> Unit): AbstractSetting<T> {
        this.valueListeners.add(listener)
        return this
    }

    override fun toString() = value.toString()

    override fun equals(other: Any?) = this === other
            || (other is AbstractSetting<*>
            && this.nameString == other.nameString
            && this.value == other.value)

    override fun hashCode() = nameString.hashCode() * 31 + value.hashCode()

}