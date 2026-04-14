package net.spartanb312.grunteon.obfuscator.pipeline

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.config.manager.ConfigGroup
import net.spartanb312.grunteon.obfuscator.process.PipelineBuilder
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.WorkerContext
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.MappingApplier
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.MappingSource
import net.spartanb312.grunteon.obfuscator.util.Logger
import java.util.concurrent.atomic.AtomicBoolean

class ProcessPipeline(
    vararg transformers: Transformer<*>
) {
    private val transformers: List<Transformer<*>>

    init {
        // check orders
        Logger.info("Validating pipeline orders...")
        val transformersList = transformers.toMutableList()
        var lastRenamerIndex = -1
        transformers.forEachIndexed { index, transformer ->
            if (transformer is MappingSource) lastRenamerIndex = index
            transformer.orderRules.forEach {
                val valid = it.first.invoke(transformersList, index)
                if (!valid) throw Exception("${transformer.engName} has a wrong order! Reason: ${it.second}")
            }
        }
        if (lastRenamerIndex != -1) {
            transformersList.add(lastRenamerIndex + 1, MappingApplier())
        }
        this.transformers = transformersList
    }

    private val initialized = AtomicBoolean(false)
    private val transformer2Config = mutableMapOf<Transformer<*>, TransformerConfig>()

    fun parseConfig(configGroup: ConfigGroup) {
        initialized.set(true)
        transformer2Config.clear()
        transformers.forEachIndexed { index, it ->
            transformer2Config[it] = configGroup.getConfig(it, index)
        }
    }

    context(instance: Grunteon)
    fun execute(): Map<String, Long> {
        if (!initialized.get()) throw Exception("Pipeline is not initialized")
        val timeMap = mutableMapOf<String, Long>()
        Logger.info("Obfuscating...")
        val pipelineBuilder = PipelineBuilder()
        val startTime0 = System.nanoTime()
        var startTime = startTime0
        transformer2Config.forEach { (transformer, config) ->
            transformer.buildStageImpl(pipelineBuilder, config)
            val currentTime = System.nanoTime()
            timeMap[transformer.engName] = currentTime - startTime
            startTime = currentTime
        }
        timeMap["Obfuscation"] = System.nanoTime() - startTime0
        val workerContext = WorkerContext()
        workerContext.execute(instance, pipelineBuilder)
        return timeMap
    }

}