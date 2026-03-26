package net.spartanb312.grunteon.obfuscator.process.hierarchy2

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.genesis.kotlin.extensions.isPrivate
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.config.manager.ConfigGroup
import net.spartanb312.grunteon.obfuscator.pipeline.ProcessPipeline
import net.spartanb312.grunteon.obfuscator.util.extensions.isInitializer
import org.objectweb.asm.tree.MethodNode
import java.util.*
import java.util.function.ToIntFunction
import kotlin.time.DurationUnit
import kotlin.time.measureTime

data class MethodNodeKey(
    val name: String,
    val desc: String,
    val access: Int
)

class MethodHierarchy(
    val classHierarchy: ClassHierarchy,
    val methodNodes: Array<MethodNode>,
    val classNodeMethodLookup: Array<Object2IntOpenHashMap<MethodNodeKey>>,
    val sourceMethod: BooleanArray
) {
    companion object {
        fun build(classHierarchy: ClassHierarchy): MethodHierarchy {
            val methodNodes = ObjectArrayList<MethodNode>()
            val methodOwner = IntArrayList()
            val classMethodNodeLookup = Array(classHierarchy.realClassCount) { Object2IntOpenHashMap<MethodNodeKey>() }
            val classToMethod = ObjectArrayList<IntArrayList>()
            for (i in 0..<classHierarchy.realClassCount) {
                val classNode = classHierarchy.classNodes[i]
                val methods = classNode.methods ?: emptyList()
                val methodList = IntArrayList()
                val methodLookup = classMethodNodeLookup[i]
                methodLookup.defaultReturnValue(-1)
                for (j in 0..<methods.size) {
                    val methodNode = methods[j]
                    if (methodNode.isInitializer) continue
                    val index = methodNodes.size
                    methodNodes.add(methodNode)
                    val key = MethodNodeKey(methodNode.name, methodNode.desc, methodNode.access)
                    methodLookup[key] = index
                    methodOwner.add(i)
                    methodList.add(index)
                }
                classToMethod.add(methodList)
            }

            val methodCount = methodNodes.size
            val methodCodeLookup = Object2IntOpenHashMap<String>()
            val methodCode = IntArray(methodCount)
            val methodAccess = IntArray(methodCount)
            val methodCodeToMethods = ObjectArrayList<IntArrayList>()
            val classToMethodCodeBits = Array(classHierarchy.realClassCount) { BitSet() }

            for (methodIdx in 0..<methodCount) {
                val methodNode = methodNodes[methodIdx]
                val codename = methodNode.name + methodNode.desc
                val myMethodCode = methodCodeLookup.computeIfAbsent(codename, ToIntFunction {
                    methodCodeToMethods.add(IntArrayList())
                    methodCodeLookup.size
                })
                methodCode[methodIdx] = myMethodCode
                methodAccess[methodIdx] = methodNode.access
                methodCodeToMethods[methodCode[methodIdx]].add(methodIdx)

                // Fill inherent method bits
                val methodOwnerIdx = methodOwner.getInt(methodIdx)
                classToMethodCodeBits[methodOwnerIdx].set(myMethodCode)
                val descendents = classHierarchy.descendants[methodOwnerIdx]
                for (i in 0..<descendents.size) {
                    classToMethodCodeBits[descendents[i]].set(myMethodCode)
                }
            }

            // Search up for source method
            val isSourceMethod = BooleanArray(methodCount)
            val methodTreeRoots = IntArrayList() // Roots are aka. source methods
            val methodTreeAdjList = ObjectArrayList<BitSet>()
            val methodToMethodTree = Array(methodCount) { BitSet() } // Tells a method belongs to which method tree(s)
            for (classIdx in 0..<classHierarchy.realClassCount) {
                val descendentIndices = classHierarchy.descendants[classIdx]
                fun setSource(methodIdx: Int) {
                    assert(!isSourceMethod[methodIdx])
                    isSourceMethod[methodIdx] = true
                    val sourceMethodCode = methodCode[methodIdx]
                    val methodTreeIdx = methodTreeRoots.size
                    methodTreeRoots.add(methodIdx)
                    val myMethodTreeAdjList = BitSet()

                    for (i in 0..<descendentIndices.size) {
                        val descendentIdx = descendentIndices[i]
                        val descendentMethods = classToMethod[descendentIdx]
                        val descendentMethodArray = descendentMethods.elements()
                        for (j in 0..<descendentMethods.size) {
                            val descendentMethod = descendentMethodArray[j]
                            val descendentMethodCode = methodCode[descendentMethod]
                            if (descendentMethodCode == sourceMethodCode) {
                                val descendentMethodTreeMarks = methodToMethodTree[descendentMethod]
                                myMethodTreeAdjList.or(descendentMethodTreeMarks)
                                descendentMethodTreeMarks.set(methodTreeIdx)
                            }
                        }
                    }
                    myMethodTreeAdjList.clear(methodTreeIdx)
                    methodTreeAdjList.add(myMethodTreeAdjList)
                }

                val myMethods = classToMethod[classIdx]
                val myMethodArray = myMethods.elements()
                for (j in 0..<myMethods.size) {
                    val myMethod = myMethodArray[j]
                    if (!methodAccess[myMethod].isPrivate) continue
                    setSource(myMethod)
                }

                val parentIndices = classHierarchy.parents[classIdx]
                val allParentMethodCodeBits = BitSet()
                for (i in 0..<parentIndices.size) {
                    val parentIdx = parentIndices[i]
                    if (parentIdx >= classHierarchy.realClassCount) continue
                    val parentCodeBits = classToMethodCodeBits[parentIdx]
                    allParentMethodCodeBits.or(parentCodeBits)
                }

                for (j in 0..<myMethods.size) {
                    val myMethod = myMethodArray[j]
                    if (methodAccess[myMethod].isPrivate) continue
                    val myMethodCode = methodCode[myMethod]
                    if (!allParentMethodCodeBits.get(myMethodCode)) {
                        setSource(myMethod)
                    }
                }
            }

            assert(methodCodeLookup.size == methodCodeToMethods.size) { "Method code lookup size mismatch" }

            return MethodHierarchy(
                classHierarchy,
                methodNodes.toTypedArray(),
                classMethodNodeLookup,
                isSourceMethod
            )
        }
    }
}

fun main() {
    val instance = Grunteon(ConfigGroup(), ProcessPipeline())
    instance.init()

    measureTime {
        MethodHierarchy.build(ClassHierarchy.build(instance.allClasses, instance.workRes::getClassNode))
    }.also { println("Cold: %.2f ms".format(it.toDouble(DurationUnit.MILLISECONDS))) }
    repeat(20) {
        MethodHierarchy.build(ClassHierarchy.build(instance.allClasses, instance.workRes::getClassNode))
    }
    measureTime {
        repeat(5) {
            MethodHierarchy.build(ClassHierarchy.build(instance.allClasses, instance.workRes::getClassNode))
        }
    }.also { println("%.2f ms".format(it.toDouble(DurationUnit.MILLISECONDS) / 5.0)) }
}