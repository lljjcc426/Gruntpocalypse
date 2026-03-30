package net.spartanb312.grunteon.obfuscator.process

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.Languages
import net.spartanb312.grunteon.obfuscator.lang.MultiText
import net.spartanb312.grunteon.obfuscator.pipeline.OrderRule
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.filters.FilterStrategy
import net.spartanb312.grunteon.obfuscator.util.filters.buildClassNamePredicates

abstract class Transformer<T : TransformerConfig>(
    val name: MultiText,
    val category: Category,
    val description: MultiText,
) {

    val engName = name.getLang(Languages.English)
    val orderRules = mutableListOf<Pair<OrderRule, String>>()
    abstract val defConfig: TransformerConfig
    abstract val confType: Class<T>
    fun qualifyConfig(config: TransformerConfig, debug: Boolean): Boolean {
        val result = config::class.java.isAssignableFrom(confType)
        if (debug && !result) Logger.error("Config type mismatch! Except ${confType::class.qualifiedName} but get ${config::class.qualifiedName}")
        return result
    }

    context(instance: Grunteon)
    val transformerSeed get() = instance.baseSeed + name.descriptor

    protected fun buildFilterStrategy(config: T): FilterStrategy {
        return FilterStrategy(
            buildClassNamePredicates(config.includeStrategy),
            buildClassNamePredicates(config.excludeStrategy)
        )
    }

    context(_: Grunteon)
    internal fun buildStageImpl(pipelineBuilder: PipelineBuilder, config: TransformerConfig) {
        context(pipelineBuilder) {
            @Suppress("UNCHECKED_CAST")
            buildStageImpl(config as T)
        }
    }

    context(instance: Grunteon, _: PipelineBuilder)
    protected abstract fun buildStageImpl(config: T)
}