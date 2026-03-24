package net.spartanb312.grunteon.obfuscator.config

import com.google.gson.JsonObject

open class Configurable : IConfigurable<Configurable> {
    override val settings = mutableListOf<AbstractSetting<*>>()
    override fun <S : AbstractSetting<*>> Configurable.setting(setting: S): S {
        settings.add(setting)
        return setting
    }
}

interface IConfigurable<T> : SettingRegister<T> {
    val settings: MutableList<AbstractSetting<*>>
    fun saveValue(): JsonObject {
        return JsonObject().apply {
            settings.forEach { it.saveValue(this) }
        }
    }

    fun readValue(jsonObject: JsonObject) {
        settings.forEach { it.readValue(jsonObject) }
    }
}