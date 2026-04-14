package net.spartanb312.grunteon.obfuscator.process.transformers.redirect

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.DISABLE_INVOKE_DISPATCHER
import net.spartanb312.grunteon.obfuscator.util.IGNORE_INVOKE_DISPATCHER
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isInitializer
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.methodFullDesc
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.isExcluded
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

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

        val maxParams = 5
        val maxHandles = 5

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
        pre {
            //Logger.info(" > InvokeDispatcher: Redirecting invokes to dispatcher...")
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        parForEachClassesFiltered(buildFilterStrategy(config)) { classNode ->
            val counter = counter.local
            if (classNode.isExcluded(DISABLE_INVOKE_DISPATCHER)) return@parForEachClassesFiltered
            val invokeInstances = mutableListOf<CallInstance>()
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
                if (it.paramsType.size > config.maxParams) return@forEach
                if (it.hasDoubleOrLong) return@forEach
                val existedList = rawGroup.getOrPut(it.returnType) { mutableListOf() }
                if (existedList.size >= config.maxHandles) return@forEach
                existedList.add(it)
            }
            val group = rawGroup.filter { it.value.size > 1 }
            //if (group.isNotEmpty()) group.forEach { (returnDesc, list) ->
            //    println("Group[$returnDesc]: ${list.joinToString(", ") { it.paramsType.size.toString() }}]")
            //}
            group.forEach { (returnDesc, list) ->
                val maxParams = list.maxOf { it.paramsType.size }

            }
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
        var hasDoubleOrLong = false
        val paramsType = Type.getArgumentTypes(callingMethod.desc).map {
            val desc = it.descriptor
            if (desc == "J" || desc == "D") hasDoubleOrLong = true
            desc
        }
        val returnType = Type.getReturnType(callingMethod.desc).descriptor
    }

}