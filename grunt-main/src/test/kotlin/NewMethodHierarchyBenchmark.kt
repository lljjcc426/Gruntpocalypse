import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.config.manager.ConfigGroup
import net.spartanb312.grunteon.obfuscator.pipeline.ProcessPipeline
import net.spartanb312.grunteon.obfuscator.process.hierarchy.HeavyHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.MethodHierarchy
import kotlin.time.DurationUnit
import kotlin.time.measureTime

fun main() {
    val instance = Grunteon(ConfigGroup(), ProcessPipeline())
    instance.init()

    val old = false

    if (old) {
        measureTime {
            HeavyHierarchy(instance).apply {
                buildClass()
                buildMethod()
            }
        }.also { println("Old Cold: %.2f ms".format(it.toDouble(DurationUnit.MILLISECONDS))) }

        repeat(20) {
            HeavyHierarchy(instance).apply {
                buildClass()
                buildMethod()
            }
        }
        measureTime {
            repeat(5) {
                HeavyHierarchy(instance).apply {
                    buildClass()
                    buildMethod()
                }
            }
        }.also { println("Old: %.2f ms".format(it.toDouble(DurationUnit.MILLISECONDS) / 5.0)) }
    } else {
        measureTime {
            MethodHierarchy.build(ClassHierarchy.build(instance.allClasses, instance.workRes::getClassNode))
        }.also { println("New Cold: %.2f ms".format(it.toDouble(DurationUnit.MILLISECONDS))) }
        repeat(20) {
            MethodHierarchy.build(ClassHierarchy.build(instance.allClasses, instance.workRes::getClassNode))
        }
        measureTime {
            repeat(5) {
                MethodHierarchy.build(ClassHierarchy.build(instance.allClasses, instance.workRes::getClassNode))
            }
        }.also { println("New: %.2f ms".format(it.toDouble(DurationUnit.MILLISECONDS) / 5.0)) }
    }
}