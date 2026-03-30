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
     * Class node methods, indexed by class index, returns internal method indices of the class's methods
     */
    val classNodeMethods: Array<IntArray>,
    /**
     * Lookup for methods in a class, indexed by class index, then by method code, returns internal method index
     */
    val classNodeMethodCodeMethodLookup: Array<Int2IntOpenHashMap>,
    /**
     * Lookup for source methods of a method, indexed by method index, returns source methods' internal indices
     */
    val methodToSource: Array<EntryArray>,
    /**
     * Whether a method is a source method, indexed by internal method index
     *
     * A source method is a method that is either private or has no parent method (not inherited from any parent class).
     * Each source method is the root of a method tree.
     */
    val isSourceMethod: BooleanArray,
    /**
     * All source method indices, indexed by method index, returns source method index
     */
    val sourceMethodIndexLookUp: Int2IntMap,
    /**
     * All source methods (aka. method tree roots), indexed by source method index, returns internal method index
     */
    val sourceMethods: EntryArray,
    /**
     * Method tree indices for each connected component, indexed by source method index,
     * returns indices of method trees in the connected component
     */
    val sourceMethodConnectedComponents: Array<EntryArray>,
    /**
     * Each source method's overrides, indexed by source method index, returns internal method indices of the override methods
     */
    val sourceMethodOverrides: Array<EntryArray>,
    val methodCodeLookup: Object2IntOpenHashMap<String>,
    val methodToMethodCode: IntArray,
    val methodCodeToMethods: Array<EntryArray>
) {
    /**
     * Validate entry using .isValid before using the returned entry
     */
    fun findMethod(className: String, methodName: String, methodDesc: String): Entry {
        val classIdx = classHierarchy.findClass(className)
        if (classIdx == -1) return Entry.INVALID
        return findMethod(classIdx, methodName, methodDesc)
    }

    /**
     * Validate entry using .isValid before using the returned entry
     */
    fun findMethod(classIdx: Int, methodName: String, methodDesc: String): Entry {
        val codename = methodName + methodDesc
        val methodCode = methodCodeLookup.getInt(codename)
        if (methodCode == -1) return Entry.INVALID
        val methodIdx = classNodeMethodCodeMethodLookup[classIdx].get(methodCode)
        return Entry(methodIdx)
    }

    @JvmInline
    value class EntryArray(val array: IntArray) {
        inline val size get() = array.size

        operator fun get(index: Int): Entry {
            return Entry(array[index])
        }

        inline fun forEach(action: (Entry) -> Unit) {
            for (i in array.indices) {
                action(Entry(array[i]))
            }
        }

        inline fun any(predicate: (Entry) -> Boolean): Boolean {
            return array.any { predicate(Entry(it)) }
        }

        companion object {
            val EMPTY = EntryArray(IntArray(0))
        }
    }

    @JvmInline
    value class Entry(val index: Int) {
        val isValid: Boolean get() = index != -1

        context(mh: MethodHierarchy, ch: ClassHierarchy)
        inline val full: String get() = "${owner.name}.$name$desc"

        context(mh: MethodHierarchy)
        inline val name: String get() = mh.methodNodes[index].name

        context(mh: MethodHierarchy)
        inline val desc: String get() = mh.methodNodes[index].desc

        context(mh: MethodHierarchy)
        inline val owner: ClassHierarchy.Entry get() = ClassHierarchy.Entry(mh.methodOwners[index])

        context(mh: MethodHierarchy)
        inline val node: MethodNode get() = mh.methodNodes[index]

        context(mh: MethodHierarchy)
        inline val isSourceMethod: Boolean get() = mh.isSourceMethod[index]

        context(mh: MethodHierarchy)
        inline val connectedComponent: EntryArray
            get() {
                val sourceMethodIdx = mh.sourceMethodIndexLookUp.get(index)
                if (sourceMethodIdx == -1) return EntryArray.EMPTY
                return mh.sourceMethodConnectedComponents[sourceMethodIdx]
            }

        context(mh: MethodHierarchy)
        inline val sourceMethods: EntryArray get() = mh.methodToSource[index]

        /**
         * Get all override methods of this source method, empty if this method is not a source method
         */
        context(mh: MethodHierarchy)
        val overrideMethods: EntryArray
            get() {
                val sourceMethodIdx = mh.sourceMethodIndexLookUp.get(index)
                if (sourceMethodIdx == -1) return EntryArray.EMPTY
                return mh.sourceMethodOverrides[sourceMethodIdx]
            }

        context(mh: MethodHierarchy)
        val methodCode: Int get() = mh.methodToMethodCode[index]

        companion object {
            val INVALID = Entry(-1)
        }
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
            methodCodeLookup.defaultReturnValue(-1)
            val methodCode = IntArray(methodCount)
            val methodAccess = IntArray(methodCount)
            val methodToMethodTree = Array(classHierarchy.realClassCount) {
                Int2ObjectOpenHashMap<IntArraySet>()
            } // Tells a class's method code belongs to which method tree(s)
            val methodCodeToMethods = ObjectArrayList<IntArrayList>()
            val classNodeMethodCodeMethodLookup = Array(classHierarchy.realClassCount) {
                Int2IntOpenHashMap().apply {
                    defaultReturnValue(-1)
                }
            }

            fun assignMethodCodeAndBroadcastToDescendants() {
                for (methodIdx in 0..<methodCount) {
                    val methodNode = methodNodes[methodIdx]
                    val codename = methodNode.name + methodNode.desc
                    val myMethodCode = methodCodeLookup.computeIfAbsent(codename, ToIntFunction {
                        methodCodeToMethods.add(IntArrayList())
                        methodCodeLookup.size
                    })
                    methodCode[methodIdx] = myMethodCode
                    methodAccess[methodIdx] = methodNode.access
                    methodCodeToMethods[myMethodCode].add(methodIdx)
                    val ownerIdx = methodOwner.getInt(methodIdx)
                    classNodeMethodCodeMethodLookup[ownerIdx][myMethodCode] = methodIdx

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
                        if (!methodAccess[myMethod].isPrivate && !methodAccess[myMethod].isStatic) continue
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
                        if (methodAccess[myMethod].isPrivate || methodAccess[myMethod].isStatic) continue
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

            val sourceMethodConnectedComponents = Array(methodTreeRoots.size) { idx ->
                val treeIdx = idx
                val connectedComponentIdx = methodTreeToConnectedComponent[treeIdx]
                val methodTreeIndices = treeGraphConnectedComponents[connectedComponentIdx]
                val methodIndices = IntArray(methodTreeIndices.size)
                for (i in methodTreeIndices.indices) {
                    val methodTreeIdx = methodTreeIndices.getInt(i)
                    methodIndices[i] = methodTreeRoots.getInt(methodTreeIdx)
                }
                EntryArray(methodIndices)
            }

            val sourceMethodOverrides = Array(methodTreeRoots.size) { IntArrayList() }
            val methodToSourceMethod = Array(methodCount) { methodIdx ->
                val list = IntArrayList()
                val methodCode = methodCode[methodIdx]
                val ownerIdx = methodOwner.getInt(methodIdx)
                val methodTreeIndices = methodToMethodTree[ownerIdx][methodCode]
                if (methodTreeIndices != null) {
                    val iterator = methodTreeIndices.intIterator()
                    while (iterator.hasNext()) {
                        val methodTreeIdx = iterator.nextInt()
                        val sourceMethodIdx = methodTreeRoots.getInt(methodTreeIdx)
                        list.add(sourceMethodIdx)
                        sourceMethodOverrides[methodTreeIdx].add(methodIdx)
                    }
                }
                EntryArray(list.toIntArray())
            }

            return MethodHierarchy(
                classHierarchy,
                methodNodes.toTypedArray(),
                methodOwner.toIntArray(),
                Array(classHierarchy.realClassCount) { classToMethod[it].toIntArray() },
                classNodeMethodCodeMethodLookup,
                methodToSourceMethod,
                isSourceMethod,
                sourceMethodToMethodTreeIdxLookup,
                EntryArray(methodTreeRoots.toIntArray()),
                sourceMethodConnectedComponents,
                Array(sourceMethodOverrides.size) { idx ->
                    EntryArray(sourceMethodOverrides[idx].toIntArray())
                },
                methodCodeLookup,
                methodCode,
                Array(methodCodeToMethods.size) { EntryArray(methodCodeToMethods[it].toIntArray()) }
            )
        }
    }
}