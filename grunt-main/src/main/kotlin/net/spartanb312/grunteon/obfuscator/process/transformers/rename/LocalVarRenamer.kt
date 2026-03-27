package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.StageBuilder
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number.NumberBasicEncrypt
import net.spartanb312.grunteon.obfuscator.util.Counter
import net.spartanb312.grunteon.obfuscator.util.FastCounter
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.methodFullDesc
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import org.objectweb.asm.tree.ClassNode

class LocalVarRenamer : Transformer<LocalVarRenamer.Config>(
    name = enText("process.rename.local_var_renamer", "LocalVarRenamer"),
    category = Category.Renaming,
    parallel = true
) {

    override val defConfig: TransformerConfig get() = Config()
    override val confType: Class<Config> get() = Config::class.java

    init {
        after(NumberBasicEncrypt::class.java, "Renamer should run after encryptor")
        // before(ReferenceRedirect::class.java, "Renamer should run before invokedynamic")
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

    private val counter = Counter()
    private lateinit var methodExPredicate: NamePredicates

    context(instance: Grunteon)
    override fun transform(config: Config) {
        Logger.info(" - LocalVarRenamer: Transforming local variables...")
        methodExPredicate = buildMethodNamePredicates(config.exclusion)
        super.transform(config)
        Logger.info("    Transformed ${counter.get()} local variables")
    }

    context(instance: Grunteon)
    override fun transformClass(classNode: ClassNode, config: Config) {
        val dictionary = NameGenerator.getDictionary(config.dictionary)
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

    override fun StageBuilder.buildStage(config: Config) {
        seq {
            Logger.info(" - LocalVarRenamer: Transforming local variables...")
            // TODO: there is a better way to do this instead of lateinit var
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }
        val counter = reducibleScopeValue { FastCounter() }
        parForEachFiltered(config) { classNode ->
            val counter = counter.local
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
                    val dictionary = NameGenerator.getDictionary(config.dictionary)
                    method.localVariables?.forEach { it.name = "${config.prefix}${dictionary.nextName()}" }
                    counter.add(method.localVariables?.size ?: 0)
                }
        }
        seq {
            Logger.info("    Transformed ${counter.global.get()} local variables")
        }
    }
}