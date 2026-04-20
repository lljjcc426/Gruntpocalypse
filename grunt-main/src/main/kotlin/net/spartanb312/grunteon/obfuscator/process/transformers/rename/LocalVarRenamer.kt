package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import kotlinx.serialization.Serializable

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.methodFullDesc
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Last update on 2026/03/31 by FluixCarvin
 */
class LocalVarRenamer : Transformer<LocalVarRenamer.Config>(
    name = enText("process.rename.local_var_renamer", "LocalVarRenamer"),
    category = Category.Renaming,
    description = enText(
        "process.rename.local_var_renamer.desc",
        "Renaming local variables"
    )
) {

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

    @Serializable
    data class Config(
        @SettingDesc(enText = "Specify class include/exclude rules")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc(enText = "Dictionary for renamer")
        val dictionary: NameGenerator.DictionaryType = NameGenerator.DictionaryType.Alphabet,
        @SettingDesc(enText = "Rename this reference")
        val renameThisReference: Boolean = false,
        @SettingDesc(enText = "Prefix for new name")
        val prefix: String = "\u202E",
        @SettingDesc(enText = "Delete local vars and parameters info (legacy compatibility)")
        val deleteASMInfo: Boolean = false,
        @SettingDesc(enText = "Delete local variable debug entries")
        val deleteLocalVars: Boolean = false,
        @SettingDesc(enText = "Delete parameter metadata")
        val deleteParameters: Boolean = false,
        @SettingDesc(enText = "Specify method exclusions.")
        val exclusion: List<String> = listOf(
            "net/dummy/**",
            "net/dummy/Class",
            "net/dummy/Class.method",
            "net/dummy/Class.method()V"
        )
    ) : TransformerConfig

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        val methodExPredicate = buildMethodNamePredicates(config.exclusion)
        pre {
            //Logger.info(" > LocalVarRenamer: Transforming local variables...")
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        val dictionary = globalScopeValue { NameGenerator.getDictionary(config.dictionary) }
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            val counter = counter.local
            val dictionary = dictionary.global
            classNode.methods.asSequence()
                .filter { !it.isAbstract && !it.isNative }
                .forEach { method ->
                    val excluded = methodExPredicate.matchedAnyBy(methodFullDesc(classNode, method))
                    if (excluded) return@forEach
                    val deleteLocalVars = config.deleteASMInfo || config.deleteLocalVars
                    val deleteParameters = config.deleteASMInfo || config.deleteParameters
                    if (deleteLocalVars || deleteParameters) {
                        val removed = removeDebugInfo(method, deleteLocalVars, deleteParameters)
                        counter.add(removed)
                        return@forEach
                    }
                    val nameGenerator = NameGenerator(dictionary, instance.obfConfig.dictionaryStartIndex)
                    method.localVariables?.forEach { local ->
                        if (!config.renameThisReference && isThisReference(method, local.index, local.name)) return@forEach
                        local.name = "${config.prefix}${nameGenerator.nextName()}"
                        counter.add()
                    }
                }
        }
        post {
            Logger.info(" - LocalVarRenamer:")
            Logger.info("    Transformed ${counter.global.get()} local variables")
        }
    }

    private fun removeDebugInfo(method: org.objectweb.asm.tree.MethodNode, deleteLocalVars: Boolean, deleteParameters: Boolean): Int {
        var removed = 0
        if (deleteParameters) {
            removed += method.parameters?.size ?: 0
            method.parameters?.clear()
        }
        if (deleteLocalVars) {
            val localVars = method.localVariables
            if (localVars != null) {
                val parameterSlots = parameterStartSlots(method)
                val before = localVars.size
                localVars.removeIf { local -> local.index !in parameterSlots }
                removed += before - localVars.size
            }
        }
        if (deleteParameters) {
            val localVars = method.localVariables
            if (localVars != null) {
                val parameterSlots = parameterStartSlots(method)
                val before = localVars.size
                localVars.removeIf { local -> local.index in parameterSlots }
                removed += before - localVars.size
            }
        }
        return removed
    }

    private fun parameterStartSlots(method: org.objectweb.asm.tree.MethodNode): Set<Int> {
        val slots = linkedSetOf<Int>()
        var index = if ((method.access and Opcodes.ACC_STATIC) == 0) 0 else 0
        if ((method.access and Opcodes.ACC_STATIC) == 0) {
            slots += 0
            index = 1
        }
        Type.getArgumentTypes(method.desc).forEach { type ->
            slots += index
            index += type.size
        }
        return slots
    }

    private fun isThisReference(method: org.objectweb.asm.tree.MethodNode, index: Int, name: String?): Boolean {
        return (method.access and Opcodes.ACC_STATIC) == 0 && index == 0 && name == "this"
    }
}
