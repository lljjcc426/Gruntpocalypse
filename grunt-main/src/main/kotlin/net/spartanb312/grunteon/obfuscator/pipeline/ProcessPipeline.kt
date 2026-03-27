package net.spartanb312.grunteon.obfuscator.pipeline

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.config.manager.ConfigGroup
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.WorkerContext
import net.spartanb312.grunteon.obfuscator.util.Logger
import java.util.concurrent.atomic.AtomicBoolean

class ProcessPipeline(
    vararg transformers: Transformer<*>
) {

    private val initialized = AtomicBoolean(false)
    private val transformers = transformers.toList()
    private val transformer2Config = mutableMapOf<Transformer<*>, TransformerConfig>()

    init {
        // check orders
        Logger.info("Validating pipeline orders...")
        transformers.forEachIndexed { index, transformer ->
            transformer.orderRules.forEach {
                val valid = it.first.invoke(this.transformers, index)
                if (!valid) throw Exception("${transformer.engName} has a wrong order! Reason: ${it.second}")
            }
        }
    }

    fun parseConfig(configGroup: ConfigGroup) {
        initialized.set(true)
        transformer2Config.clear()
        transformers.forEachIndexed { index, it ->
            transformer2Config[it] = configGroup.getConfig(it, index)
        }
    }

    context(instance: Grunteon)
    fun execute() {
        if (!initialized.get()) throw Exception("Pipeline is not initialized")
        Logger.info("Obfuscating...")
        val old = false
        if (old) {
            transformer2Config.forEach { (transformer, config) ->
                transformer.execute(instance, config)
            }
        } else {
            val stages = transformer2Config.map { (transformer, config) ->
                transformer.buildStage(config)
            }
            val workerContext = WorkerContext()
            workerContext.execute(instance, stages)
        }
    }

}