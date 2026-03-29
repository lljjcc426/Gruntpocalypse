package net.spartanb312.grunteon.obfuscator.process.hierarchy2

import it.unimi.dsi.fastutil.ints.*
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.grunteon.obfuscator.util.extensions.isInitializer
import org.objectweb.asm.tree.MethodNode
import java.util.function.ToIntFunction

/**
 * Key for method lookup, contains method name, desc and access flags (to distinguish private/protected/public)
 */
data class MethodNodeKey(
    val name: String,
    val desc: String
)

/**
 * Method hierarchy built on top of class hierarchy, data is stored in parallel array for better performance.
 *
 * It uses internal method indices to read data
 */
class MethodHierarchy(
    /**
     * Class hierarchy this method hierarchy is built on
     */
    val classHierarchy: ClassHierarchy,
    /**
     * All method nodes in this method hierarchy, indexed by internal method index
     */
    val methodNodes: Array<MethodNode>,
    /**
     * Owner class index for each method, indexed by internal method index
     */
    val methodOwners: IntArray,
    /**
     * Lookup for methods in a class, indexed by class index, then by method key, returns internal method index
     */
    val classNodeMethodLookup: Array<Object2IntOpenHashMap<MethodNodeKey>>,
    /**
     * Whether a method is a source method, indexed by internal method index
     *
     * A source method is a method that is either private or has no parent method (not inherited from any parent class).
     * Each source method is the root of a method tree.
     */
    val sourceMethod: BooleanArray,
    /**
     * All method tree roots (aka. source methods), indexed by method tree index, returns internal method index
     */
    val methodTreeRoots: IntArray,
    /**
     * Lookup for source method to method tree index, indexed by internal method index, returns method tree index
     */
    val sourceMethodToMethodTreeIdxLookup: Int2IntOpenHashMap,
    /**
     * Adjacency list for method tree graph, indexed by method tree index, returns adjacent method tree indices
     */
    val methodTreeAdjList: Array<IntArray>,
    /**
     * Connected component index for each method tree, indexed by method tree index
     */
    val methodTreeToConnectedComponent: IntArray,
    /**
     * Method tree indices for each connected component, indexed by connected component index,
     * returns indices of method trees in the connected component
     */
    val treeCCToTreeIdx: Array<IntArray>
) {
    fun findMethod(className: String, methodName: String, methodDesc: String): Int {
        val classIdx = classHierarchy.findClass(className)
        if (classIdx == -1) return -1
        return findMethod(classIdx, methodName, methodDesc)
    }

    fun findMethod(classIdx: Int, methodName: String, methodDesc: String): Int {
        val methodLookup = classNodeMethodLookup[classIdx]
        val methodKey = MethodNodeKey(methodName, methodDesc)
        return methodLookup.getInt(methodKey)
    }

    /**
     * Check if a method is a source method, indexed by internal method index
     *
     * A source method is a method that is either private or has no parent method (not inherited from any parent class).
     */
    fun isSourceMethod(methodIdx: Int): Boolean {
        return sourceMethod[methodIdx]
    }

    /**
     * Get a source method's method tree connected component, indexed by internal method index
     *
     * Returns internal method indices of all methods in the same connected component as the source method
     * Connected components are source methods that their owner class have common descendents
     */
    fun getSourceMethodTreeCCs(methodIdx: Int): IntArray? {
        val methodTreeIdx = sourceMethodToMethodTreeIdxLookup.get(methodIdx)
        if (methodTreeIdx == -1) return null
        val ccIdx = methodTreeToConnectedComponent[methodTreeIdx]
        if (ccIdx == -1) return null
        return treeCCToTreeIdx[ccIdx]
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
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
                        val key = MethodNodeKey(methodNode.name, methodNode.desc)
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
                    for (i in descendents.indices) {
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
                    for (j in myMethods.indices) {
                        val myMethod = myMethodArray[j]
                        if (!methodAccess[myMethod].isPrivate) continue
                        setSource(myMethod)
                    }

                    val parentIndices = classHierarchy.parents[classIdx]
                    val allParentMethodCodeBits = IntOpenHashSet()
                    for (i in parentIndices.indices) {
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