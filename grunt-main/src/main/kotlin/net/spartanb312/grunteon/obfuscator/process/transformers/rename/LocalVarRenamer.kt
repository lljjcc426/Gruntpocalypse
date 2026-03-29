package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number.NumberBasicEncrypt
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.methodFullDesc
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy

class LocalVarRenamer : Transformer<LocalVarRenamer.Config>(
    name = enText("process.rename.local_var_renamer", "LocalVarRenamer"),
    category = Category.Renaming,
    parallel = true
) {

    override val defConfig: TransformerConfig get() = Config()
    override val confType: Class<Config> get() = Config::class.java

    init {
        after(Category.Encryption, "Renamer should run after encryption category")
        after(Category.Controlflow, "Renamer should run after controlflow category")
        after(Category.AntiDebug, "Renamer should run after anti debug category")
        after(Category.Authentication, "Renamer should run after authentication category")
        after(Category.Exploit, "Renamer should run after exploit category")
        after(Category.Miscellaneous, "Renamer should run after miscellaneous category")
        after(Category.Optimization, "Renamer should run after optimization category")
        after(Category.Redirect, "Renamer should run after redirect category")
    }

    class Config : TransformerConfig() {
        val dictionary by setting(
            name = enText("process.rename.local_var_renamer.dictionary", "Dictionary"),
            value = NameGenerator.DictionaryType.Alphabet,
            desc = enText("process.rename.local_var_renamer.dictionary.desc", "Dictionary for renamer")
        )
        val prefix by setting(
            name = enText("process.rename.local_var_renamer.prefix", "Prefix"),
            value = "\u202E",
            desc = enText("process.rename.local_var_renamer.prefix.desc", "Prefix for new name")
        )
        val deleteASMInfo by setting(
            name = enText("process.rename.local_var_renamer.delete_names", "Delete names"),
            value = false,
            desc = enText(
                "process.rename.local_var_renamer.delete_names.desc",
                "Delete local vars and parameters info"
            ),
        )
        val exclusion by setting(
            enText("process.rename.local_var_renamer.method_exclusion", "Method exclusion"),
            listOf(
                "net/dummy/**", // Exclude package
                "net/dummy/Class", // Exclude class
                "net/dummy/Class.method", // Exclude method name
                "net/dummy/Class.method()V", // Exclude method with desc
            ),
            enText("process.rename.local_var_renamer.method_exclusion.desc", "Specify method exclusions."),
        )
    }

    private lateinit var methodExPredicate: NamePredicates

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            Logger.info(" - LocalVarRenamer: Transforming local variables...")
            // TODO: there is a better way to do this instead of lateinit var
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        val dictionary = globalScopeValue { NameGenerator.getDictionary(config.dictionary) }
        parForEachFiltered(buildFilterStrategy(config)) { classNode ->
            val counter = counter.local
            val dictionary = dictionary.global
            classNode.methods.asSequence()
                .filter { !it.isAbstract && !it.isNative }
                .forEach { method ->
                    val excluded = methodExPredicate.matchedAnyBy(methodFullDesc(classNode, method))
                    // if (excluded) println("Excluded method: ${methodFullDesc(classNode, method)}")
                    if (excluded) return@forEach
                    if (config.deleteASMInfo) {
                        val locals = method.localVariables?.size ?: 0
                        val params = method.parameters?.size ?: 0
                        method.parameters?.clear()
                        method.localVariables?.clear()
                        counter.add(locals + params)
                        return@forEach
                    }
                    val nameGenerator = NameGenerator(dictionary)
                    method.localVariables?.forEach { it.name = "${config.prefix}${nameGenerator.nextName()}" }
                    counter.add(method.localVariables?.size ?: 0)
                }
        }
        post {
            Logger.info(" - LocalVarRenamer:")
            Logger.info("    Transformed ${counter.global.get()} local variables")
        }
    }
}