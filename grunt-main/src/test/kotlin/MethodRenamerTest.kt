import net.spartanb312.grunteon.obfuscator.config.manager.ConfigGroup
import net.spartanb312.grunteon.obfuscator.pipeline.ProcessPipeline
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.MethodRenamer
import net.spartanb312.grunteon.obfuscator.util.ClearClassNode
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassWriter.COMPUTE_FRAMES
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.outputStream
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class MethodRenamerTest {
    private lateinit var tempDir: Path

    @BeforeTest
    fun before() {
        val instance = readTestClasses(
            net.spartanb312.grunteon.testcase.Asserts::class.java,
            ProcessPipeline(MethodRenamer()).apply { parseConfig(ConfigGroup()) }
        )
        context(instance.workRes, instance) {
            instance.pipeline.execute()
        }
        tempDir = Path("build/tmp/grunteon-MethodRenamerTest").also { println(it.absolutePathString()) }
        for (classNode in instance.workRes.inputClassCollection) {
            val bytes = ClassWriter(COMPUTE_FRAMES).apply {
                classNode.accept(ClearClassNode(Opcodes.ASM9, this))
            }.toByteArray()
            val outputFile = tempDir.resolve(classNode.name + ".class")
            Files.createDirectories(outputFile.parent)
            outputFile.outputStream().use {
                it.write(bytes)
            }
        }
    }

    private fun runTestClass(className: String) {
        val javaHome = Path.of(System.getProperty("java.home"))
        val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")
        val javaExe = javaHome.resolve("bin").resolve(if (isWindows) "java.exe" else "java").toString()
        val process = ProcessBuilder(javaExe, "-cp", tempDir.absolutePathString(), className)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        assertEquals(0, exitCode, "Test class $className failed with exit code $exitCode:\n$output")
    }

    @Test
    fun implicitOverrideEnd1() = runTestClass("net.spartanb312.grunteon.testcase.methodrename.ImplicitOverrideEnd1")
    @Test
    fun implicitOverrideMid1() = runTestClass("net.spartanb312.grunteon.testcase.methodrename.ImplicitOverrideMid1")
    @Test
    fun overlapComplex() = runTestClass("net.spartanb312.grunteon.testcase.methodrename.OverlapComplex")
    @Test
    fun overlapImplicitOverride2To1() =
        runTestClass("net.spartanb312.grunteon.testcase.methodrename.OverlapImplicitOverride2To1")

    @Test
    fun overlapImplicitOverride3To1() =
        runTestClass("net.spartanb312.grunteon.testcase.methodrename.OverlapImplicitOverride3To1")

    @Test
    fun overlapImplicitOverride3To1To1() =
        runTestClass("net.spartanb312.grunteon.testcase.methodrename.OverlapImplicitOverride3To1To1")

    @Test
    fun overlapInterface2To1() = runTestClass("net.spartanb312.grunteon.testcase.methodrename.OverlapInterface2To1")
    @Test
    fun overlapInterface3To2() = runTestClass("net.spartanb312.grunteon.testcase.methodrename.OverlapInterface3To2")
    @Test
    fun overlapInterface3To2To1() =
        runTestClass("net.spartanb312.grunteon.testcase.methodrename.OverlapInterface3To2To1")

    @Test
    @Ignore
    fun overloadShadow1() = runTestClass("net.spartanb312.grunteon.testcase.methodrename.OverloadShadow1")
}