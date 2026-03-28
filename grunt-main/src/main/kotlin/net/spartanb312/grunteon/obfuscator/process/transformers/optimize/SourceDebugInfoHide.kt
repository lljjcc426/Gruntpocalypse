package net.spartanb312.grunteon.obfuscator.process.transformers.optimize

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.config.at
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.util.Counter
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.collection.random
import net.spartanb312.grunteon.obfuscator.util.collection.toListFast
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.interfaces.DisplayEnum
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LineNumberNode

class SourceDebugInfoHide : Transformer<SourceDebugInfoHide.Config>(
    name = enText("process.optimize.source_debug_info_hide", "SourceDebugInfoHide"),
    category = Category.Optimization,
    parallel = true
) {

    override val defConfig: TransformerConfig get() = Config()
    override val confType: Class<Config> get() = Config::class.java

    init {
        before(Category.Encryption, "Optimizer should run before encryption category")
        before(Category.Controlflow, "Optimizer should run before controlflow category")
        before(Category.AntiDebug, "Optimizer should run before anti debug category")
        before(Category.Authentication, "Optimizer should run before authentication category")
        before(Category.Exploit, "Optimizer should run before exploit category")
        before(Category.Miscellaneous, "Optimizer should run before miscellaneous category")
        before(Category.Redirect, "Optimizer should run before redirect category")
        before(Category.Renaming, "Optimizer should run before renaming category")
    }

    class Config : TransformerConfig() {
        val sourceFiles by setting(
            name = enText("process.optimize.source_debug_info_hide.source_files", "Remove/Edit source files"),
            value = SourceFileAction.Remove,
            desc = enText(
                "process.optimize.source_debug_info_hide.source_files.desc",
                "Remove or edit source file information"
            ),
        )
        val lineNumbers by setting(
            name = enText("process.optimize.source_debug_info_hide.line_numbers", "Remove line numbers"),
            value = true,
            desc = enText(
                "process.optimize.source_debug_info_hide.line_numbers.desc",
                "Remove line numbers"
            ),
        )
        val sourceNames by setting(
            name = enText("process.optimize.source_debug_info_hide.source_names", "Source file names"),
            value = listOf(
                ""
            ),
            desc = enText(
                "process.optimize.source_debug_info_hide.source_names.desc",
                "Customize source names"
            )
        ).at { sourceFiles == SourceFileAction.Replace }
    }

    enum class SourceFileAction(override val displayName: CharSequence) : DisplayEnum {
        Off("No operation"),
        Remove("Remove"),
        Replace("Replace"),
    }

    private val counter = Counter()

    context(instance: Grunteon)
    override fun transform(config: Config) {
        Logger.info(" - SourceDebugInfoHide: Removing/Editing debug information...")
        super.transform(config)
        Logger.info("    Removed/Edited ${counter.get()} debug information")
    }

    context(instance: Grunteon)
    override fun transformClass(classNode: ClassNode, config: Config) {
        val randomGen = Xoshiro256PPRandom(getSeed(classNode.name))
        if (config.sourceFiles != SourceFileAction.Off) {
            if (config.sourceFiles == SourceFileAction.Replace) {
                classNode.sourceDebug = config.sourceNames.random(randomGen)
                classNode.sourceFile = config.sourceNames.random(randomGen)
            } else {
                classNode.sourceDebug = null
                classNode.sourceFile = null
            }
            counter.add()
        }
        if (config.lineNumbers) classNode.methods.forEach { methodNode ->
            methodNode.instructions.toListFast().forEach {
                if (it is LineNumberNode) {
                    methodNode.instructions.remove(it)
                    counter.add()
                }
            }
        }
    }

}