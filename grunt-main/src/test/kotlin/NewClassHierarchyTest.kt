import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.grunteon.obfuscator.process.hierarchy.Hierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.ClassHierarchy
import kotlin.test.Test
import kotlin.test.assertEquals

class NewClassHierarchyTest {
    @Test
    fun classH() {
        val instance1 = readTestClasses(net.spartanb312.grunteon.testcase.Asserts::class.java)
        val old = Hierarchy(instance1)
        old.buildClassLess()
        val instance2 = readTestClasses(net.spartanb312.grunteon.testcase.Asserts::class.java)
        val new = ClassHierarchy.build(instance2.classes.values, instance2.workRes::getClassNode)
        checkClass(old, new)
    }

    @Test
    fun classHFastUtils() {
        val instance1 = readTestClasses(ObjectArrayList::class.java)
        val old = Hierarchy(instance1)
        old.buildClassLess()
        val instance2 = readTestClasses(ObjectArrayList::class.java)
        val new = ClassHierarchy.build(instance2.classes.values, instance2.workRes::getClassNode)
        checkClass(old, new)
    }

    private fun checkClass(
        old: Hierarchy,
        new: ClassHierarchy
    ) {
        old.instance.classes.values.forEach { node ->
            val info = old.classInfos[node.name]!!
            val newIdx = new.classNameLookUp.getInt(info.name)
            assertEquals(info.name, new.classNames[newIdx], "Class name mismatch for ${info.name}")
            var expectedParents: Set<String> = info.parentsNames
            if (expectedParents.isEmpty()) expectedParents = setOf(ClassHierarchy.JAVA_OBJECT)
            assertEquals(
                expectedParents,
                new.parents[newIdx].map { idx -> new.classNames[idx] }.toSet(),
                "Parents mismatch for ${info.name}"
            )

            assertEquals(
                info.parents.map { it.name }.toSet(),
                new.ancestors[newIdx].map { idx -> new.classNames[idx] }.toSet(),
                "Ancestors mismatch for ${info.name}"
            )

            val expectedDescendants = info.children.map { it.name }.toSet()
            val actualDescendants = new.descendants[newIdx].map { idx -> new.classNames[idx] }.toSet()
            assertEquals(
                expectedDescendants,
                actualDescendants,
                "Descendants mismatch for ${info.name}, expected - actual = ${expectedDescendants - actualDescendants}, actual - expected = ${actualDescendants - expectedDescendants}"
            )
        }
    }
}