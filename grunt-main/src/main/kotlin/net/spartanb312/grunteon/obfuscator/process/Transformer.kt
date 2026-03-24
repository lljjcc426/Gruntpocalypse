package net.spartanb312.grunteon.obfuscator.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.Languages
import net.spartanb312.grunteon.obfuscator.lang.MultiText
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.resource.JarResources
import net.spartanb312.grunteon.obfuscator.process.resource.WorkResources
import net.spartanb312.grunteon.obfuscator.util.Logger
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

    context(instance: Grunteon, res: WorkResources, jar: JarResources)
    open fun transform(config: T) {
        if (parallel) runBlocking {
            jar.classes.asSequence()
                .filter { true } // TODO : class filter
                .forEach { clazz ->
                    MainScope.launch(Dispatchers.IO) {
                        transformClass(clazz.value, config)
                    }
                }
        } else jar.classes.asSequence()
            .filter { true } // TODO : class filter
            .forEach { clazz ->
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