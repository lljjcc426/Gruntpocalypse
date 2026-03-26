import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.grunteon.obfuscator.process.hierarchy.HeavyHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy.Hierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.MethodHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.MethodNodeKey
import net.spartanb312.grunteon.obfuscator.util.extensions.isInitializer
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
        old.instance.classes.values.forEach { node ->
            val classInfo = old.classInfos[node.name]!!
            val classIdx = new.classHierarchy.classNameLookUp.getInt(classInfo.name)
            classInfo.methods.forEach {
                if (it.virtual) return@forEach
                if (it.methodNode.isInitializer) return@forEach
                val key = MethodNodeKey(it.name, it.desc, it.methodNode.access)
                val methodIdx = new.classNodeMethodLookup[classIdx].getInt(key)
                assertEquals(
                    it.isSourceMethod,
                    new.sourceMethod[methodIdx],
                    "Source method flag mismatch for ${classInfo.name}.${it.name}${it.desc}"
                )
            }
        }
    }
}