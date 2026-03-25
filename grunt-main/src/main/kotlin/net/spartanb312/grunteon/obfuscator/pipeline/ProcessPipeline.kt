package net.spartanb312.grunteon.obfuscator.pipeline

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.config.manager.ConfigGroup
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.resource.JarResources
import net.spartanb312.grunteon.obfuscator.process.resource.WorkResources
import net.spartanb312.grunteon.obfuscator.util.Logger
import java.util.concurrent.atomic.AtomicBoolean

class ProcessPipeline(
    vararg transformers: Transformer<*>
) {

    private val initialized = AtomicBoolean(false)
    private val transformers = transformers.toMutableList()
    private val transformer2Config = mutableMapOf<Transformer<*>, TransformerConfig>()

    fun parseConfig(configGroup: ConfigGroup) {
        initialized.set(true)
        transformer2Config.clear()
        transformers.forEachIndexed { index, it ->
            transformer2Config[it] = configGroup.getConfig(it, index)
        }
    }

    context(instance: Grunteon, res: WorkResources, jar: JarResources)
    fun execute() {
        if (!initialized.get()) throw Exception("Pipeline is not initialized")
        Logger.info("Obfuscating...")
        transformer2Config.forEach { (transformer, config) ->
            transformer.execute(instance, res, jar, config)
        }
    }

}