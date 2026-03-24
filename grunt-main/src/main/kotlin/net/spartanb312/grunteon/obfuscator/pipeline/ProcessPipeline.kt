package net.spartanb312.grunteon.obfuscator.pipeline

import net.spartanb312.grunteon.obfuscator.config.manager.ConfigGroup
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig

class ProcessPipeline(
    vararg transformers: Transformer<*>
) {

    private val transformers = transformers.toMutableList()
    private val transformer2Config = mutableMapOf<Transformer<*>, TransformerConfig>()

    fun parseConfig(configGroup: ConfigGroup) {
        transformer2Config.clear()
        transformers.forEachIndexed { index, it ->
            transformer2Config[it] = configGroup.getConfig(it, index)
        }
    }

    fun execute() {
        transformer2Config.forEach { (transformer, config) ->
            // TODO: pipeline execution
        }
    }

}