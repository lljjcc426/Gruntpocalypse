package net.spartanb312.grunteon.obfuscator.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.Languages
import net.spartanb312.grunteon.obfuscator.lang.MultiText
import net.spartanb312.grunteon.obfuscator.process.resource.JarResources
import net.spartanb312.grunteon.obfuscator.process.resource.WorkResources
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.extensions.isExcluded
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildClassNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAllBy
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import net.spartanb312.grunteon.obfuscator.util.filters.matchedNoneBy
import net.spartanb312.grunteon.obfuscator.util.thread.MainScope
import org.objectweb.asm.tree.ClassNode

abstract class Transformer<T : TransformerConfig>(
    val name: MultiText,
    val category: Category,
    val parallel: Boolean = false
) {

    val engName = name.getLang(Languages.English)
    abstract val defConfig: TransformerConfig
    abstract val confType: Class<T>
    fun qualifyConfig(config: TransformerConfig, debug: Boolean): Boolean {
        val result = config::class.java.isAssignableFrom(confType)
        if (debug && !result) Logger.error("Config type mismatch! Except ${confType::class.qualifiedName} but get ${config::class.qualifiedName}")
        return result
    }

    @Suppress("UNCHECKED_CAST")
    fun execute(instance: Grunteon, res: WorkResources, jar: JarResources, config: TransformerConfig) {
        context(instance, res, jar) { transform(config as T) }
    }

    protected lateinit var excludePredicate: NamePredicates
    protected lateinit var includePredicate: NamePredicates

    fun buildFilterPredicate(config: T) {
        excludePredicate = buildClassNamePredicates(config.excludeStrategy)
        includePredicate = buildClassNamePredicates(config.includeStrategy)
    }

    context(instance: Grunteon, res: WorkResources, jar: JarResources)
    open fun transform(config: T) {
        buildFilterPredicate(config)
        if (parallel) runBlocking {
            jar.classes.asSequence()
                .filter { clazz ->
                    val include = includePredicate.matchedAllBy(clazz.value.name)
                    val exclude = excludePredicate.matchedAnyBy(clazz.value.name)
                    val hardExclude = clazz.value.isExcluded
                    include && !exclude && !hardExclude
                }.forEach { clazz ->
                    launch(Dispatchers.IO) {
                        transformClass(clazz.value, config)
                    }
                }
        } else jar.classes.asSequence()
            .filter { clazz ->
                val include = includePredicate.matchedAllBy(clazz.value.name)
                val exclude = excludePredicate.matchedAnyBy(clazz.value.name)
                val hardExclude = clazz.value.isExcluded
                include && !exclude && !hardExclude
            }.forEach { clazz ->
                transformClass(clazz.value, config)
            }
    }

    context(instance: Grunteon, res: WorkResources, jar: JarResources)
    open fun transformClass(
        classNode: ClassNode,
        config: T,
    ) {
    }

}