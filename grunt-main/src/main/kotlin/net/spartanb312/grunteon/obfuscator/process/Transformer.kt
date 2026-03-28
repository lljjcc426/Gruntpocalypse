package net.spartanb312.grunteon.obfuscator.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.Languages
import net.spartanb312.grunteon.obfuscator.lang.MultiText
import net.spartanb312.grunteon.obfuscator.pipeline.OrderRule
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.extensions.isExcluded
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildClassNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAllBy
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import org.objectweb.asm.tree.ClassNode

abstract class Transformer<T : TransformerConfig>(
    val name: MultiText,
    val category: Category,
    val parallel: Boolean = false
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

    @Suppress("UNCHECKED_CAST")
    fun execute(instance: Grunteon, config: TransformerConfig) {
        context(instance) { transform(config as T) }
    }

    protected lateinit var excludePredicate: NamePredicates
    protected lateinit var includePredicate: NamePredicates

    fun buildFilterPredicate(config: T) {
        excludePredicate = buildClassNamePredicates(config.excludeStrategy)
        includePredicate = buildClassNamePredicates(config.includeStrategy)
    }

    context(instance: Grunteon)
    fun filterClass(clazz: ClassNode): Boolean {
        val include = includePredicate.matchedAllBy(clazz.name)
        val exclude = excludePredicate.matchedAnyBy(clazz.name)
        val hardExclude = clazz.isExcluded
        return include && !exclude && !hardExclude
    }

    protected inline fun PipelineBuilder.parForEachFiltered(
        config: T,
        crossinline action: context(Grunteon, ScopeValueAccess) (ClassNode) -> Unit
    ) {
        buildFilterPredicate(config)
        parForEach {
            if (filterClass(it)) action(it)
        }
    }

    context(instance: Grunteon)
    open fun transform(config: T) {
        buildFilterPredicate(config)
        runBlocking {
            instance.workRes.inputClassCollection.asSequence()
                .filter { clazz ->
                    val include = includePredicate.matchedAllBy(clazz.name)
                    val exclude = excludePredicate.matchedAnyBy(clazz.name)
                    val hardExclude = clazz.isExcluded
                    include && !exclude && !hardExclude
                }.forEach { clazz ->
                    if (parallel) launch(Dispatchers.IO) { transformClass(clazz, config) }
                    else transformClass(clazz, config)
                }
        }
    }

    context(instance: Grunteon)
    open fun transformClass(
        classNode: ClassNode,
        config: T,
    ) {
    }

    context(instance: Grunteon)
    internal fun buildStageImpl(pipelineBuilder: PipelineBuilder, config: TransformerConfig) {
        @Suppress("UNCHECKED_CAST")
        pipelineBuilder.buildStageImpl(config as T)
    }

    context(instance: Grunteon)
    protected abstract fun PipelineBuilder.buildStageImpl(config: T)
}