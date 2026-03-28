package net.spartanb312.grunteon.obfuscator.process

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap
import kotlinx.coroutines.runBlocking
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.util.LWWSP
import org.objectweb.asm.tree.ClassNode
import java.util.concurrent.RecursiveTask

class ScopeValueAccess(
    internal val globals: Array<Any>,
    internal val reducibleGlobal: Array<Mergeable<*>>,
    internal val reducibleLocal: Array<Mergeable<*>?>
) {
    constructor(global: ScopeValueGlobal) : this(
        global.globals,
        global.reducibleGlobal,
        arrayOfNulls(global.reducibleGlobal.size)
    )

    fun fork(): ScopeValueAccess {
        return ScopeValueAccess(globals, reducibleGlobal, arrayOfNulls(reducibleGlobal.size))
    }

    fun mergeToLocal(other: ScopeValueAccess) {
        for (i in reducibleGlobal.indices) {
            val mine = reducibleLocal[i]
            val yours = other.reducibleLocal[i]
            if (mine != null) {
                if (yours != null) {
                    mine.merge(yours)
                }
            } else if (yours != null) {
                reducibleLocal[i] = yours
            }
        }
    }
}

class ScopeValueGlobal(
    internal val globals: Array<Any>,
    internal val reducibleGlobal: Array<Mergeable<*>>
) {
    fun mergeToGlobal(other: ScopeValueAccess) {
        for (i in reducibleGlobal.indices) {
            val mine = reducibleGlobal[i]
            val yours = other.reducibleLocal[i]
            if (yours != null) {
                mine.merge(yours)
            }
        }
    }
}

sealed interface Instruction {
    object Barrier : Instruction
    class Seq(val block: context(Grunteon, ScopeValueAccess) () -> Unit) : Instruction
    class Pre(val block: context(Grunteon, ScopeValueAccess) () -> Unit) : Instruction
    class Post(val block: context(Grunteon, ScopeValueAccess) () -> Unit) : Instruction
    class SeqForEach(
        val includeGenerated: Boolean,
        val block: context(Grunteon, ScopeValueAccess)  (classNode: ClassNode) -> Unit
    ) : Instruction

    class ParForEach(
        val includeGenerated: Boolean,
        val block: context(Grunteon, ScopeValueAccess)  (classNode: ClassNode) -> Unit
    ) : Instruction
}

sealed interface ScopeValueKey<T> {
    sealed interface Global<T> : ScopeValueKey<T> {
        context(_: Grunteon, _: ScopeValueAccess)
        val global: T
    }

    sealed interface Reducible<T : Mergeable<*>> : ScopeValueKey<T> {
        context(_: Grunteon, _: ScopeValueAccess)
        val global: T

        context(_: Grunteon, _: ScopeValueAccess)
        val local: T
    }
}

internal class GlobalScopeValueKeyImpl<T>(val init: context(Grunteon) () -> T, val index: Int) :
    ScopeValueKey.Global<T> {
    @Suppress("UNCHECKED_CAST")
    context(_: Grunteon, access: ScopeValueAccess)
    override val global: T
        get() = access.globals[index] as T
}

internal class ReducibleScopeValueKeyImpl<T : Mergeable<*>>(val init: context(Grunteon) () -> T, val index: Int) :
    ScopeValueKey.Reducible<T> {
    @Suppress("UNCHECKED_CAST")
    context(_: Grunteon, access: ScopeValueAccess)
    override val global: T
        get() = access.reducibleGlobal[index] as T

    @Suppress("UNCHECKED_CAST")
    context(_: Grunteon, access: ScopeValueAccess)
    override val local: T
        get() {
            var value = access.reducibleLocal[index] as T?
            if (value == null) {
                value = init()
                access.reducibleLocal[index] = value
            }
            return value
        }
}

class PipelineBuilder {
    internal val instructions = mutableListOf<Instruction>()
    internal val globalScopeValueKeys = mutableListOf<GlobalScopeValueKeyImpl<*>>()
    internal val reducibleScopeValueKeys = mutableListOf<ReducibleScopeValueKeyImpl<*>>()

    fun barrier() {
        instructions += Instruction.Barrier
    }

    fun seq(block: context(Grunteon, ScopeValueAccess) () -> Unit) {
        instructions += Instruction.Seq(block)
    }

    fun pre(block: context(Grunteon, ScopeValueAccess) () -> Unit) {
        instructions += Instruction.Pre(block)
    }

    fun post(block: context(Grunteon, ScopeValueAccess) () -> Unit) {
        instructions += Instruction.Post(block)
    }

    fun seqForEach(
        includeGenerated: Boolean = false,
        block: context(Grunteon, ScopeValueAccess) (classNode: ClassNode) -> Unit
    ) {
        instructions += Instruction.SeqForEach(includeGenerated, block)
    }

    fun parForEach(
        includeGenerated: Boolean = false,
        block: context(Grunteon, ScopeValueAccess) (classNode: ClassNode) -> Unit
    ) {
        instructions += Instruction.ParForEach(includeGenerated, block)
    }

    fun <T> globalScopeValue(init: context(Grunteon) () -> T): ScopeValueKey.Global<T> {
        val key = GlobalScopeValueKeyImpl(init, globalScopeValueKeys.size)
        globalScopeValueKeys += key
        return key
    }

