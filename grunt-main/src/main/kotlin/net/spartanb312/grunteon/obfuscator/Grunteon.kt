package net.spartanb312.grunteon.obfuscator

import net.spartanb312.grunteon.obfuscator.config.manager.ConfigGroup
import net.spartanb312.grunteon.obfuscator.pipeline.ProcessPipeline
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.resource.JarDumper
import net.spartanb312.grunteon.obfuscator.process.resource.WorkResources
import net.spartanb312.grunteon.obfuscator.process.transformers.PostProcess
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.ArithmeticSubstitute
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number.NumberBasicEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.string.StringArrayedEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.other.FakeSyntheticBridge
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.DeclaredFieldsExtract
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.*
import net.spartanb312.grunteon.obfuscator.process.transformers.other.DecompilerCrasher
import net.spartanb312.grunteon.obfuscator.process.transformers.other.ShuffleMembers
import net.spartanb312.grunteon.obfuscator.process.transformers.other.Watermark
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.FieldAccessProxy
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.InvokeProxy
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.*
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.filters.buildClassNamePredicates
import net.spartanb312.grunteon.obfuscator.util.logging.SimpleLogger
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.walk
import kotlin.random.Random
import kotlin.time.DurationUnit
import kotlin.time.measureTime

/**
 * Grunteon
 * A JVM bytecode obfuscator
 * 3rd generation of Grunt
 */
const val VERSION = "3.0.0"
const val SUBTITLE = "build 260415"
const val GITHUB = "https://github.com/SpartanB312/Grunt"

fun main(args: Array<String>) {
    if ("--silent" !in args) {
        Logger = SimpleLogger(
            "Grunteon",
            "logs/${SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(Date())}.txt"
        )
    }
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

    val queue = ArrayDeque<Double>()
    repeat(args.getOrNull(0)?.toIntOrNull() ?: 1) {
        val emptyConfig = ConfigGroup()
        val pipeline = ProcessPipeline(
            // Optimize
            DeadCodeRemove(),
            EnumOptimize(),
            KotlinClassShrink(),
            ClassShrink(),
            SourceDebugInfoHide(),
            StringEqualsOptimize(),
            // Misc
            DeclaredFieldsExtract(),
            // Encrypt
            ArithmeticSubstitute(),
            NumberBasicEncrypt(),
            StringArrayedEncrypt(),
            // Redirect
            InvokeProxy(),
            FieldAccessProxy(),
            // Renamer
            LocalVarRenamer(),
            ClassRenamer(),
            FieldRenamer(),
            MethodRenamer(),
            // Other
            FakeSyntheticBridge(),
            DecompilerCrasher(),
            ShuffleMembers(),
            Watermark(),
            // Post
            PostProcess()
        )
        val instance = emptyConfig.runPipeline(pipeline)
        instance.init()

        val timeMap: Map<String, Long>
        measureTime {
            timeMap = instance.execute()
        }.toDouble(DurationUnit.MILLISECONDS).also { time ->
            while (queue.size >= 5) queue.poll()
            queue.add(time)
            println("Execution time: ${"%.2f".format(time)} ms (average: ${"%.2f".format(queue.average())} ms)")
            timeMap.forEach { (name, time) ->
                println("$name: ${"%.2f".format(time / 1000000.0)} ms")
            }
        }
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
    val nameMapping = NameMapping()
    val baseSeed get() = if (configGroup.controllableRandom) configGroup.inputSeed else Random.nextInt().toString()

    fun init() {
        Logger.info("Executing obfuscating job...")

        val prependPath = System.getenv("GRUNTEON_PREPEND_PATH") ?: ""

        val inputRoot = Path(prependPath, "input.jar")
        val libs = listOf(Path(prependPath, "libs/"))

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

        workRes = WorkResources.read(inputRoot, libs.flatMap { resolvePath(it) })
    }

    fun execute(): Map<String, Long> {
        // TODO: Profiler
        var timeMap: Map<String, Long>
        context(workRes) {
            timeMap = pipeline.execute()
        }

        // TODO: make this optional
        JarDumper.dumpJar(Path("obfTest/AT/engine/boar-main.jar"))
        return timeMap
    }

    val mixinExPredicate = buildClassNamePredicates(configGroup.mixinExclusions)
    val globalExPredicate = buildClassNamePredicates(configGroup.exclusions)

}
