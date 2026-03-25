package net.spartanb312.grunteon.obfuscator.process.hierarchy2

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.spartanb312.grunteon.obfuscator.Grunteon
import org.objectweb.asm.tree.ClassNode
import java.util.function.ToIntFunction

class ClassHierarchy {
    var classNodes = emptyArray<ClassNode>(); private set
    val classNameLookUp = Object2IntOpenHashMap<String>()
    var parents = emptyArray<IntArray>(); private set
    var children = emptyArray<IntArray>(); private set
    var ancestors = emptyArray<IntArray>(); private set
    var descendants = emptyArray<IntArray>(); private set
    var realClassCount = 0; private set
    var classCount = 0; private set

    @OptIn(ExperimentalStdlibApi::class)
    @Suppress("UNCHECKED_CAST", "EmptyRange")
    fun init(instance: Grunteon) {
        classNodes = instance.allClasses.toTypedArray()
        realClassCount = classNodes.size
        classCount = realClassCount

        val parentsTemp = arrayOfNulls<IntArray>(classCount) as Array<IntArray>

        for (i in 0..<realClassCount) {
            classNameLookUp[classNodes[i].name] = i
        }

        for (i in 0..<realClassCount) {
            val classNode = classNodes[i]
            val interfaces = classNode.interfaces ?: emptyList()
            val parentCount = 1 + interfaces.size
            val parentArray = IntArray(parentCount)
            parentArray[0] = classNameLookUp.computeIfAbsent(classNode.superName ?: JAVA_OBJECT, ToIntFunction {
                classCount++
            })
            for (j in 0 until interfaces.size) {
                parentArray[j + 1] = classNameLookUp.computeIfAbsent(interfaces[j], ToIntFunction {
                    classCount++
                })
            }
            parentsTemp[i] = parentArray.distinct().toIntArray()
        }

        val emptyIntArray = IntArray(0)
        parents = parentsTemp.copyOf(classCount) { emptyIntArray }
        children = arrayOfNulls<IntArray>(classCount) as Array<IntArray>
        ancestors = arrayOfNulls<IntArray>(classCount) as Array<IntArray>
        descendants = arrayOfNulls<IntArray>(classCount) as Array<IntArray>

        val childrenTemp = Array(classCount) { IntArrayList() }
        val ancestorsTemp = Array(classCount) { IntArrayList(parents[it]) }
        val visited = BooleanArray(classCount)

        fun dfs(myIdx: Int) {
            if (visited[myIdx]) return
            visited[myIdx] = true
            val myParents = parents[myIdx]
            val myAncestors = ancestorsTemp[myIdx]
            for (myParentIdx in 0..<myParents.size) {
                val parentIdx = myParents[myParentIdx]
                dfs(parentIdx)
                childrenTemp[parentIdx].add(myIdx)
                myAncestors.addAll(ancestorsTemp[parentIdx])
            }
        }
        for (i in 0..<classCount) {
            dfs(i)
        }
        val descendantsTemp = Array(classCount) { IntArrayList(childrenTemp[it]) }

        for (i in 0..<classCount) {
            val myAncestors = ancestorsTemp[i]
            for (j in 0..<myAncestors.size) {
                val ancestorIdx = myAncestors.getInt(j)
                descendantsTemp[ancestorIdx].add(i)
            }
        }

        for (i in 0..<classCount) {
            children[i] = childrenTemp[i].distinct().toIntArray()
            ancestors[i] = ancestorsTemp[i].distinct().toIntArray()
            descendants[i] = descendantsTemp[i].distinct().toIntArray()
        }
    }

    companion object {
        const val JAVA_OBJECT = "java/lang/Object"
    }
}