package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.config.manager.ConfigGroup
import net.spartanb312.grunteon.obfuscator.pipeline.ProcessPipeline
import net.spartanb312.grunteon.obfuscator.process.MappingApplier
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.resource.JarDumper
import net.spartanb312.grunteon.obfuscator.process.resource.WorkResources
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number.NumberBasicEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.*
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.ClassRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.LocalVarRenamer
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.filters.buildClassNamePredicates
import java.nio.file.Path
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
//    Logger = SimpleLogger(
//        "Grunteon",
//        "logs/${SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(Date())}.txt"
//    )
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

    repeat(100) {
        measureTimeMillis {
            val emptyConfig = ConfigGroup()
            val pipeline = ProcessPipeline(
                DeadCodeRemove(),
                EnumOptimize(),
                KotlinClassShrink(),
                ClassShrink(),
                SourceDebugInfoHide(),
                StringEqualsOptimize(),
                NumberBasicEncrypt(),
                LocalVarRenamer(),
                ClassRenamer(),
            )
            val instance = emptyConfig.runPipeline(pipeline)
            instance.init()
            instance.execute()
        }.also { println("$it ms") }
    }
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
    lateinit var workRes: WorkResources
    val mappingApplier = MappingApplier(this)
    val baseSeed get() = if (configGroup.controllableRandom) configGroup.inputSeed else Random.nextInt().toString()

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
    }

    fun execute() {
//        // TODO: Profiler
//        context(workRes) {
//            contextOf<Grunteon>().pipeline.execute()
//        }

        // TODO: make this optional
        JarDumper.dumpJar(Path("output.jar"))
    }

    val mixinExPredicate = buildClassNamePredicates(configGroup.mixinExclusions)
    val globalExPredicate = buildClassNamePredicates(configGroup.exclusions)

}
