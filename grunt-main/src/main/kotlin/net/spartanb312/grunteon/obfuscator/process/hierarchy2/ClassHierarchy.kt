package net.spartanb312.grunteon.obfuscator.process.hierarchy2

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.grunteon.obfuscator.Grunteon
import org.objectweb.asm.tree.ClassNode
import java.util.function.ToIntFunction

class ClassHierarchy {
    var classNodes = emptyArray<ClassNode>(); private set
    var classNames = emptyArray<String>(); private set
    val classNameLookUp = Object2IntOpenHashMap<String>()
    var parents = emptyArray<IntArray>(); private set
    var children = emptyArray<IntArray>(); private set
    var ancestors = emptyArray<IntArray>(); private set
    var descendants = emptyArray<IntArray>(); private set
    var broken = BooleanArray(0); private set
    var missingDependencies = BooleanArray(0); private set
    var realClassCount = 0; private set
    var classCount = 0; private set

    @OptIn(ExperimentalStdlibApi::class)
    @Suppress("UNCHECKED_CAST", "EmptyRange")
    fun init(instance: Grunteon) {
        val classNodesTemp = ObjectArrayList(instance.allClasses)
        realClassCount = classNodesTemp.size
        classCount = realClassCount

        val parentsTemp = arrayOfNulls<IntArray>(classCount) as Array<IntArray>
        val classNamesTemp = ObjectArrayList<String>(classCount)

        for (i in 0..<realClassCount) {
            val myName = classNodesTemp[i].name
            classNameLookUp[myName] = i
            assert(classNamesTemp.size == i)
            classNamesTemp.add(myName)
        }
        assert(classCount == classNodesTemp.size)
        assert(realClassCount == classNodesTemp.size)
        assert(classNamesTemp.size == classNodesTemp.size)
        assert(classNameLookUp.size == classNodesTemp.size)

        val phantomClassHandle = ToIntFunction<String> {
            val newIdx = classCount++
            assert(newIdx == classNamesTemp.size)
            classNodesTemp.add(MISSING_CLASSNODE)
            classNamesTemp.add(it)
            newIdx
        }

        for (i in 0..<realClassCount) {
            val classNode = classNodesTemp[i]
            if (classNode.name == JAVA_OBJECT) {
                parentsTemp[i] = IntArray(0)
                continue
            }
            val interfaces = classNode.interfaces ?: emptyList()
            val parentCount = 1 + interfaces.size
            val parentArray = IntArray(parentCount)
            parentArray[0] = classNameLookUp.computeIfAbsent(classNode.superName ?: JAVA_OBJECT, phantomClassHandle)
            for (j in 0 until interfaces.size) {
                parentArray[j + 1] = classNameLookUp.computeIfAbsent(interfaces[j], phantomClassHandle)
            }
            parentsTemp[i] = parentArray.distinct().toIntArray()
        }

        classNames = classNamesTemp.toTypedArray()

        assert(realClassCount >= classCount)
        assert(classNames.size == realClassCount)

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

        broken = BooleanArray(classCount)
        missingDependencies = BooleanArray(classCount)

        for (i in 0..<classCount) {
            children[i] = childrenTemp[i].distinct().toIntArray()
            ancestors[i] = ancestorsTemp[i].distinct().toIntArray()
            descendants[i] = descendantsTemp[i].distinct().toIntArray()
            broken[i] = classNodesTemp[i] == MISSING_CLASSNODE
            missingDependencies[i] = broken[i] || ancestors[i].any { broken[it] }
        }
    }

    companion object {
        const val JAVA_OBJECT = "java/lang/Object"
        val MISSING_CLASSNODE = ClassNode()
    }
}