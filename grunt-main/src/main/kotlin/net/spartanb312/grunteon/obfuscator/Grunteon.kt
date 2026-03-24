package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.config.manager.ConfigGroup
import net.spartanb312.grunteon.obfuscator.pipeline.ProcessPipeline
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.resource.JarResources
import net.spartanb312.grunteon.obfuscator.process.resource.WorkResources
import net.spartanb312.grunteon.obfuscator.process.transformers.TestTransformer
import net.spartanb312.grunteon.obfuscator.util.Logger
import java.io.File

/**
 * Grunteon
 * A JVM bytecode obfuscator
 * 3rd generation of Grunt
 */
const val VERSION = "3.0.0"
const val SUBTITLE = "build 260324"
const val GITHUB = "https://github.com/SpartanB312/Grunt"

fun main() {

    println(
        """
             ________  __________   ____ ___   _______    ___________
            /  _____/  \______   \ |    |   \  \      \   \__    ___/
           /   \  ___   |       _/ |    |   /  /   |   \    |    |   
           \    \_\  \  |    |   \ |    |  /  /    |    \   |    |   
            \______  /  |____|_  / |______/   \____|__  /   |____|   
        """.trimIndent()
    )
    println("==========================================================")
    println(" Grunteon $VERSION [${SUBTITLE}]")
    println(" GitHub: $GITHUB")
    println("==========================================================")

    Logger.info("Initializing obfuscator...")

    // TODO: Module scan
    // TODO: Module initialize

    // TODO: Plugin scan
    // TODO: Plugin initialize

    val dummyConfig = ConfigGroup()
    val instance = dummyConfig.runConfig(
        TestTransformer(),
        TestTransformer(),
        TestTransformer(),
    )
    instance.execute()
}

fun ConfigGroup.runConfig(vararg transformers: Transformer<*>): Grunteon {
    return Grunteon(this, ProcessPipeline(*transformers))
}

// Grunteon process instance
class Grunteon(
    val configGroup: ConfigGroup,
    val pipeline: ProcessPipeline
) {

    fun execute() {
        Logger.info("Executing obfuscating job...")

        // Reading input jar
        val inputJar = JarResources(File(configGroup.input))
        // Reading working res
        val res = WorkResources(inputJar)
        res.readLibs(configGroup.libs)

        // TODO: Profiler
        context(res, inputJar) {
            contextOf<Grunteon>().pipeline.execute()
        }

        // TODO: Output
    }

}
