package net.spartanb312.grunteon.obfuscator.process.hierarchy2

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.objectweb.asm.tree.ClassNode
import java.util.function.ToIntFunction

class ClassHierarchy(
    val classNodes: Array<ClassNode>,
    val classNames: Array<String>,
    val classNameLookUp: Object2IntOpenHashMap<String>,
    val parents: Array<IntArray>,
    val children: Array<IntArray>,
    val ancestors: Array<IntArray>,
    val descendants: Array<IntArray>,
    val broken: BooleanArray,
    val missingDependencies: BooleanArray,
    val realClassCount: Int,
    val classCount: Int
) {
    companion object {
        const val JAVA_OBJECT = "java/lang/Object"
        val MISSING_CLASSNODE = ClassNode()

        @OptIn(ExperimentalStdlibApi::class)
        @Suppress("UNCHECKED_CAST")
        fun build(inputClassNodes: Collection<ClassNode>, lookup: ((String) -> ClassNode?)? = null): ClassHierarchy {
            val classNodes = ObjectArrayList(inputClassNodes)
            val classNameLookUp = Object2IntOpenHashMap<String>()
            var realClassCount = classNodes.size
            val classNames = ObjectArrayList<String>(realClassCount)

            for (i in 0..<realClassCount) {
                val myName = classNodes[i].name
                classNameLookUp[myName] = i
                assert(classNames.size == i)
                classNames.add(myName)
            }

            assert(realClassCount == classNodes.size)
            assert(classNames.size == classNodes.size)
            assert(classNameLookUp.size == classNodes.size)

            if (lookup != null) {
                fun addRemainingAncestorsRecursively(node: ClassNode) {
                    val superName = node.superName ?: JAVA_OBJECT
                    if (!classNameLookUp.containsKey(superName)) {
                        lookup(superName)?.let {
                            classNodes.add(it)
                            classNameLookUp[superName] = classNodes.size - 1
                            classNames.add(superName)
                            addRemainingAncestorsRecursively(it)
                        }
                    }
                    val interfaces = node.interfaces ?: emptyList()
                    for (j in 0 until interfaces.size) {
                        val interfaceName = interfaces[j]
                        if (!classNameLookUp.containsKey(interfaceName)) {
                            lookup(interfaceName)?.let {
                                classNodes.add(it)
                                classNameLookUp[interfaceName] = classNodes.size - 1
                                classNames.add(interfaceName)
                                addRemainingAncestorsRecursively(it)
                            }
                        }
                    }
                }

                for (i in 0..<classNodes.size) {
                    addRemainingAncestorsRecursively(classNodes[i])
                }

                realClassCount = classNodes.size
                assert(realClassCount >= inputClassNodes.size)
                assert(classNames.size == classNodes.size)
                assert(classNameLookUp.size == classNodes.size)
            }


            var classCount = realClassCount
            val phantomClassHandle = ToIntFunction<String> {
                val newIdx = classCount++
                assert(newIdx == classNames.size)
                classNames.add(it)
                newIdx
            }

            var parents = arrayOfNulls<IntArray>(realClassCount) as Array<IntArray>

            for (i in 0..<realClassCount) {
                val classNode = classNodes[i]
                if (classNode.name == JAVA_OBJECT) {
                    parents[i] = IntArray(0)
                    continue
                }
                val interfaces = classNode.interfaces ?: emptyList()
                val parentCount = 1 + interfaces.size
                val parentArray = IntArray(parentCount)
                parentArray[0] = classNameLookUp.computeIfAbsent(classNode.superName ?: JAVA_OBJECT, phantomClassHandle)
                for (j in 0 until interfaces.size) {
                    parentArray[j + 1] = classNameLookUp.computeIfAbsent(interfaces[j], phantomClassHandle)
                }
                parents[i] = parentArray.distinct().toIntArray()
            }

            assert(classCount >= realClassCount)
            assert(classNames.size == classCount)

            val emptyIntArray = IntArray(0)
            parents = parents.copyOf(classCount) { emptyIntArray }

            val children = Array(classCount) { IntArrayList() }
            val ancestors = Array(classCount) { IntArrayList(parents[it]) }
            val visited = BooleanArray(classCount)

            fun dfs(myIdx: Int) {
                if (visited[myIdx]) return
                visited[myIdx] = true
                val myParents = parents[myIdx]
                val myAncestors = ancestors[myIdx]
                for (myParentIdx in 0..<myParents.size) {
                    val parentIdx = myParents[myParentIdx]
                    dfs(parentIdx)
                    children[parentIdx].add(myIdx)
                    myAncestors.addAll(ancestors[parentIdx])
                }
            }
            for (i in 0..<classCount) {
                dfs(i)
            }
            val descendants = Array(classCount) { IntArrayList(children[it]) }

            for (i in 0..<classCount) {
                val myAncestors = ancestors[i]
                for (j in 0..<myAncestors.size) {
                    val ancestorIdx = myAncestors.getInt(j)
                    descendants[ancestorIdx].add(i)
                }
            }

            val broken = BooleanArray(classCount)
            val missingDependencies = BooleanArray(classCount)

            val finalChildren = arrayOfNulls<IntArray>(classCount) as Array<IntArray>
            val finalAncestors = arrayOfNulls<IntArray>(classCount) as Array<IntArray>
            val finalDescendants = arrayOfNulls<IntArray>(classCount) as Array<IntArray>

            for (i in 0..<classCount) {
                finalChildren[i] = children[i].distinct().toIntArray()
                finalAncestors[i] = ancestors[i].distinct().toIntArray()
                finalDescendants[i] = descendants[i].distinct().toIntArray()
                broken[i] = classNodes[i] == MISSING_CLASSNODE
                missingDependencies[i] = broken[i] || ancestors[i].any { broken[it] }
            }

            return ClassHierarchy(
                classNodes.toTypedArray(),
                classNames.toTypedArray(),
                classNameLookUp,
                parents,
                finalChildren,
                finalAncestors,
                finalDescendants,
                broken,
                missingDependencies,
                realClassCount,
                classCount
            )
        }
    }
}
