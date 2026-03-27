package net.spartanb312.grunteon.obfuscator.process.transformers.optimize

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.StageBuilder
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.util.Counter
import net.spartanb312.grunteon.obfuscator.util.FastCounter
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.matchAnyOp
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.JumpInsnNode

class DeadCodeRemove : Transformer<DeadCodeRemove.Config>(
    name = enText("process.optimize.dead_code_remove", "DeadCodeRemove"),
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
        val pop by setting(
            name = enText("process.optimize.dead_code_remove.pop_remove", "Pop remove"),
            value = true,
            desc = enText("process.optimize.dead_code_remove.pop_remove.desc", "Remove redundant load and pop")
        )
        val pop2 by setting(
            name = enText("process.optimize.dead_code_remove.pop2_remove", "Pop2 remove"),
            value = true,
            desc = enText("process.optimize.dead_code_remove.pop2_remove.desc", "Remove redundant load and pop2")
        )
        val fallthrough by setting(
            name = enText("process.optimize.dead_code_remove.fallthrough", "Fallthrough remove"),
            value = true,
            desc = enText("process.optimize.dead_code_remove.fallthrough.desc", "Remove fall through goto")
        )
    }

    private val counter = Counter()

    context(instance: Grunteon)
    override fun transform(config: Config) {
        Logger.info(" - DeadCodeRemove: Removing dead codes...")
        super.transform(config)
        Logger.info("    Removed ${counter.get()} dead codes")
    }

    context(instance: Grunteon)
    override fun transformClass(classNode: ClassNode, config: Config) {
        classNode.methods.toList().asSequence()
            .filter { !it.isNative && !it.isAbstract }
            .forEach { methodNode ->
                for (it in methodNode.instructions.toList()) {
                    when {
                        config.pop && it.opcode == Opcodes.POP -> {
                            val pre = it.previous ?: continue
                            if (pre.matchAnyOp(Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.ALOAD, Opcodes.LDC)) {
                                methodNode.instructions.remove(pre)
                                methodNode.instructions.remove(it)
                                counter.add(2)
                            }
                        }

                        config.pop2 && it.opcode == Opcodes.POP2 -> {
                            val pre = it.previous ?: continue
                            if (pre.matchAnyOp(Opcodes.DLOAD, Opcodes.LLOAD)) {
                                methodNode.instructions.remove(pre)
                                methodNode.instructions.remove(it)
                                counter.add(2)
                            } else if (pre.matchAnyOp(Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.ALOAD, Opcodes.LDC)) {
                                val prePre = pre.previous ?: continue
                                if (prePre.matchAnyOp(Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.ALOAD, Opcodes.LDC)) {
                                    methodNode.instructions.remove(prePre)
                                    methodNode.instructions.remove(pre)
                                    methodNode.instructions.remove(it)
                                    counter.add(3)
                                }
                            }
                        }

                        config.fallthrough && it.opcode == Opcodes.GOTO -> {
                            val next = it.next
                            if (next == (it as JumpInsnNode).label) {
                                methodNode.instructions.remove(it)
                                counter.add(1)
                            }
                        }
                    }
                }
            }
    }

    override fun StageBuilder.buildStage(config: Config) {
        seq {
            Logger.info(" - DeadCodeRemove: Removing dead codes...")
        }
        val counter = reducibleScopeValue { FastCounter() }
        parForEachFiltered(config) { classNode ->
            classNode.methods.toList().asSequence()
                .filter { !it.isNative && !it.isAbstract }
                .forEach { methodNode ->
                    for (it in methodNode.instructions.toList()) {
                        val counter = counter.local
                        when {
                            config.pop && it.opcode == Opcodes.POP -> {
                                val pre = it.previous ?: continue
                                if (pre.matchAnyOp(Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.ALOAD, Opcodes.LDC)) {
                                    methodNode.instructions.remove(pre)
                                    methodNode.instructions.remove(it)
                                    counter.add(2)
                                }
                            }

                            config.pop2 && it.opcode == Opcodes.POP2 -> {
                                val pre = it.previous ?: continue
                                if (pre.matchAnyOp(Opcodes.DLOAD, Opcodes.LLOAD)) {
                                    methodNode.instructions.remove(pre)
                                    methodNode.instructions.remove(it)
                                    counter.add(2)
                                } else if (pre.matchAnyOp(Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.ALOAD, Opcodes.LDC)) {
                                    val prePre = pre.previous ?: continue
                                    if (prePre.matchAnyOp(Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.ALOAD, Opcodes.LDC)) {
                                        methodNode.instructions.remove(prePre)
                                        methodNode.instructions.remove(pre)
                                        methodNode.instructions.remove(it)
                                        counter.add(3)
                                    }
                                }
                            }

                            config.fallthrough && it.opcode == Opcodes.GOTO -> {
                                val next = it.next
                                if (next == (it as JumpInsnNode).label) {
                                    methodNode.instructions.remove(it)
                                    counter.add(1)
                                }
                            }
                        }
                    }
                }
        }
        seq {
            Logger.info("    Removed ${counter.global.get()} dead codes")
        }
    }
}
