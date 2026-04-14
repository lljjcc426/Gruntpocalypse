package net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.extensions.*
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode

class ParameterObfuscate : Transformer<ParameterObfuscate.Config>(
    name = enText("process.miscellaneous.parameter_obfuscate", "ParameterObfuscate"),
    category = Category.Miscellaneous,
    description = enText(
        "process.miscellaneous.parameter_obfuscate.desc",
        "Obfuscate parameters to object type"
    )
) {

    override val defConfig: TransformerConfig get() = Config()
    override val confType: Class<Config> get() = Config::class.java

    class Config : TransformerConfig() {
        // Exclusion
        val exclusion by setting(
            enText("process.miscellaneous.parameter_obfuscate.method_exclusion", "Method exclusion"),
            listOf(
                "net/dummy/**", // Exclude package
                "net/dummy/Class", // Exclude class
                "net/dummy/Class.method", // Exclude method name
                "net/dummy/Class.method()V", // Exclude method with desc
            ),
            enText("process.miscellaneous.parameter_obfuscate.method_exclusion.desc", "Specify method exclusions."),
        )
    }

    private lateinit var methodExPredicate: NamePredicates

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        barrier()
        pre {
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        parForEachClassesFiltered(buildFilterStrategy(config)) { classNode ->
            val counter = counter.local
            val callInstances = Object2ObjectOpenHashMap<MethodNode, MutableList<MethodInsnNode>>()
            // Collect method call instance
            classNode.methods.toList().asSequence()
                .filter { !it.isAbstract && !it.isNative }
                .forEach { method ->
                    method.instructions.toList().forEach { instruction ->
                        if (instruction !is MethodInsnNode) return@forEach
                        val callingOwner = instance.workRes.getClassNode(instruction.owner) ?: return@forEach
                        if (callingOwner.name != classNode.name) return@forEach
                        val callingMethod = callingOwner.methods?.toList()?.find {
                            it.name == instruction.name && it.desc == instruction.desc
                        } ?: return@forEach
                        if (callingMethod.isInitializer) return@forEach
                        if (!callingMethod.isPrivate) return@forEach
                        callInstances.getOrPut(callingMethod) { mutableListOf() }.add(instruction)
                    }
                }
            // Obfuscate parameter
            callInstances.forEach { (callingMethod, instances) ->
                val isStatic = callingMethod.isStatic
                val params = Type.getArgumentTypes(callingMethod.desc)
                val mappedParams =
                    params.map { if (it.descriptor.startsWith("L")) "Ljava/lang/Object;" else it.descriptor }
                val indexedParams = Int2ObjectOpenHashMap<Type>()
                var stack = 0
                params.forEach {
                    indexedParams[stack] = it
                    stack += it.size
                }
                val oldDesc = callingMethod.desc
                val newDesc = "(${mappedParams.joinToString("")})${oldDesc.substringAfter(")")}"
                //if (oldDesc != newDesc) println("Desc ${callingMethod.desc} -> $newDesc")
                if (oldDesc != newDesc) {
                    // println("Obfuscated ${classNode.name}.${callingMethod.name}")
                    callingMethod.desc = newDesc
                    instances.forEach { it.desc = newDesc }
                    callingMethod.instructions.forEach { instruction ->
                        if (instruction is VarInsnNode && instruction.opcode == Opcodes.ALOAD) {
                            val index = if (isStatic) instruction.`var`
                            else if (instruction.`var` > 0) instruction.`var` - 1
                            else -1
                            val foundType = indexedParams.getOrElse(index) { null }
                            if (foundType != null) {
                                val cast = TypeInsnNode(
                                    Opcodes.CHECKCAST,
                                    foundType.descriptor.correctCast()
                                )
                                callingMethod.instructions.insert(instruction, cast)
                            }
                        }
                    }
                }
            }
        }
        barrier()
    }

    private fun String.correctCast() : String {
        return if (startsWith("L")) removePrefix("L").removeSuffix(";") else this
    }

}