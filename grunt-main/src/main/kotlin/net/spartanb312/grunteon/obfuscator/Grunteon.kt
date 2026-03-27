package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.config.manager.ConfigGroup
import net.spartanb312.grunteon.obfuscator.pipeline.ProcessPipeline
import net.spartanb312.grunteon.obfuscator.process.MappingApplier
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.resource.JarDumper
import net.spartanb312.grunteon.obfuscator.process.resource.WorkResources
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number.NumberBasicEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.ClassShrink
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.DeadCodeRemove
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.EnumOptimize
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.KotlinClassShrink
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.ClassRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.LocalVarRenamer
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.cryptography.toRandom
import net.spartanb312.grunteon.obfuscator.util.filters.buildClassNamePredicates
import net.spartanb312.grunteon.obfuscator.util.logging.SimpleLogger
import java.io.File
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.walk
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Grunteon
 * A JVM bytecode obfuscator
 * 3rd generation of Grunt
 */
const val VERSION = "3.0.0"
const val SUBTITLE = "build 260327"
const val GITHUB = "https://github.com/SpartanB312/Grunt"

fun main() {
    Logger = SimpleLogger(
        "Grunteon",
        "logs/${SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(Date())}.txt"
    )
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
        val pipeline = ProcessPipeline(
            DeadCodeRemove(),
            EnumOptimize(),
            KotlinClassShrink(),
            ClassShrink(),
            NumberBasicEncrypt(),
            LocalVarRenamer(),
            ClassRenamer(),
        )
        val instance = emptyConfig.runPipeline(pipeline)
        instance.init()
        instance.execute()
    }.also { println("$it ms") }
}

fun ConfigGroup.runPipeline(pipeline: ProcessPipeline): Grunteon {
    return Grunteon(this, pipeline.apply { parseConfig(this@runPipeline) })
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
    lateinit var output: JarDumper
    lateinit var workRes: WorkResources
    val mappingApplier = MappingApplier(this)
    val baseSeed get() = if (configGroup.controllableRandom) configGroup.inputSeed else Random.nextInt().toString()
    val generalRandom = getSeed(configGroup.input, configGroup.output).toRandom()

    fun init() {
        Logger.info("Executing obfuscating job...")

        val prependPath = System.getenv("GRUNTEON_PREPEND_PATH") ?: ""

        val inputRoot = Path(prependPath, "input.jar")
        val outputRoot = listOf(Path("libs/"))

        fun resolvePath(path: Path): List<Path> {
            return if (path.isDirectory()) {
                path.walk()
                    .filter { !it.isDirectory() && it.extension == "jar" }
                    .map { it }
                    .toList()
            } else {
                listOf(path)
            }
        }

        workRes = WorkResources.read(inputRoot, outputRoot.flatMap { resolvePath(it) })

        // Output dumper
        output = JarDumper(
            instance = this,
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
    }

    fun execute() {
        // TODO: Profiler
        context(workRes) {
            contextOf<Grunteon>().pipeline.execute()
        }

        output.dumpJar()
    }

    val mixinExPredicate = buildClassNamePredicates(configGroup.mixinExclusions)
    val globalExPredicate = buildClassNamePredicates(configGroup.exclusions)

}
