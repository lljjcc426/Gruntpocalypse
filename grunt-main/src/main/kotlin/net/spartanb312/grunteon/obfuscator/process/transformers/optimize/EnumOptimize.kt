package net.spartanb312.grunteon.obfuscator.process.transformers.optimize

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.resource.WorkResources
import net.spartanb312.grunteon.obfuscator.util.Counter
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.extensions.findMethod
import net.spartanb312.grunteon.obfuscator.util.extensions.isEnum
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode

class EnumOptimize : Transformer<EnumOptimize.Config>(
    name = enText("process.optimize.enum_optimize", "EnumOptimize"),
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

    class Config : TransformerConfig()

    private val counter = Counter()

    context(instance: Grunteon, res: WorkResources)
    override fun transform(config: Config) {
        Logger.info(" - EnumOptimize: Optimizing enums...")
        super.transform(config)
        Logger.info("    Optimized ${counter.get()} enums")
    }

    context(instance: Grunteon, res: WorkResources)
    override fun transformClass(classNode: ClassNode, config: Config) {
        if (!classNode.isEnum) return
        val desc = "[L${classNode.name};"
        val valuesMethod = classNode.findMethod("values", "()$desc") {
            it.instructions.size() >= 4
        }
        if (valuesMethod != null) {
            for (instruction in valuesMethod.instructions.toList()) {
                if (instruction is MethodInsnNode) {
                    if (instruction.opcode == Opcodes.INVOKEVIRTUAL && instruction.name == "clone") {
                        if (instruction.next.opcode == Opcodes.CHECKCAST) {
                            valuesMethod.instructions.remove(instruction.next)
                        }
                        valuesMethod.instructions.remove(instruction)
                        counter.add(1)
                    }
                }
            }
        }
    }

}