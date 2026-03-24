package net.spartanb312.grunteon.obfuscator.config

import net.spartanb312.grunteon.obfuscator.lang.MultiText

abstract class MutableSetting<T : Any>(
    final override val nameMulti: MultiText,
    valueIn: T,
    override val description: MultiText,
    visibility: (() -> Boolean),
) : AbstractSetting<T>() {

    init {
        visibilities.add(visibility)
    }

    final override val nameString: String by nameMulti
    override val displayName by nameMulti
    override var aliasName = nameString
    override var defaultValue = valueIn
    override var value = valueIn
        set(value) {
            if (value != field) {
                val prev = field
                val new = value
                field = new
                valueListeners.forEach { it(prev, field) }
                listeners.forEach { it() }
            }
        }

}