    fun <T : Mergeable<*>> reducibleScopeValue(init: context(Grunteon) () -> T): ScopeValueKey.Reducible<T> {
        val key = ReducibleScopeValueKeyImpl(init, reducibleScopeValueKeys.size)
        reducibleScopeValueKeys += key
        return key
    }
}

private val lwwsp = LWWSP(Runtime.getRuntime().availableProcessors()) {
    it.isDaemon = true
}

internal class WorkerContext {
    val globalKeys = Reference2ObjectOpenHashMap<GlobalScopeValueKeyImpl<*>, Any>()
    val reducibleKeys = Reference2ObjectOpenHashMap<ReducibleScopeValueKeyImpl<*>, Mergeable<*>>()

    fun execute(instance: Grunteon, pipelineBuilder: PipelineBuilder) {
        runBlocking {
            val global = Array(pipelineBuilder.globalScopeValueKeys.size) { index ->
                // WTF fastutil Reference2ObjectFunction is broken and doesn't give correctly typed parameter
                globalKeys.computeIfAbsent(pipelineBuilder.globalScopeValueKeys[index], java.util.function.Function {
                    it.init(instance)
                })
            }
            val reducible = Array(pipelineBuilder.reducibleScopeValueKeys.size) { index ->
                reducibleKeys.computeIfAbsent(
                    pipelineBuilder.reducibleScopeValueKeys[index],
                    java.util.function.Function {
                        it.init(instance)
                    })
            }
            val scopeValueGlobal = ScopeValueGlobal(global, reducible)

            val preTasks = ObjectArrayList<Instruction.Pre>()
            val pendingParallelTasks = ObjectArrayList<Instruction.ParForEach>()
            val postTasks = ObjectArrayList<Instruction.Post>()

            suspend fun flushParallelTasks() {
                preTasks.forEach {
                    val access = ScopeValueAccess(scopeValueGlobal)
                    it.block.invoke(instance, access)
                    scopeValueGlobal.mergeToGlobal(access)
                }
                preTasks.clear()
                if (!pendingParallelTasks.isEmpty) {
                    val tasks = pendingParallelTasks.toTypedArray()
                    pendingParallelTasks.clear()
                    val classArray = instance.workRes.inputClassCollection.toTypedArray()
                    val access = LWWSP.iterativeTask(
                        size = classArray.size,
                        batchSize = 128,
                        newScope = { ScopeValueAccess(scopeValueGlobal) },
                        action = { start, end ->
                            for (i in start..<end) {
                                val node = classArray[i]
                                @Suppress("ReplaceManualRangeWithIndicesCalls")
                                for (j in 0..<tasks.size) {
                                    tasks[j].block.invoke(
                                        instance,
                                        this,
                                        node
                                    )
                                }
                            }
                        }
                    ).submit(lwwsp).await().getOrThrow().reduce { a, b ->
                        a.mergeToLocal(b)
                        a
                    }
                    scopeValueGlobal.mergeToGlobal(access)
                }
                postTasks.forEach {
                    val access = ScopeValueAccess(scopeValueGlobal)
                    it.block.invoke(instance, access)
                    scopeValueGlobal.mergeToGlobal(access)
                }
                postTasks.clear()
            }

            pipelineBuilder.instructions.forEach { inst ->
                when (inst) {
                    is Instruction.Seq -> {
                        flushParallelTasks()
                        val access = ScopeValueAccess(scopeValueGlobal)
                        inst.block.invoke(instance, access)
                        scopeValueGlobal.mergeToGlobal(access)
                    }
                    is Instruction.SeqForEach -> {
                        flushParallelTasks()
                        val access = ScopeValueAccess(scopeValueGlobal)
                        instance.workRes.inputClassCollection.forEach { classNode ->
                            inst.block.invoke(instance, access, classNode)
                        }
                        scopeValueGlobal.mergeToGlobal(access)
                    }
                    is Instruction.ParForEach -> {
                        pendingParallelTasks.add(inst)
                    }
                    is Instruction.Pre -> {
                        preTasks.add(inst)
                    }
                    is Instruction.Post -> {
                        postTasks.add(inst)
                    }
                    is Instruction.Barrier -> {
                        flushParallelTasks()
                    }
                }
            }
            flushParallelTasks()
        }
    }

    internal class ParForEachTask(
        val sharedResources: SharedResources,
        val access: ScopeValueAccess,
        val from: Int,
        val to: Int
    ) : RecursiveTask<ScopeValueAccess>() {
        override fun compute(): ScopeValueAccess {
            val size = to - from
            val targetSize = maxOf(sharedResources.classArray.size / 256, 16)
            if (size > targetSize) {
                val mid = (from + size / 2)
                val left = ParForEachTask(sharedResources, access, from, mid)
                val right = ParForEachTask(sharedResources, access.fork(), mid, to)
                right.fork()
                val leftResult = left.compute()
                val rightResult = right.join()
                leftResult.mergeToLocal(rightResult)
                return leftResult
            }

            val func = sharedResources.instruction.block
            val instance = sharedResources.instance
            val classArray = sharedResources.classArray
            for (i in from until to) {
                func.invoke(instance, access, classArray[i])
            }
            return access
        }

        class SharedResources(
            val instance: Grunteon,
            val instruction: Instruction.ParForEach,
            val classArray: Array<ClassNode>
        )
    }
}
