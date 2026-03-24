package net.spartanb312.grunteon.obfuscator.config.manager

import net.spartanb312.grunteon.obfuscator.config.Configurable
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig

class ConfigGroup : Configurable() {

    /**
     * General configs
     */
    val input by setting(
        enText("config.input", "Input"),
        "input.jar",
        enText("config.input.desc", "The input jar that will be obfuscated")
    )
    val output by setting(
        enText("config.output", "Output"),
        "output.jar",
        enText("config.output.desc", "The output obfuscated jar")
    )
    val libs by setting(
        enText("config.libs", "Libraries"),
        listOf(""),
        enText("config.libs.desc", "Dependencies of the input jar")
    )


    /**
     * Transformer configs
     */
    val configs = mutableListOf<TransformerConfig>()

    fun getConfig(transformer: Transformer<*>, index: Int): TransformerConfig {
        val conf = configs.getOrNull(index) ?: transformer.defConfig
        if (!transformer.qualifyConfig(conf, true)) return transformer.defConfig
        return conf
    }

}