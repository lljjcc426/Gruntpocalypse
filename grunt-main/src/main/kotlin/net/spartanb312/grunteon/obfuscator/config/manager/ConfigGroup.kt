package net.spartanb312.grunteon.obfuscator.config.manager

import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig

class ConfigGroup {

    val configs = mutableListOf<TransformerConfig>()

    fun getConfig(transformer: Transformer<*>, index: Int): TransformerConfig {
        val conf = configs.getOrNull(index) ?: transformer.defConfig
        if (!transformer.qualifyConfig(conf, true)) return transformer.defConfig
        return conf
    }

}