package net.spartanb312.grunteon.obfuscator.process.transformers.redirect

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.spartanb312.genesis.kotlin.extensions.LABEL
import net.spartanb312.genesis.kotlin.extensions.PRIVATE
import net.spartanb312.genesis.kotlin.extensions.STATIC
import net.spartanb312.genesis.kotlin.extensions.insn.CHECKCAST
import net.spartanb312.genesis.kotlin.extensions.insn.ILOAD
import net.spartanb312.genesis.kotlin.extensions.insn.INVOKESTATIC
import net.spartanb312.genesis.kotlin.extensions.insn.INVOKEVIRTUAL
import net.spartanb312.genesis.kotlin.extensions.node
import net.spartanb312.genesis.kotlin.extensions.toInsnNode
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.DISABLE_INVOKE_DISPATCHER
import net.spartanb312.grunteon.obfuscator.util.IGNORE_INVOKE_DISPATCHER
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.getLoadType
import net.spartanb312.grunteon.obfuscator.util.extensions.getReturnType
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isInitializer
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.isStatic
import net.spartanb312.grunteon.obfuscator.util.extensions.methodFullDesc
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.isExcluded
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import net.spartanb312.grunteon.obfuscator.util.getRandomString
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import org.objectweb.asm.tree.VarInsnNode
import kotlin.random.Random

