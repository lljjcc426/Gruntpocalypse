import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.config.manager.ConfigGroup
import net.spartanb312.grunteon.obfuscator.pipeline.ProcessPipeline
import net.spartanb312.grunteon.obfuscator.process.hierarchy.Hierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.ClassHierarchy
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class NewHierarchyTest {
    @Test
    fun light() {
        val emptyConfig = ConfigGroup()
        val instance = Grunteon(emptyConfig, ProcessPipeline())
        instance.init()
        val light = Hierarchy(instance)
        light.buildClass()
        val newLight = ClassHierarchy()
        newLight.init(instance)
        checkLight(light, newLight)
    }

    @Test
    fun lightShuffle() {
        val emptyConfig = ConfigGroup()
        val instance = Grunteon(emptyConfig, ProcessPipeline())
        instance.init()
        val light = Hierarchy(instance)
        light.buildClass()
        instance.allClasses.shuffle(Random(1145141919810L))
        val newLight = ClassHierarchy()
        newLight.init(instance)
        checkLight(light, newLight)
    }

    private fun checkLight(
        light: Hierarchy,
        newLight: ClassHierarchy
    ) {
        light.classInfos.values.forEach { info ->
            val newIdx = newLight.classNameLookUp.getInt(info.name)
            assertEquals(info.name, newLight.classNames[newIdx], "Class name mismatch for ${info.name}")
            var expectedParents: Set<String> = info.parentsNames
            if (expectedParents.isEmpty() && info.name != ClassHierarchy.JAVA_OBJECT) expectedParents =
                setOf(ClassHierarchy.JAVA_OBJECT)
            assertEquals(
                expectedParents,
                newLight.parents[newIdx].map { idx -> newLight.classNames[idx] }.toSet(),
                "Parents mismatch for ${info.name}"
            )

            assertEquals(
                info.parents.map { it.name }.toSet(),
                newLight.ancestors[newIdx].map { idx -> newLight.classNames[idx] }.toSet(),
                "Ancestors mismatch for ${info.name}"
            )

            val expectedDescendants = info.children.map { it.name }.toSet()
            val actualDescendants = newLight.descendants[newIdx].map { idx -> newLight.classNames[idx] }.toSet()
            assertEquals(
                expectedDescendants,
                actualDescendants,
                "Descendants mismatch for ${info.name}, expected - actual = ${expectedDescendants - actualDescendants}, actual - expected = ${actualDescendants - expectedDescendants}"
            )
        }
    }
}