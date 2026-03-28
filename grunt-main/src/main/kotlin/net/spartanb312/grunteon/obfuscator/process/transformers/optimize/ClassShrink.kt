package net.spartanb312.grunteon.obfuscator.process.transformers.optimize

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Counter
import net.spartanb312.grunteon.obfuscator.util.FastCounter
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.collection.toListFast
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*

class ClassShrink : Transformer<ClassShrink.Config>(
    name = enText("process.optimize.class_shrink", "ClassShrink"),
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
        val innerClasses by setting(
            name = enText("process.optimize.class_shrink.remove_inner_classes", "Inner class remove"),
            value = true,
            desc = enText(
                "process.optimize.class_shrink.remove_inner_classes.desc",
                "Remove redundant inner classes"
            )
        )
        val unusedLabels by setting(
            name = enText("process.optimize.class_shrink.remove_unused_labels", "Unused labels remove"),
            value = true,
            desc = enText(
                "process.optimize.class_shrink.remove_unused_labels.desc",
                "Remove unused labels"
            )
        )
        val nopRemove by setting(
            name = enText("process.optimize.class_shrink.nop_remove", "NOP remove"),
            value = true,
            desc = enText(
                "process.optimize.class_shrink.nop_remove.desc",
                "Remove redundant NOP instructions"
            )
        )
        val methodSignatures by setting(
            name = enText("process.optimize.class_shrink.remove_method_signatures", "Method signatures remove"),
            value = true,
            desc = enText(
                "process.optimize.class_shrink.remove_method_signatures.desc",
                "Remove method signatures"
            )
        )
    }

    private val innerClasses = Counter()
    private val unusedLabels = Counter()
    private val nops = Counter()
    private val methodSignatures = Counter()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            Logger.info(" - ClassShrink: Shrinking classes...")
        }
        val innerClasses = reducibleScopeValue { FastCounter() }
        val unusedLabels = reducibleScopeValue { FastCounter() }
        val nops = reducibleScopeValue { FastCounter() }
        val methodSignatures = reducibleScopeValue { FastCounter() }
        parForEach { classNode ->
            val innerClasses = innerClasses.local
            val unusedLabels = unusedLabels.local
            val nops = nops.local
            val methodSignatures = methodSignatures.local
            if (config.innerClasses) {
                classNode.outerClass = null
                classNode.outerMethod = null
                classNode.outerMethodDesc = null
                innerClasses.add(classNode.innerClasses?.size ?: 0)
                classNode.innerClasses.clear()
            }
            if (config.unusedLabels) {
                classNode.methods.forEach { methodNode ->
                    val labels = mutableListOf<LabelNode>()
                    methodNode.instructions.forEach { if (it is LabelNode) labels.add(it) }
                    methodNode.instructions.forEach { instruction ->
                        when (instruction) {
                            is JumpInsnNode -> labels.remove(instruction.label)
                            is LookupSwitchInsnNode -> {
                                labels.remove(instruction.dflt)
                                labels.removeAll(instruction.labels)
                            }

                            is TableSwitchInsnNode -> {
                                labels.remove(instruction.dflt)
                                labels.removeAll(instruction.labels)
                            }

                            is FrameNode -> {
                                instruction.local?.forEach { if (it is LabelNode) labels.remove(it) }
                                instruction.stack?.forEach { if (it is LabelNode) labels.remove(it) }
                            }
                        }
                    }
                    methodNode.localVariables?.forEach {
                        labels.remove(it.start)
                        labels.remove(it.end)
                    }
                    methodNode.tryCatchBlocks?.forEach {
                        labels.remove(it.start)
                        labels.remove(it.end)
                        labels.remove(it.handler)
                    }
                    labels.forEach { methodNode.instructions.remove(it) }
                    unusedLabels.add(labels.size)
                }
            }
            if (config.nopRemove) {
                classNode.methods.forEach { methodNode ->
                    methodNode.instructions.toListFast().asSequence()
                        .filter { it.opcode == Opcodes.NOP }
                        .forEach {
                            methodNode.instructions.remove(it)
                            nops.add()
                        }
                }
            }
            if (config.methodSignatures) {
                classNode.methods.forEach { methodNode ->
                    if (methodNode.signature != null) {
                        methodNode.signature = null
                        methodSignatures.add()
                    }
                }
            }
        }
        post {
            Logger.info(" - ClassShrink:")
            if (config.innerClasses) Logger.info("    Removed ${innerClasses.global.get()} inner classes")
            if (config.unusedLabels) Logger.info("    Removed ${unusedLabels.global.get()} unused labels")
            if (config.nopRemove) Logger.info("    Removed ${nops.global.get()} NOP instructions")
            if (config.methodSignatures) Logger.info("    Removed ${methodSignatures.global.get()} method signatures")
        }
    }
}