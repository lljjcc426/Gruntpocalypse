import net.spartanb312.grunteon.obfuscator.process.hierarchy.Hierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.ClassHierarchy
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class NewClassHierarchyTest {
    @Test
    fun classH() {
        val old = Hierarchy()
        old.buildClass(readTestClasses())
        val new = ClassHierarchy.build(readTestClasses())
        checkClass(old, new)
    }

    @Test
    fun classHShuffle() {
        val old = Hierarchy()
        old.buildClass(readTestClasses())
        val new = ClassHierarchy.build(readTestClasses().shuffled(Random(1145141919810)))
        checkClass(old, new)
    }

    private fun checkClass(
        old: Hierarchy,
        new: ClassHierarchy
    ) {
        old.classInfos.values.forEach { info ->
            val newIdx = new.classNameLookUp.getInt(info.name)
            assertEquals(info.name, new.classNames[newIdx], "Class name mismatch for ${info.name}")
            var expectedParents: Set<String> = info.parentsNames
            if (expectedParents.isEmpty() && info.name != ClassHierarchy.JAVA_OBJECT) expectedParents =
                setOf(ClassHierarchy.JAVA_OBJECT)
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