package net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isBridge
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.isPrivate
import net.spartanb312.grunteon.obfuscator.util.extensions.isStatic
import net.spartanb312.grunteon.obfuscator.util.extensions.isSynthetic
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.util.concurrent.ConcurrentLinkedQueue

class ParameterObfuscate : Transformer<ParameterObfuscate.Config>(
    name = enText("process.miscellaneous.parameter_obfuscate", "ParameterObfuscate"),
    category = Category.Miscellaneous,
    description = enText(
        "process.miscellaneous.parameter_obfuscate.desc",
        "Obfuscate parameters to object type"
    )
) {
    @Serializable
    data class Config(
        @SettingDesc(enText = "Only obfuscate private methods")
        val onlyPrivateMethod: Boolean = true, // Not stable for all methods
        @SettingDesc(enText = "Specify class include/exclude rules")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc(enText = "Specify method exclusions.")
        val exclusion: List<String> = listOf(
            "net/dummy/**",
            "net/dummy/Class",
            "net/dummy/Class.method",
            "net/dummy/Class.method()V"
        )
    ) : TransformerConfig

    private lateinit var methodExPredicate: NamePredicates

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        barrier()
        pre {
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        val remapJob = ConcurrentLinkedQueue<RemapJob>()
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            val counter = counter.local
            val callInstances = Object2ObjectOpenHashMap<CallTarget, MutableSet<MethodInsnNode>>()
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
                        //if (callingMethod.isInitializer) return@forEach
                        if (!callingMethod.isPrivate && (!callingMethod.isStatic || config.onlyPrivateMethod)) return@forEach
                        if (callingMethod.isSynthetic || callingMethod.isBridge) return@forEach
                        val shadowNames = callingOwner.methods.filter {
                            it.name == callingMethod.name
                                    && Type.getArgumentTypes(it.desc).size == Type.getArgumentTypes(callingMethod.desc).size
                        }
                        if (shadowNames.size > 1) return@forEach // avoid bridge method shadow
                        callInstances.getOrPut(CallTarget(callingOwner, callingMethod)) {
                            mutableSetOf()
                        }.add(instruction) // add this
                    }
                }
            // Obfuscate parameter
            callInstances.forEach { (callTarget, instances) ->
                val callingOwner = callTarget.owner
                val callingMethod = callTarget.method
                val isStatic = callingMethod.isStatic
                val params = Type.getArgumentTypes(callingMethod.desc)
                val mappedParams =
                    params.map {
                        if (it.descriptor.startsWith("L")) {
                            if (it.descriptor != "Ljava/lang/Object;") counter.add()
                            "Ljava/lang/Object;"
                        } else it.descriptor
                    }
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
                    // Scan job
                    if (!config.onlyPrivateMethod) remapJob.add(
                        RemapJob(
                            callingOwner.name,
                            callingMethod.name,
                            oldDesc,
                            newDesc
                        )
                    )
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
        seq {
            // search all
            if (!config.onlyPrivateMethod) {
                val list = ObjectArrayList(remapJob)
                instance.workRes.inputClassCollection.forEach { classNode ->
                    classNode.methods.forEach { method ->
                        method.instructions.forEach { instr ->
                            if (instr is MethodInsnNode) {
                                for (remapJob in list) {
                                    if (instr.owner == remapJob.owner && instr.name == remapJob.name && instr.desc == remapJob.preDesc) {
                                        instr.desc = remapJob.newDesc
                                        //println("${remapJob.owner}.${remapJob.name}${remapJob.preDesc}->${remapJob.newDesc}")
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        post {
            Logger.info(" - ParameterObfuscate:")
            Logger.info("    Obfuscated ${counter.global.get()} parameters")
        }
    }

    data class CallTarget(
        val owner: ClassNode,
        val method: MethodNode,
    )

    class RemapJob(
        val owner: String,
        val name: String,
        val preDesc: String,
        val newDesc: String,
    )

    private fun String.correctCast(): String {
        return if (startsWith("L")) removePrefix("L").removeSuffix(";") else this
    }

}
