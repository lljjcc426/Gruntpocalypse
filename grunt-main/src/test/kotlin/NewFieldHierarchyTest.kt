import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.config.manager.ConfigGroup
import net.spartanb312.grunteon.obfuscator.pipeline.ProcessPipeline
import net.spartanb312.grunteon.obfuscator.process.hierarchy.HeavyHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy.Hierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.FieldHierarchy
import net.spartanb312.grunteon.testcase.Asserts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NewFieldHierarchyTest {
    @Test
    fun methodH() {
        val instance1 = readTestClasses(Asserts::class.java)
        val old = HeavyHierarchy(instance1)
        old.buildClass()
        old.buildField()
        val instance2 = readTestClasses(Asserts::class.java)
        val new = FieldHierarchy.build(
            ClassHierarchy.build(
                instance2.workRes.inputClassCollection,
                instance2.workRes::getClassNode
            )
        )
        checkField(old, new)
    }

    @Test
    fun methodHFastUtils() {
        val instance1 = readTestClasses(ObjectArrayList::class.java)
        val old = HeavyHierarchy(instance1)
        old.buildClass()
        old.buildField()
        val instance2 = readTestClasses(ObjectArrayList::class.java)
        val new = FieldHierarchy.build(
            ClassHierarchy.build(
                instance2.workRes.inputClassCollection,
                instance2.workRes::getClassNode
            )
        )
        checkField(old, new)
    }

    @Test
    fun methodHAT() {
        val instance1 = Grunteon(ConfigGroup(), ProcessPipeline())
        instance1.init()
        val old = HeavyHierarchy(instance1)
        old.buildClass()
        old.buildMethod()
        val instance2 = Grunteon(ConfigGroup(), ProcessPipeline())
        instance2.init()
        val new = FieldHierarchy.build(
            ClassHierarchy.build(
                instance2.workRes.inputClassCollection,
                instance2.workRes::getClassNode
            )
        )
        checkField(old, new)
    }

    private fun checkField(
        old: Hierarchy,
        new: FieldHierarchy
    ) {
        old.instance.workRes.inputClassCollection.forEach { node ->
            val classInfo = old.classInfos[node.name]!!
            val classIdx = new.classHierarchy.classNameLookUp.getInt(classInfo.name)
            classInfo.fields.forEach { fieldInfo ->
                val fieldName = fieldInfo.name
                val fieldDesc = fieldInfo.fieldNode.desc
                val fieldIdx = new.findField(classIdx, fieldName, fieldDesc)
                assertTrue(fieldIdx != -1, "Field not found for ${classInfo.name}.$fieldName$fieldDesc")
                assertEquals(
                    fieldInfo.isSourceField,
                    new.isSourceField(fieldIdx),
                    "Source method flag mismatch for ${classInfo.name}.$fieldName$fieldDesc"
                )
            }
        }
    }
}