class InvokeDispatcher : Transformer<InvokeDispatcher.Config>(
    name = enText("process.redirect.invoke_dispatcher", "InvokeDispatcher"),
    category = Category.Redirect,
    description = enText(
        "process.redirect.invoke_dispatcher.desc",
        "Redirect multiple method invokes to a single dispatcher"
    )
) {

    override val defConfig: TransformerConfig get() = Config()
    override val confType: Class<Config> get() = Config::class.java

    class Config : TransformerConfig() {
        val chance by setting(
            name = enText("process.redirect.invoke_dispatcher.replace_chance", "ReplaceChance"),
            value = 0.3f,
            range = 0f..1f,
            desc = enText(
                "process.redirect.invoke_dispatcher.replace_chance.desc",
                "The chance that attempt to replace put/set to getter/setter"
            )
        )

        val maxParams = 10
        val maxHandles = 10

        // Exclusion
        val exclusion by setting(
            enText("process.redirect.invoke_dispatcher.method_exclusion", "Method exclusion"),
            listOf(
                "net/dummy/**", // Exclude package
                "net/dummy/Class", // Exclude class
                "net/dummy/Class.method", // Exclude method name
                "net/dummy/Class.method()V", // Exclude method with desc
            ),
            enText("process.redirect.invoke_dispatcher.method_exclusion.desc", "Specify method exclusions."),
        )
    }

    private lateinit var methodExPredicate: NamePredicates

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        barrier()
        pre {
            //Logger.info(" > InvokeDispatcher: Redirecting invokes to dispatcher...")
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        val counter2 = reducibleScopeValue { MergeableCounter() }
        parForEachClassesFiltered(buildFilterStrategy(config)) { classNode ->
            val counter = counter.local
            val counter2 = counter2.local
            if (classNode.isExcluded(DISABLE_INVOKE_DISPATCHER)) return@parForEachClassesFiltered
            val invokeInstances = mutableListOf<CallInstance>()
            val randomGen = Xoshiro256PPRandom(getSeed(classNode.name))
            // Collect invokes
            classNode.methods.toList().asSequence()
                .filter { !it.isAbstract && !it.isNative && !it.isInitializer }
                .forEach { method ->
                    if (method.isExcluded(DISABLE_INVOKE_DISPATCHER)) return@forEach
                    val excluded = methodExPredicate.matchedAnyBy(methodFullDesc(classNode, method))
                    if (excluded) return@forEach
                    val randomGen = Xoshiro256PPRandom(getSeed(classNode.name, method.name, method.desc))
                    method.instructions.toList().forEach { instruction ->
                        if (instruction !is MethodInsnNode) return@forEach
                        if (randomGen.nextFloat() > config.chance) return@forEach
                        val callingOwner = instance.workRes.getClassNode(instruction.owner) ?: return@forEach
                        if (callingOwner.isExcluded(IGNORE_INVOKE_DISPATCHER)) return@forEach
                        val callingMethod = callingOwner.methods?.find {
                            it.name == instruction.name && it.desc == instruction.desc
                        } ?: return@forEach
                        if (callingMethod.isExcluded(IGNORE_INVOKE_DISPATCHER)) return@forEach
                        val callInstance = CallInstance(
                            classNode,
                            method,
                            instruction,
                            callingOwner,
                            callingMethod,
                        )
                        invokeInstances.add(callInstance)
                    }
                }
            // Group by return types
            val rawGroup = Object2ObjectOpenHashMap<String, MutableList<CallInstance>>()
            invokeInstances.shuffled().forEach {
                if (it.loadType.size > config.maxParams) return@forEach
                if (it.hasPrimitive) return@forEach
                val existedList = rawGroup.getOrPut(it.returnType) { mutableListOf() }
                if (existedList.size >= config.maxHandles) return@forEach
                existedList.add(it)
            }
            val group = rawGroup.filter { it.value.size > 1 }
            //if (group.isNotEmpty()) group.forEach { (returnDesc, list) ->
            //    println("Group[$returnDesc]: \n ${list.joinToString("\n ") { it.loadType.joinToString(",") { it } }}]")
            //}
            group.forEach { (returnDesc, list) ->
                val maxLoadOp = list.maxOf { it.loadType.size }
                val conditions = list.size
                counter.add(conditions)
                val endCase = randomGen.nextInt()
                val startCase = endCase - conditions + 1
                var desc = "("
                repeat(maxLoadOp) { desc += "Ljava/lang/Object;" }
                desc += "I)$returnDesc"
                val dispatcher = method(
                    PRIVATE + STATIC,
                    "dispatcher_${randomGen.getRandomString(10)}",
                    desc
                ) {
                    INSTRUCTIONS {
                        val startLabel = Label()
                        val labels = buildList { repeat(conditions) { add(Label()) } }
                        LABEL(startLabel)
                        ILOAD(maxLoadOp)
                        +TableSwitchInsnNode(
                            startCase,
                            endCase,
                            startLabel.node,
                            *labels.map { it.node }.toTypedArray()
                        )
                        labels.forEachIndexed { index, label ->
                            LABEL(label)
                            val handle = list[index]
                            val accessKey = startCase + index
                            handle.callHandle.owner = classNode.name
                            handle.callHandle.name = methodNode.name
                            handle.callHandle.desc = methodNode.desc
                            // fix load
                            val addNulls = maxLoadOp - handle.loadType.size
                            if (addNulls > 0) repeat(addNulls) {
                                handle.callerMethod.instructions.insertBefore(
                                    handle.callHandle,
                                    InsnNode(Opcodes.ACONST_NULL)
                                )
                            }
                            // load key
                            handle.callerMethod.instructions.insertBefore(
                                handle.callHandle,
                                accessKey.toInsnNode()
                            )
                            if (handle.callingMethod.isStatic) {
                                var stack = 0
                                Type.getArgumentTypes(handle.callingMethod.desc).forEach {
                                    +VarInsnNode(it.getLoadType(), stack)
                                    CHECKCAST(it.descriptor.removePrefix("L").removeSuffix(";"))
                                    stack += it.size
                                }
                                INVOKESTATIC(
                                    handle.callingOwner.name,
                                    handle.callingMethod.name,
                                    handle.callingMethod.desc
                                )
                                +InsnNode(handle.callingMethod.desc.getReturnType())
                            } else {
                                var stack = 0
                                Type.getArgumentTypes(handle.callingMethod.desc).forEach {
                                    +VarInsnNode(it.getLoadType(), stack)
                                    CHECKCAST(it.descriptor.removePrefix("L").removeSuffix(";"))
                                    stack += it.size
                                }
                                INVOKEVIRTUAL(
                                    handle.callingOwner.name,
                                    handle.callingMethod.name,
                                    handle.callingMethod.desc
                                )
                                +InsnNode(handle.callingMethod.desc.getReturnType())
                            }
                        }
                    }
                }
                classNode.methods.add(dispatcher)
                counter2.add()
                //println("Added dispatcher[${list.size}] ${dispatcher.name} to ${classNode.name}")
            }
        }
        post {
            Logger.info(" - InvokeDispatcher:")
            Logger.info("    Redirected ${counter.global.get()} method calls to ${counter2.global.get()} dispatchers")
        }
    }

    data class CallInstance(
        val caller: ClassNode,
        val callerMethod: MethodNode,
        val callHandle: MethodInsnNode,
        val callingOwner: ClassNode,
        val callingMethod: MethodNode,
    ) {
        val callerFullName get() = "${caller.name}.${callerMethod.name}${callerMethod.desc}"
        val callingFullName get() = "${callingOwner.name}.${callingMethod.name}${callingMethod.desc}"
        private val paramsType = Type.getArgumentTypes(callingMethod.desc).map { it.descriptor }
        val loadType = if (callingMethod.isStatic) paramsType
        else mutableListOf(callingOwner.name).apply { addAll(paramsType) }
        val hasPrimitive = run {
            loadType.forEach {
                if (!it.startsWith("L") && !it.startsWith("[")) return@run true
            }
            return@run false
        }
        val returnType = Type.getReturnType(callingMethod.desc).descriptor
    }

}