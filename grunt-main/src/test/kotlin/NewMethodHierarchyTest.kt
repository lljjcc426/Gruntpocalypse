import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.grunteon.obfuscator.process.hierarchy.HeavyHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy.Hierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.MethodHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.MethodNodeKey
import net.spartanb312.grunteon.obfuscator.util.extensions.isInitializer
import kotlin.streams.asSequence
import kotlin.test.Test
import kotlin.test.assertEquals

class NewMethodHierarchyTest {
    @Test
    fun methodH() {
        val instance1 = readTestClasses(net.spartanb312.grunteon.testcase.Asserts::class.java)
        val old = HeavyHierarchy(instance1)
        old.buildClassLess()
        old.buildMethod()
        val instance2 = readTestClasses(net.spartanb312.grunteon.testcase.Asserts::class.java)
        val new = MethodHierarchy.build(ClassHierarchy.build(instance2.classes.values, instance2.workRes::getClassNode))
        checkMethod(old, new)
    }

    @Test
    fun methodHFastUtils() {
        val instance1 = readTestClasses(ObjectArrayList::class.java)
        val old = HeavyHierarchy(instance1)
        old.buildClassLess()
        old.buildMethod()
        val instance2 = readTestClasses(ObjectArrayList::class.java)
        val new = MethodHierarchy.build(ClassHierarchy.build(instance2.classes.values, instance2.workRes::getClassNode))
        checkMethod(old, new)
    }

    private fun checkMethod(
        old: Hierarchy,
        new: MethodHierarchy
    ) {
        new.treeCCToTreeIdx.forEach {
            assert(it.distinct().size == it.size)
        }

        old.instance.classes.values.forEach { node ->
            val classInfo = old.classInfos[node.name]!!
            val classIdx = new.classHierarchy.classNameLookUp.getInt(classInfo.name)
            classInfo.methods.forEach { methodInfo ->
                if (methodInfo.virtual) return@forEach
                if (methodInfo.methodNode.isInitializer) return@forEach
                val key = MethodNodeKey(methodInfo.name, methodInfo.desc, methodInfo.methodNode.access)
                val methodIdx = new.classNodeMethodLookup[classIdx].getInt(key)
                assertEquals(
                    methodInfo.isSourceMethod,
                    new.sourceMethod[methodIdx],
                    "Source method flag mismatch for ${classInfo.name}.${methodInfo.name}${methodInfo.desc}"
                )

                if (!methodInfo.isSourceMethod) {
                    assert(methodInfo.competitors.isEmpty()) { "WTF" }
                    assert(methodInfo.relatedMethods.isEmpty()) { "WTF" }
                    return@forEach
                }
                val methodTreeIdx = new.sourceMethodToMethodTreeIdxLookup[methodIdx]
                assert(methodTreeIdx != -1) { "Method ${classInfo.name}.${methodInfo.name}${methodInfo.desc} is not a method tree root" }
                val newCompetitors = new.methodTreeAdjList[methodTreeIdx].stream().asSequence()
                    .map {
                        val methodIdx = new.methodTreeRoots[it]
                        val methodOwner = new.classHierarchy.classNodes[new.methodOwners[methodIdx]]
                        val methodNode = new.methodNodes[methodIdx]
                        "${methodOwner.name}.${methodNode.name}${methodNode.desc}"
                    }.toSet()
//                assertEquals(
//                    methodInfo.competitors.map { it.full }.toSet(),
//                    newCompetitors,
//                    "Competitors mismatch for ${classInfo.name}.${methodInfo.name}${methodInfo.desc}"
//                )
                var newRelated =
                    new.treeCCToTreeIdx[new.methodTreeToConnectedComponent[methodTreeIdx]].stream().asSequence()
                        .map { treeIdx ->
                            val methodIdx = new.methodTreeRoots[treeIdx]
                            val methodOwner = new.classHierarchy.classNodes[new.methodOwners[methodIdx]]
                            val methodNode = new.methodNodes[methodIdx]
                            "${methodOwner.name}.${methodNode.name}${methodNode.desc}"
                        }.toSet()
                if (newRelated.size == 1) {
                    newRelated = emptySet()
                }
                assertEquals(
                    methodInfo.relatedMethods.map { it.full }.toSet(),
                    newRelated,
                    "Related methods mismatch for ${classInfo.name}.${methodInfo.name}${methodInfo.desc}"
                )
            }
        }
    }
}