import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.config.manager.ConfigGroup
import net.spartanb312.grunteon.obfuscator.pipeline.ProcessPipeline
import net.spartanb312.grunteon.obfuscator.process.hierarchy.Hierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.ClassHierarchy
import kotlin.time.DurationUnit
import kotlin.time.measureTime

fun main() {
    val instance = Grunteon(ConfigGroup(), ProcessPipeline())
    instance.init()

    val old = false

    if (old) {
        measureTime {
            Hierarchy(instance).apply {
                buildClass()
            }
        }.also { println("Old Cold: %.2f ms".format(it.toDouble(DurationUnit.MILLISECONDS))) }

        repeat(100) {
            Hierarchy(instance).apply {
                buildClass()
            }
        }
        measureTime {
            repeat(10) {
                Hierarchy(instance).apply {
                    buildClass()
                }
            }
        }.also { println("Old: %.2f ms".format(it.toDouble(DurationUnit.MILLISECONDS) / 10.0)) }
    } else {
        measureTime {
            ClassHierarchy.build(instance.allClasses, instance.workRes::getClassNode)
        }.also { println("New Cold: %.2f ms".format(it.toDouble(DurationUnit.MILLISECONDS))) }
        repeat(100) {
            ClassHierarchy.build(instance.allClasses, instance.workRes::getClassNode)
        }
        measureTime {
            repeat(10) {
                ClassHierarchy.build(instance.allClasses, instance.workRes::getClassNode)
            }
        }.also { println("New: %.2f ms".format(it.toDouble(DurationUnit.MILLISECONDS) / 10.0)) }
    }
}