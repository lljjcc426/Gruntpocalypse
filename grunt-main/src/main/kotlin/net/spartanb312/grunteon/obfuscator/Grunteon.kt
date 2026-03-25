package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.config.manager.ConfigGroup
import net.spartanb312.grunteon.obfuscator.pipeline.ProcessPipeline
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.resource.JarDumper
import net.spartanb312.grunteon.obfuscator.process.resource.JarResources
import net.spartanb312.grunteon.obfuscator.process.resource.WorkResources
import net.spartanb312.grunteon.obfuscator.process.transformers.TestTransformer
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number.NumberBasicEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.ClassRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.LocalVarRenamer
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.filters.buildClassNamePredicates
import java.io.File
import kotlin.io.path.Path
import kotlin.system.measureTimeMillis

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

    measureTimeMillis {
        val emptyConfig = ConfigGroup()
        val instance = emptyConfig.runPipeline(
            TestTransformer(),
            NumberBasicEncrypt(),
            LocalVarRenamer(),
            TestTransformer(),
        )
        instance.execute()
    }.also { println("$it ms") }
}

fun ConfigGroup.runPipeline(vararg transformers: Transformer<*>): Grunteon {
    return Grunteon(this, ProcessPipeline(*transformers).apply { parseConfig(this@runPipeline) })
}

// Grunteon process instance
class Grunteon(
    val configGroup: ConfigGroup,
    val pipeline: ProcessPipeline
) {

    /**
     * Resources
     */
    lateinit var input: JarResources
    lateinit var output: JarDumper
    lateinit var workRes: WorkResources
    inline val classes get() = input.classes
    inline val libraries get() = workRes.libraries
    inline val allClasses get() = workRes.allClasses

    fun execute() {
        Logger.info("Executing obfuscating job...")

        // Reading input jar
        input = JarResources(Path("input.jar"))
        input.readInput()
        // Reading working res
        workRes = WorkResources(input)
        workRes.readLibs(listOf("libs/"))//configGroup.libs)
        // Output dumper
        output = JarDumper(
            jarResources = input,
            outputFile = File("obftest/AT/engine/boar-main.jar"),
            forceComputeMax = configGroup.forceComputeMax,
            missingCheck = configGroup.missingCheck,
            corruptHeader = configGroup.corruptHeaders,
            corruptCRC32 = configGroup.corruptCRC32,
            removeTimestamps = configGroup.removeTimeStamps,
            compressionLevel = configGroup.compressionLevel,
            archiveComment = configGroup.archiveComment,
            fileRemovePrefix = configGroup.fileRemovePrefix,
            fileRemoveSuffix = configGroup.fileRemoveSuffix,
        )

        // TODO: Profiler
        context(workRes, input) {
            contextOf<Grunteon>().pipeline.execute()
        }

        output.dumpJar()
    }

    val mixinExPredicate = buildClassNamePredicates(configGroup.mixinExclusions)
    val globalExPredicate = buildClassNamePredicates(configGroup.exclusions)

}
