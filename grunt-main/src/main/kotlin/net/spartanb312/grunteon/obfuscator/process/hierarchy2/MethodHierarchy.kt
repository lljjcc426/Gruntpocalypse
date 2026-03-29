package net.spartanb312.grunteon.obfuscator.process.hierarchy2

import it.unimi.dsi.fastutil.ints.*
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.grunteon.obfuscator.util.extensions.isInitializer
import org.objectweb.asm.tree.MethodNode
import java.util.function.ToIntFunction

data class MethodNodeKey(
    val name: String,
    val desc: String,
    val access: Int
)

class MethodHierarchy(
    val classHierarchy: ClassHierarchy,
    val methodNodes: Array<MethodNode>,
    val methodOwners: IntArray,
    val classNodeMethodLookup: Array<Object2IntOpenHashMap<MethodNodeKey>>,
    val sourceMethod: BooleanArray,
    val methodTreeRoots: IntArray,
    val sourceMethodToMethodTreeIdxLookup: Int2IntOpenHashMap,
    val methodTreeAdjList: Array<IntArray>,
    val methodTreeToConnectedComponent: IntArray,
    val treeCCToTreeIdx: Array<IntArray>
) {
    companion object {
        fun build(classHierarchy: ClassHierarchy): MethodHierarchy {
            val methodNodes = ObjectArrayList<MethodNode>(classHierarchy.realClassCount)
            val methodOwner = IntArrayList(classHierarchy.realClassCount)
            val classMethodNodeLookup = Array(classHierarchy.realClassCount) { Object2IntOpenHashMap<MethodNodeKey>() }
            val classToMethod = arrayOfNulls<IntArrayList>(classHierarchy.realClassCount) as Array<IntArrayList>

            fun populateMethodNodesAndOwners() {
                for (i in 0..<classHierarchy.realClassCount) {
                    val classNode = classHierarchy.classNodes[i]
                    val methods = classNode.methods ?: emptyList()
                    val methodList = IntArrayList()
                    classToMethod[i] = methodList
                    val methodLookup = classMethodNodeLookup[i]
                    methodLookup.defaultReturnValue(-1)
                    for (j in methods.indices) {
                        val methodNode = methods[j]
                        if (methodNode.isInitializer) continue
                        val index = methodNodes.size
                        methodNodes.add(methodNode)
                        val key = MethodNodeKey(methodNode.name, methodNode.desc, methodNode.access)
                        methodLookup[key] = index
                        methodOwner.add(i)
                        methodList.add(index)
                    }
                }
            }
            populateMethodNodesAndOwners()

            val methodCount = methodNodes.size
            val methodCodeLookup = Object2IntOpenHashMap<String>(methodCount)
            val methodCode = IntArray(methodCount)
            val methodAccess = IntArray(methodCount)
            val methodToMethodTree = Array(classHierarchy.realClassCount) {
                Int2ObjectOpenHashMap<IntArraySet>()
            } // Tells a class's method code belongs to which method tree(s)

            fun assignMethodCodeAndBroadcastToDescendants() {
                for (methodIdx in 0..<methodCount) {
                    val methodNode = methodNodes[methodIdx]
                    val codename = methodNode.name + methodNode.desc
                    val myMethodCode = methodCodeLookup.computeIfAbsent(codename, ToIntFunction {
                        methodCodeLookup.size
                    })
                    methodCode[methodIdx] = myMethodCode
                    methodAccess[methodIdx] = methodNode.access

                    // Fill inherent method bits
                    if (methodNode.access.isPrivate) continue
                    val methodOwnerIdx = methodOwner.getInt(methodIdx)
                    methodToMethodTree[methodOwnerIdx].put(myMethodCode, IntArraySet())
                    val descendents = classHierarchy.descendants[methodOwnerIdx]
                    for (i in 0..<descendents.size) {
                        val descendentIdx = descendents[i]
                        if (descendentIdx < classHierarchy.realClassCount) {
                            methodToMethodTree[descendentIdx].put(myMethodCode, IntArraySet())
                        }
                    }
                }
            }
            assignMethodCodeAndBroadcastToDescendants()

            // Search up for source method
            val isSourceMethod = BooleanArray(methodCount)
            val methodTreeRoots = IntArrayList() // Roots are aka. source methods
            val methodTreeAdjList = ObjectArrayList<IntSet>()
            val sourceMethodToMethodTreeIdxLookup = Int2IntOpenHashMap(methodCount)
            sourceMethodToMethodTreeIdxLookup.defaultReturnValue(-1)

            fun assignMethodTreeToMethods() {
                for (classIdx in 0..<classHierarchy.realClassCount) {
                    val descendentIndices = classHierarchy.descendants[classIdx]
                    fun setSource(methodIdx: Int) {
                        assert(!isSourceMethod[methodIdx])
                        isSourceMethod[methodIdx] = true
                        val sourceMethodCode = methodCode[methodIdx]
                        val methodTreeIdx = methodTreeRoots.size
                        methodTreeRoots.add(methodIdx)
                        sourceMethodToMethodTreeIdxLookup[methodIdx] = methodTreeIdx
                        if (descendentIndices.isEmpty()) {
                            methodTreeAdjList.add(IntSets.emptySet())
                            return
                        }
                        val myMethodTreeAdjList = IntArraySet()

                        for (i in 0..<descendentIndices.size) {
                            val descendentIdx = descendentIndices[i]
                            val descendentMethodCodes = methodToMethodTree[descendentIdx]
                            val descendentMethodCodeTreeIndices =
                                descendentMethodCodes[sourceMethodCode] ?: continue
                            val iterator = descendentMethodCodeTreeIndices.intIterator()
                            while (iterator.hasNext()) {
                                val otherMethodTreeIdx = iterator.nextInt()
                                methodTreeAdjList[otherMethodTreeIdx].add(methodTreeIdx)
                                myMethodTreeAdjList.add(otherMethodTreeIdx)
                            }
                            descendentMethodCodeTreeIndices.add(methodTreeIdx)
                        }
                        myMethodTreeAdjList.remove(methodTreeIdx)
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
                    val allParentMethodCodeBits = IntOpenHashSet()
                    for (i in 0..<parentIndices.size) {
                        val parentIdx = parentIndices[i]
                        if (parentIdx >= classHierarchy.realClassCount) continue
                        val parentCodeBits = methodToMethodTree[parentIdx]
                        allParentMethodCodeBits.addAll(parentCodeBits.keys)
                    }

                    for (j in 0..<myMethods.size) {
                        val myMethod = myMethodArray[j]
                        if (methodAccess[myMethod].isPrivate) continue
                        val myMethodCode = methodCode[myMethod]
                        if (!allParentMethodCodeBits.contains(myMethodCode)) {
                            setSource(myMethod)
                        }
                    }
                }
            }

            assignMethodTreeToMethods()

            val treeGraphConnectedComponents = ObjectArrayList<IntArrayList>()
            val methodTreeToConnectedComponent = IntArray(methodTreeRoots.size) { -1 }

            fun buildMethodTreeGraphConnectedComponents() {
                val visitedTree = BooleanArray(methodTreeRoots.size)
                fun dfs(treeIdx: Int, connectedComponentIdx: Int) {
                    if (visitedTree[treeIdx]) return
                    visitedTree[treeIdx] = true
                    treeGraphConnectedComponents[connectedComponentIdx].add(treeIdx)
                    methodTreeToConnectedComponent[treeIdx] = connectedComponentIdx
                    val adjList = methodTreeAdjList[treeIdx]
                    val iterator = adjList.intIterator()
                    while (iterator.hasNext()) {
                        val nextTreeIdx = iterator.nextInt()
                        dfs(nextTreeIdx, connectedComponentIdx)
                    }
                }

                for (i in 0..<methodTreeRoots.size) {
                    if (!visitedTree[i]) {
                        val connectedComponentIdx = treeGraphConnectedComponents.size
                        treeGraphConnectedComponents.add(IntArrayList())
                        dfs(i, connectedComponentIdx)
                    }
                }
            }

            buildMethodTreeGraphConnectedComponents()

            return MethodHierarchy(
                classHierarchy,
                methodNodes.toTypedArray(),
                methodOwner.toIntArray(),
                classMethodNodeLookup,
                isSourceMethod,
                methodTreeRoots.toIntArray(),
                sourceMethodToMethodTreeIdxLookup,
                Array(methodTreeAdjList.size) { methodTreeAdjList[it].toIntArray() },
                methodTreeToConnectedComponent,
                Array(treeGraphConnectedComponents.size) { treeGraphConnectedComponents[it].toIntArray() }
            )
        }
    }
}