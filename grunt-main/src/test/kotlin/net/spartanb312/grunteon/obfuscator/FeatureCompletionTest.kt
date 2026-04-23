package net.spartanb312.grunteon.obfuscator

import com.google.gson.JsonParser
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.Controlflow
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.ConstPoolEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.HWIDAuthentication
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.InvokeDynamic
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.ClassRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.FieldRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.LocalVarRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.MethodRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.ReflectionSupport
import net.spartanb312.grunteon.obfuscator.util.DISABLE_FIELD_PROXY
import net.spartanb312.grunteon.obfuscator.util.DISABLE_INVOKE_DISPATCHER
import net.spartanb312.grunteon.obfuscator.util.DISABLE_INVOKE_DYNAMIC
import net.spartanb312.grunteon.obfuscator.util.DISABLE_INVOKE_PROXY
import net.spartanb312.grunteon.obfuscator.util.IGNORE_FIELD_PROXY
import net.spartanb312.grunteon.obfuscator.util.IGNORE_INVOKE_DISPATCHER
import net.spartanb312.grunteon.obfuscator.util.IGNORE_INVOKE_PROXY
import net.spartanb312.grunteon.obfuscator.util.extensions.hasAnnotation
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import java.nio.file.Files
import java.nio.file.Path
import java.net.URLClassLoader
import java.util.jar.JarFile
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.io.path.writeText
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FeatureCompletionTest {

    @Test
    fun localVarRenameSeparatesParameterAndLocalDeletionFlags() {
        val inputJar = compileJar(
            "sample/LocalVarSample.java" to """
                package sample;
                public class LocalVarSample {
                    public int work(int value) {
                        int temp = value + 1;
                        return temp;
                    }
                }
            """.trimIndent()
        )
        val deleteParametersInstance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = null,
                transformerConfigs = listOf(
                    LocalVarRenamer.Config(
                        renameThisReference = false,
                        deleteParameters = true,
                        deleteLocalVars = false
                    )
                )
            )
        )
        deleteParametersInstance.execute()
        val deleteParametersMethod = deleteParametersInstance.workRes.inputClassMap["sample/LocalVarSample"]
            ?.methods
            ?.firstOrNull { it.name == "work" }
        assertNotNull(deleteParametersMethod)
        assertTrue(deleteParametersMethod.localVariables.orEmpty().isNotEmpty(), "deleteParameters 涓嶅簲椤烘墜娓呮帀灞€閮ㄥ彉閲忚〃")
        assertTrue(deleteParametersMethod.parameters.isNullOrEmpty(), "deleteParameters 搴旀竻绌哄弬鏁板厓鏁版嵁")

        val deleteLocalsInstance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = null,
                transformerConfigs = listOf(
                    LocalVarRenamer.Config(
                        renameThisReference = false,
                        deleteParameters = false,
                        deleteLocalVars = true
                    )
                )
            )
        )
        deleteLocalsInstance.execute()
        val deleteLocalsMethod = deleteLocalsInstance.workRes.inputClassMap["sample/LocalVarSample"]
            ?.methods
            ?.firstOrNull { it.name == "work" }
        assertNotNull(deleteLocalsMethod)
        assertTrue(deleteLocalsMethod.localVariables.orEmpty().all { it.name == "this" || it.name == "value" }, "deleteLocalVars 只应保留 this/参数槽位")
        assertFalse(deleteLocalsMethod.parameters.isNullOrEmpty(), "deleteLocalVars 不应清空参数元数据")
    }

    @Test
    fun renameMemberExclusionActuallySkipsSpecifiedMembers() {
        val inputJar = compileJar(
            "sample/TestClass.java" to """
                package sample;
                public class TestClass {
                    private String keepField = "A";
                    private String renameField = "B";
                    private String keep() { return keepField; }
                    private String work() { return renameField; }
                    public static void main(String[] args) {
                        TestClass self = new TestClass();
                        System.out.println(self.keep() + self.work());
                    }
                }
            """.trimIndent()
        )
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = null,
                transformerConfigs = listOf(
                    FieldRenamer.Config(
                        excludedNames = emptyList(),
                        memberExclusion = listOf("sample/TestClass.keepField")
                    ),
                    MethodRenamer.Config(
                        excludedNames = emptyList(),
                        memberExclusion = listOf("sample/TestClass.keep()Ljava/lang/String;")
                    )
                )
            )
        )

        instance.execute()

        val classNode = instance.workRes.inputClassMap["sample/TestClass"]
        assertNotNull(classNode)
        val methodNames = classNode.methods.map { it.name }.toSet()
        val fieldNames = classNode.fields.map { it.name }.toSet()
        assertTrue("keep" in methodNames, "被排除的方法 keep 应保留原名")
        assertFalse("work" in methodNames, "未排除的方法 work 应被重命名")
        assertTrue("keepField" in fieldNames, "被排除的字段 keepField 应保留原名")
        assertFalse("renameField" in fieldNames, "未排除的字段 renameField 应被重命名")
    }

    @Test
    fun constPoolDontScrambleMarksGeneratedPoolToSkipLaterScrambling() {
        val inputJar = compileJar(
            "sample/ConstPoolSample.java" to """
                package sample;
                public class ConstPoolSample {
                    public static String text() { return "HELLO"; }
                }
            """.trimIndent()
        )
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = null,
                transformerConfigs = listOf(
                    ConstPoolEncrypt.Config(
                        integer = false,
                        long = false,
                        float = false,
                        double = false,
                        string = true,
                        dontScramble = true
                    )
                )
            )
        )

        instance.execute()

        val poolClass = instance.workRes.inputClassCollection.firstOrNull { it.name.endsWith("\$ConstantPool") }
        assertNotNull(poolClass, "应生成 ConstantPool 类")
        assertTrue(poolClass.hasAnnotation(DISABLE_FIELD_PROXY))
        assertTrue(poolClass.hasAnnotation(DISABLE_INVOKE_PROXY))
        assertTrue(poolClass.hasAnnotation(DISABLE_INVOKE_DISPATCHER))
        assertTrue(poolClass.hasAnnotation(DISABLE_INVOKE_DYNAMIC))
        assertTrue(poolClass.fields.all { it.hasAnnotation(IGNORE_FIELD_PROXY) })
        assertTrue(poolClass.methods.filterNot { it.name == "<init>" || it.name == "<clinit>" }.all {
            it.hasAnnotation(IGNORE_INVOKE_PROXY) && it.hasAnnotation(IGNORE_INVOKE_DISPATCHER)
        })
    }

    @Test
    fun invokeDynamicAdvancedOptionsNowAffectGeneratedHelpers() {
        val inputJar = compileJar(
            "sample/IndySample.java" to """
                package sample;
                public class IndySample {
                    public static String target(String value) { return value + "!"; }
                    public static String call() { return target("A"); }
                }
            """.trimIndent()
        )
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = null,
                transformerConfigs = listOf(
                    InvokeDynamic.Config(
                        replacePercentage = 100,
                        heavyProtection = true,
                        metadataClass = "sample/meta/Meta",
                        massiveRandomBlank = true,
                        reobfuscate = true,
                        enhancedFlowReobf = true
                    )
                )
            )
        )

        instance.execute()

        val metadataAnnotation = instance.workRes.inputClassCollection.firstOrNull { it.name == "sample/meta/Meta" }
        assertNotNull(metadataAnnotation, "应生成 metadata 注解类")
        val helperOwner = instance.workRes.inputClassMap["sample/IndySample"]
        assertNotNull(helperOwner, "helper 搴旂户缁暀鍦ㄥ師濮嬬被閲岋紝鍜?2.0 椋庢牸瀵归綈")
        val helperMethods = helperOwner.methods.filterNot { it.name == "<init>" || it.name == "<clinit>" || it.name == "target" || it.name == "call" }
        val blankAlphabet = setOf('_', '$', 'I', 'l', '1', 'O', '0')
        assertTrue(helperMethods.any { method -> method.name.all { it in blankAlphabet } }, "MassiveRandomBlank 应生成 blank 风格 helper 名")

        val decryptMethod = helperMethods.firstOrNull { it.desc == "(Ljava/lang/String;)Ljava/lang/String;" }
        assertNotNull(decryptMethod, "搴旂敓鎴?decrypt helper")
        val opcodes = decryptMethod.instructions.toArray().map { it.opcode }
        assertTrue(Opcodes.POP in opcodes, "HeavyProtection 应在 decrypt helper 中插入额外噪音")

        val bootstrapMethod = helperMethods.firstOrNull { it.desc.contains("java/lang/invoke/MethodHandles\$Lookup") }
        assertNotNull(bootstrapMethod, "搴旂敓鎴?bootstrap helper")
        val bootstrapOpcodes = bootstrapMethod.instructions.toArray().map { it.opcode }
        assertTrue(Opcodes.IFLT in bootstrapOpcodes, "Reobfuscate 应向 helper 注入额外控制流")
        assertTrue(Opcodes.NOP in bootstrapOpcodes, "EnhancedFlowReobf 应增加额外噪音指令")
    }

    @Test
    fun controlflowBuilderMovesArithmeticHelpersIntoProcessorClasses() {
        val inputJar = compileJar(
            "sample/FlowSample.java" to """
                package sample;
                public class FlowSample {
                    public static int branch(int value) {
                        if (value > 5) return value + 1;
                        return value - 1;
                    }
                }
            """.trimIndent()
        )
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = null,
                transformerConfigs = listOf(
                    Controlflow.Config(
                        intensity = 2,
                        bogusConditionJump = true,
                        tableSwitchJump = true,
                        mangledCompareJump = true,
                        arithmeticExprBuilder = true,
                        junkBuilderParameter = true,
                        builderNativeAnnotation = true
                    )
                )
            )
        )

        instance.execute()

        val processorClasses = instance.workRes.inputClassCollection.filter { it.name.startsWith("sample/FlowSample\$processor_") }
        assertTrue(processorClasses.isNotEmpty(), "Controlflow builder 搴旂敓鎴愮嫭绔?processor class")
        val generatedBuilderMethods = processorClasses.flatMap { it.methods }.filterNot { it.name == "<init>" || it.name == "<clinit>" }
        assertTrue(generatedBuilderMethods.isNotEmpty(), "processor class 搴斿寘鍚?builder helper")
        assertTrue(generatedBuilderMethods.any { it.desc.endsWith(")I") }, "builder helper 搴旇繑鍥?int 缁撴灉")
    }

    @Test
    fun controlflowCompareJumpsCanBridgeThroughTableSwitches() {
        val inputJar = compileJar(
            "sample/CompareBridgeSample.java" to """
                package sample;
                public class CompareBridgeSample {
                    public static int branch(int left, int right) {
                        if (left > right) {
                            return left - right;
                        }
                        return right - left;
                    }
                }
            """.trimIndent()
        )
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = null,
                transformerConfigs = listOf(
                    Controlflow.Config(
                        intensity = 1,
                        bogusConditionJump = false,
                        switchExtractor = false,
                        switchProtect = false,
                        mutateJumps = false,
                        tableSwitchJump = true,
                        switchReplaceRate = 100,
                        mangledCompareJump = true,
                        ifReplaceRate = 100,
                        ifICompareReplaceRate = 100,
                        junkCode = true,
                        trappedSwitchCase = true
                    )
                )
            )
        )

        instance.execute()

        val classNode = instance.workRes.inputClassMap["sample/CompareBridgeSample"]
        assertNotNull(classNode)
        val branchMethod = classNode.methods.firstOrNull { it.name == "branch" }
        assertNotNull(branchMethod)
        assertTrue(
            branchMethod.instructions.toArray().any { it is TableSwitchInsnNode },
            "Controlflow 鎼存柨鐨?compare jump 鏉烆剚鍨?switch bridge"
        )
    }

    @Test
    fun controlflowNullChecksCanBridgeThroughLookupSwitches() {
        val inputJar = compileJar(
            "sample/LookupBridgeSample.java" to """
                package sample;
                public class LookupBridgeSample {
                    public static int branch(String value) {
                        if (value != null) {
                            return value.length();
                        }
                        return 0;
                    }
                }
            """.trimIndent()
        )
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = null,
                transformerConfigs = listOf(
                    Controlflow.Config(
                        intensity = 1,
                        bogusConditionJump = false,
                        switchExtractor = false,
                        switchProtect = false,
                        mutateJumps = false,
                        tableSwitchJump = true,
                        switchReplaceRate = 100,
                        mangledCompareJump = true,
                        ifReplaceRate = 100,
                        ifICompareReplaceRate = 100,
                        junkCode = true,
                        trappedSwitchCase = true
                    )
                )
            )
        )

        instance.execute()

        val classNode = instance.workRes.inputClassMap["sample/LookupBridgeSample"]
        assertNotNull(classNode)
        val branchMethod = classNode.methods.firstOrNull { it.name == "branch" }
        assertNotNull(branchMethod)
        assertTrue(
            branchMethod.instructions.toArray().any { it is LookupSwitchInsnNode },
            "Controlflow 鎼存柨鐨?null/object compare 鏉烆剚鍨?lookup switch bridge"
        )
    }

    @Test
    fun controlflowBogusConditionPreservesRuntimeBranchSemantics() {
        val inputJar = compileJar(
            "sample/BogusRuntimeSample.java" to """
                package sample;
                public class BogusRuntimeSample {
                    public static int branch(int value) {
                        if (value > 5) return value + 1;
                        return value - 1;
                    }
                }
            """.trimIndent()
        )
        val outputJar = Files.createTempFile("grunteon-controlflow-runtime-bogus", ".jar")
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = outputJar.pathString,
                transformerConfigs = listOf(
                    Controlflow.Config(
                        intensity = 1,
                        switchExtractor = false,
                        bogusConditionJump = true,
                        gotoReplaceRate = 100,
                        mangledCompareJump = false,
                        switchProtect = false,
                        tableSwitchJump = false,
                        mutateJumps = false,
                        reverseExistedIf = false,
                        trappedSwitchCase = false,
                        arithmeticExprBuilder = false,
                        useLocalVar = false,
                        junkCode = false,
                        expandedJunkCode = false
                    )
                )
            )
        )

        instance.execute()

        assertEquals(3, invokeStatic(outputJar, "sample.BogusRuntimeSample", "branch", 4) as Int)
        assertEquals(8, invokeStatic(outputJar, "sample.BogusRuntimeSample", "branch", 7) as Int)
    }

    @Test
    @Disabled("Known controlflow runtime issue: arithmetic builder path can recurse/overflow under current implementation")
    fun controlflowBuilderPreservesRuntimeBranchSemantics() {
        val inputJar = compileJar(
            "sample/FlowRuntimeSample.java" to """
                package sample;
                public class FlowRuntimeSample {
                    public static int branch(int value) {
                        if (value > 5) return value + 1;
                        return value - 1;
                    }
                }
            """.trimIndent()
        )
        val outputJar = Files.createTempFile("grunteon-controlflow-runtime-builder", ".jar")
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = outputJar.pathString,
                transformerConfigs = listOf(
                    Controlflow.Config(
                        intensity = 2,
                        bogusConditionJump = true,
                        tableSwitchJump = true,
                        mangledCompareJump = true,
                        arithmeticExprBuilder = true,
                        junkBuilderParameter = true,
                        builderNativeAnnotation = true
                    )
                )
            )
        )

        instance.execute()

        assertEquals(3, invokeStatic(outputJar, "sample.FlowRuntimeSample", "branch", 4) as Int)
        assertEquals(8, invokeStatic(outputJar, "sample.FlowRuntimeSample", "branch", 7) as Int)
    }

    @Test
    @Disabled("Known controlflow runtime issue: compare-jump switch bridge does not yet preserve runtime semantics")
    fun controlflowCompareJumpBridgesPreserveRuntimeResults() {
        val inputJar = compileJar(
            "sample/CompareRuntimeSample.java" to """
                package sample;
                public class CompareRuntimeSample {
                    public static int branch(int left, int right) {
                        if (left > right) {
                            return left - right;
                        }
                        return right - left;
                    }
                }
            """.trimIndent()
        )
        val outputJar = Files.createTempFile("grunteon-controlflow-runtime-compare", ".jar")
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = outputJar.pathString,
                transformerConfigs = listOf(
                    Controlflow.Config(
                        intensity = 1,
                        bogusConditionJump = false,
                        switchExtractor = false,
                        switchProtect = false,
                        mutateJumps = false,
                        tableSwitchJump = true,
                        switchReplaceRate = 100,
                        mangledCompareJump = true,
                        ifReplaceRate = 100,
                        ifICompareReplaceRate = 100,
                        junkCode = true,
                        trappedSwitchCase = true
                    )
                )
            )
        )

        instance.execute()

        assertEquals(4, invokeStatic(outputJar, "sample.CompareRuntimeSample", "branch", 9, 5) as Int)
        assertEquals(3, invokeStatic(outputJar, "sample.CompareRuntimeSample", "branch", 2, 5) as Int)
    }

    @Test
    @Disabled("Known controlflow runtime issue: lookup-switch bridge can currently emit unverifiable bytecode")
    fun controlflowNullChecksPreserveRuntimeResults() {
        val inputJar = compileJar(
            "sample/LookupRuntimeSample.java" to """
                package sample;
                public class LookupRuntimeSample {
                    public static int branch(String value) {
                        if (value != null) {
                            return value.length();
                        }
                        return 0;
                    }
                }
            """.trimIndent()
        )
        val outputJar = Files.createTempFile("grunteon-controlflow-runtime-lookup", ".jar")
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = outputJar.pathString,
                transformerConfigs = listOf(
                    Controlflow.Config(
                        intensity = 1,
                        bogusConditionJump = false,
                        switchExtractor = false,
                        switchProtect = false,
                        mutateJumps = false,
                        tableSwitchJump = true,
                        switchReplaceRate = 100,
                        mangledCompareJump = true,
                        ifReplaceRate = 100,
                        ifICompareReplaceRate = 100,
                        junkCode = true,
                        trappedSwitchCase = true
                    )
                )
            )
        )

        instance.execute()

        assertEquals(0, invokeStatic(outputJar, "sample.LookupRuntimeSample", "branch", null) as Int)
        assertEquals(6, invokeStatic(outputJar, "sample.LookupRuntimeSample", "branch", "sample") as Int)
    }

    @Test
    fun reflectionSupportRemapsInheritedPublicMembersAcrossHierarchy() {
        val inputJar = compileJar(
            "sample/Base.java" to """
                package sample;
                public class Base {
                    public String fieldValue = "F";
                    public String hello() { return "H"; }
                }
            """.trimIndent(),
            "sample/Child.java" to """
                package sample;
                public class Child extends Base {
                    public static String reflect() throws Exception {
                        var method = Child.class.getMethod("hello");
                        var field = Child.class.getField("fieldValue");
                        return (String) method.invoke(new Child()) + field.get(new Child());
                    }
                }
            """.trimIndent()
        )
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = null,
                transformerConfigs = listOf(
                    ReflectionSupport.Config(
                        printLog = false,
                        clazz = false,
                        method = false,
                        field = false
                    ),
                    ClassRenamer.Config(),
                    FieldRenamer.Config(
                        excludedNames = emptyList(),
                        memberExclusion = emptyList()
                    ),
                    MethodRenamer.Config(
                        excludedNames = listOf("main"),
                        memberExclusion = emptyList()
                    )
                )
            )
        )

        instance.execute()

        val mappedMethodName = instance.nameMapping.mapReflectiveMethodName(
            sequenceOf("sample/Child", "sample/Base"),
            "hello"
        )
        val mappedFieldName = instance.nameMapping.mapReflectiveFieldName(
            sequenceOf("sample/Child", "sample/Base"),
            "fieldValue"
        )
        assertNotNull(mappedMethodName)
        assertNotNull(mappedFieldName)

        val childMappedName = instance.nameMapping.getMapping("sample/Child") ?: "sample/Child"
        val childClass = instance.workRes.inputClassMap[childMappedName]
        assertNotNull(childClass)
        val reflectMethodName = instance.nameMapping.mapMethodName("sample/Child", "reflect", "()Ljava/lang/String;") ?: "reflect"
        val reflectMethod = childClass.methods.firstOrNull { it.name == reflectMethodName }
        assertNotNull(reflectMethod)
        val stringLiterals = reflectMethod.instructions.toArray()
            .mapNotNull { (it as? LdcInsnNode)?.cst as? String }
            .toSet()
        assertFalse("hello" in stringLiterals)
        assertFalse("fieldValue" in stringLiterals)
        assertTrue(mappedMethodName in stringLiterals)
        assertTrue(mappedFieldName in stringLiterals)
    }

    @Test
    fun hwidEncryptConstProtectsGeneratedRuntimeLiterals() {
        val onlineUrl = "https://example.com/list.txt"
        val inputJar = compileJar(
            "sample/HwidTarget.java" to """
                package sample;
                public class HwidTarget {
                    public static int value() { return 1; }
                }
            """.trimIndent()
        )
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = null,
                transformerConfigs = listOf(
                    HWIDAuthentication.Config(
                        onlineMode = true,
                        onlineURL = onlineUrl,
                        encryptConst = true,
                        cachePools = 1
                    )
                )
            )
        )

        instance.execute()

        val runtimeClass = instance.workRes.inputClassCollection.firstOrNull {
            it.name.startsWith("net/spartanb312/grunteon/hwid/HWIDRuntime_")
        }
        val poolClass = instance.workRes.inputClassCollection.firstOrNull {
            it.name.startsWith("net/spartanb312/grunteon/hwid/HWIDPool_")
        }
        assertNotNull(runtimeClass, "搴旂敓鎴?HWID runtime class")
        assertNotNull(poolClass, "搴旂敓鎴?HWID pool class")
        assertTrue(runtimeClass.methods.any { it.name.startsWith("decode_literal_") }, "EncryptConst 搴旂敓鎴愬瓧绗︿覆瑙ｇ爜 helper")
        assertFalse(collectStringConstants(runtimeClass).contains(onlineUrl), "鍔犲瘑鍚?runtime class 涓嶅簲淇濈暀鏄庢枃 onlineURL")
        assertFalse(collectStringConstants(poolClass).contains(onlineUrl), "鍔犲瘑鍚?pool class 涓嶅簲淇濈暀鏄庢枃 onlineURL")
        assertFalse(collectStringConstants(runtimeClass).contains("HWID verification failed"), "鍔犲瘑鍚?runtime class 涓嶅簲淇濈暀澶辫触鎻愮ず鏄庢枃")
    }

    @Test
    fun classRenameRemapsJavaSpiResources() {
        val inputJar = compileJar(
            "sample/spi/MyService.java" to """
                package sample.spi;
                public interface MyService {
                    String name();
                }
            """.trimIndent(),
            "sample/spi/impl/MyServiceImpl.java" to """
                package sample.spi.impl;
                import sample.spi.MyService;
                public class MyServiceImpl implements MyService {
                    public String name() { return "ok"; }
                }
            """.trimIndent(),
            resources = mapOf(
                "META-INF/services/sample.spi.MyService" to "sample.spi.impl.MyServiceImpl\n"
            )
        )
        val outputJar = Files.createTempFile("grunteon-feature-spi", ".jar")
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = outputJar.pathString,
                transformerConfigs = listOf(
                    ClassRenamer.Config()
                )
            )
        )

        instance.execute()

        val mappedService = instance.nameMapping.getMapping("sample/spi/MyService")!!.replace('/', '.')
        val mappedImpl = instance.nameMapping.getMapping("sample/spi/impl/MyServiceImpl")!!.replace('/', '.')
        JarFile(outputJar.toFile()).use { jar ->
            val entry = jar.getJarEntry("META-INF/services/$mappedService")
            assertNotNull(entry, "SPI descriptor file 鎼存棃娈㈢猾濠氬櫢閸涜棄鎮曟稉鈧挧鐤潶 remap")
            val text = jar.getInputStream(entry).reader(Charsets.UTF_8).readText().trim()
            assertEquals(mappedImpl, text)
            assertEquals(null, jar.getJarEntry("META-INF/services/sample.spi.MyService"))
        }
    }

    @Test
    fun classRenameRemapsMethodTypeDescriptorStrings() {
        val inputJar = compileJar(
            "sample/DescTarget.java" to """
                package sample;
                public class DescTarget {
                    public String value() { return "x"; }
                }
            """.trimIndent(),
            "sample/DescUser.java" to """
                package sample;
                import java.lang.invoke.MethodType;
                public class DescUser {
                    public static String descriptor() {
                        return MethodType.fromMethodDescriptorString("(Lsample/DescTarget;)Ljava/lang/String;", DescUser.class.getClassLoader()).toMethodDescriptorString();
                    }
                }
            """.trimIndent()
        )
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = null,
                transformerConfigs = listOf(
                    ReflectionSupport.Config(),
                    ClassRenamer.Config()
                )
            )
        )

        instance.execute()

        val mappedTarget = instance.nameMapping.getMapping("sample/DescTarget")!!
        val userMappedName = instance.nameMapping.getMapping("sample/DescUser") ?: "sample/DescUser"
        val userClass = instance.workRes.inputClassMap[userMappedName]
        assertNotNull(userClass)
        val descriptorMethodName = instance.nameMapping.mapMethodName("sample/DescUser", "descriptor", "()Ljava/lang/String;") ?: "descriptor"
        val descriptorMethod = userClass.methods.firstOrNull { it.name == descriptorMethodName }
        assertNotNull(descriptorMethod)
        val stringLiterals = descriptorMethod.instructions.toArray()
            .mapNotNull { (it as? LdcInsnNode)?.cst as? String }
            .toSet()
        assertFalse("(Lsample/DescTarget;)Ljava/lang/String;" in stringLiterals)
        assertTrue("(L$mappedTarget;)Ljava/lang/String;" in stringLiterals)
    }

    @Test
    fun classRenameRemapsAsmTypeDescriptorAndInternalNameStrings() {
        val inputJar = compileJar(
            "sample/AsmTypeTarget.java" to """
                package sample;
                public class AsmTypeTarget {
                    public String value() { return "ok"; }
                }
            """.trimIndent(),
            "sample/AsmTypeUser.java" to """
                package sample;
                import org.objectweb.asm.Type;
                public class AsmTypeUser {
                    public static String descriptor() {
                        return Type.getType("Lsample/AsmTypeTarget;").getDescriptor();
                    }
                    public static String methodDescriptor() {
                        return Type.getMethodType("(Lsample/AsmTypeTarget;)Ljava/lang/String;").getDescriptor();
                    }
                    public static String internalName() {
                        return Type.getObjectType("sample/AsmTypeTarget").getInternalName();
                    }
                }
            """.trimIndent()
        )
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = null,
                transformerConfigs = listOf(
                    ReflectionSupport.Config(),
                    ClassRenamer.Config()
                )
            )
        )

        instance.execute()

        val mappedTarget = instance.nameMapping.getMapping("sample/AsmTypeTarget")!!
        val userMappedName = instance.nameMapping.getMapping("sample/AsmTypeUser") ?: "sample/AsmTypeUser"
        val userClass = instance.workRes.inputClassMap[userMappedName]
        assertNotNull(userClass)
        val stringLiterals = userClass.methods.asSequence()
            .flatMap { it.instructions.toArray().asSequence() }
            .mapNotNull { (it as? LdcInsnNode)?.cst as? String }
            .toSet()
        assertFalse("Lsample/AsmTypeTarget;" in stringLiterals)
        assertFalse("(Lsample/AsmTypeTarget;)Ljava/lang/String;" in stringLiterals)
        assertFalse("sample/AsmTypeTarget" in stringLiterals)
        assertTrue("L$mappedTarget;" in stringLiterals)
        assertTrue("(L$mappedTarget;)Ljava/lang/String;" in stringLiterals)
        assertTrue(mappedTarget in stringLiterals)
    }

    @Test
    fun classRenameRemapsConstantDescDescriptorStrings() {
        val inputJar = compileJar(
            "sample/ConstDescTarget.java" to """
                package sample;
                public class ConstDescTarget {
                    public String value() { return "OK"; }
                }
            """.trimIndent(),
            "sample/ConstDescUser.java" to """
                package sample;
                import java.lang.constant.ClassDesc;
                import java.lang.constant.MethodTypeDesc;
                public class ConstDescUser {
                    public static ClassDesc classDesc() {
                        return ClassDesc.ofDescriptor("Lsample/ConstDescTarget;");
                    }
                    public static MethodTypeDesc methodTypeDesc() {
                        return MethodTypeDesc.ofDescriptor("(Lsample/ConstDescTarget;)Ljava/lang/String;");
                    }
                }
            """.trimIndent()
        )
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = null,
                transformerConfigs = listOf(
                    ReflectionSupport.Config(),
                    ClassRenamer.Config()
                )
            )
        )

        instance.execute()

        val mappedTarget = instance.nameMapping.getMapping("sample/ConstDescTarget")!!
        val userMappedName = instance.nameMapping.getMapping("sample/ConstDescUser") ?: "sample/ConstDescUser"
        val userClass = instance.workRes.inputClassMap[userMappedName]
        assertNotNull(userClass)
        val stringLiterals = userClass.methods.asSequence()
            .flatMap { it.instructions.toArray().asSequence() }
            .mapNotNull { (it as? LdcInsnNode)?.cst as? String }
            .toSet()
        assertFalse("Lsample/ConstDescTarget;" in stringLiterals)
        assertFalse("(Lsample/ConstDescTarget;)Ljava/lang/String;" in stringLiterals)
        assertTrue("L$mappedTarget;" in stringLiterals)
        assertTrue("(L$mappedTarget;)Ljava/lang/String;" in stringLiterals)
    }

    @Test
    fun renameRemapsAsmHandleOwnerMemberAndDescriptorStrings() {
        val inputJar = compileJar(
            "sample/HandleTarget.java" to """
                package sample;
                public class HandleTarget {
                    public String fieldValue = "F";
                    public String work(int value) { return String.valueOf(value); }
                }
            """.trimIndent(),
            "sample/HandleUser.java" to """
                package sample;
                import org.objectweb.asm.Handle;
                import org.objectweb.asm.Opcodes;
                public class HandleUser {
                    public static Handle methodHandle() {
                        return new Handle(Opcodes.H_INVOKEVIRTUAL, "sample/HandleTarget", "work", "(I)Ljava/lang/String;", false);
                    }
                    public static Handle fieldHandle() {
                        return new Handle(Opcodes.H_GETFIELD, "sample/HandleTarget", "fieldValue", "Ljava/lang/String;", false);
                    }
                }
            """.trimIndent()
        )
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = null,
                transformerConfigs = listOf(
                    ReflectionSupport.Config(),
                    ClassRenamer.Config(),
                    FieldRenamer.Config(
                        excludedNames = emptyList(),
                        memberExclusion = emptyList()
                    ),
                    MethodRenamer.Config(
                        excludedNames = listOf("main"),
                        memberExclusion = emptyList()
                    )
                )
            )
        )

        instance.execute()

        val mappedOwner = instance.nameMapping.getMapping("sample/HandleTarget")!!
        val mappedMethod = instance.nameMapping.mapMethodName("sample/HandleTarget", "work", "(I)Ljava/lang/String;")!!
        val mappedField = instance.nameMapping.mapFieldName("sample/HandleTarget", "fieldValue", "Ljava/lang/String;")
        val userMappedName = instance.nameMapping.getMapping("sample/HandleUser") ?: "sample/HandleUser"
        val userClass = instance.workRes.inputClassMap[userMappedName]
        assertNotNull(userClass)
        val stringLiterals = userClass.methods.asSequence()
            .flatMap { it.instructions.toArray().asSequence() }
            .mapNotNull { (it as? LdcInsnNode)?.cst as? String }
            .toSet()
        assertFalse("sample/HandleTarget" in stringLiterals)
        assertFalse("work" in stringLiterals)
        assertFalse("fieldValue" in stringLiterals)
        assertTrue(mappedOwner in stringLiterals)
        assertTrue(mappedMethod in stringLiterals)
        assertTrue(mappedField in stringLiterals)
        assertTrue("(I)Ljava/lang/String;" in stringLiterals, "JDK method descriptor 不应被改动")
        assertTrue("Ljava/lang/String;" in stringLiterals, "JDK field descriptor 不应被改动")
    }

    @Test
    fun dumpMappingsIncludesMetaPipelineSummaryAndInvokeDynamicSections() {
        val inputJar = compileJar(
            "sample/MappingTarget.java" to """
                package sample;
                import java.util.function.Supplier;
                public class MappingTarget {
                    private String fieldValue = "F";
                    public String work() { return "!" + fieldValue; }
                    public static Supplier<String> supplier() { return new MappingTarget()::work; }
                }
            """.trimIndent()
        )
        val outputJar = Files.createTempFile("grunteon-feature-mapping", ".jar")
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = outputJar.pathString,
                dumpMappings = true,
                transformerConfigs = listOf(
                    ClassRenamer.Config(),
                    FieldRenamer.Config(
                        excludedNames = emptyList(),
                        memberExclusion = emptyList()
                    ),
                    MethodRenamer.Config(
                        excludedNames = listOf("main"),
                        memberExclusion = emptyList()
                    )
                )
            )
        )

        instance.execute()

        val mappingFile = outputJar.resolveSibling("${outputJar.fileName.toString().removeSuffix(".jar")}.mappings.json")
        assertTrue(Files.exists(mappingFile), "应输出 .mappings.json 文件")
        instance.nameMapping.putIndyMapping("sampleDyn", "()Ljava/lang/String;", "a")
        instance.nameMapping.dump(
            mappingFile,
            net.spartanb312.grunteon.obfuscator.process.transformers.rename.NameMapping.DumpContext(
                input = inputJar.pathString,
                output = outputJar.pathString,
                profiler = false,
                multithreading = false,
                steps = instance.transformers.map { it.first.engName }
            )
        )

        val root = JsonParser.parseString(Files.readString(mappingFile)).asJsonObject
        val meta = root.getAsJsonObject("meta")
        val pipeline = root.getAsJsonObject("pipeline")
        val summary = root.getAsJsonObject("summary")
        val classes = root.getAsJsonObject("classes")
        val invokedynamic = root.getAsJsonObject("invokedynamic")

        assertEquals("grunteon/mappings@1", meta.get("schema").asString)
        assertEquals(inputJar.pathString, meta.get("input").asString)
        assertEquals(outputJar.pathString, meta.get("output").asString)
        assertTrue(meta.has("generatedAt"))

        val steps = pipeline.getAsJsonArray("steps").map { it.asString }
        assertTrue("ClassRenamer" in steps)
        assertTrue("FieldRenamer" in steps)
        assertTrue("MethodRenamer" in steps)
        assertTrue("MappingApplier" in steps)
        assertFalse(pipeline.get("multithreading").asBoolean)
        assertFalse(pipeline.get("profiler").asBoolean)

        assertTrue(summary.get("classCount").asInt > 0)
        assertTrue(summary.get("methodCount").asInt > 0)
        assertTrue(summary.get("fieldCount").asInt > 0)
        assertEquals(invokedynamic.entrySet().size, summary.get("indyCount").asInt)
        assertTrue(summary.get("indyCount").asInt > 0)

        assertTrue(classes.has("sample/MappingTarget"))
        val classEntry = classes.getAsJsonObject("sample/MappingTarget")
        assertTrue(classEntry.has("new"))
        assertTrue(classEntry.getAsJsonObject("methods").entrySet().isNotEmpty())
        assertTrue(classEntry.getAsJsonObject("fields").entrySet().isNotEmpty())
        assertTrue(invokedynamic.entrySet().isNotEmpty(), "InvokeDynamic 映射应导出到 invokedynamic 区块")
    }

    @Test
    fun dumpMappingsIncludesResourceRemapSections() {
        val inputJar = compileJar(
            "sample/ManifestMain.java" to """
                package sample;
                public class ManifestMain {
                    public static void main(String[] args) {
                        System.out.println("ok");
                    }
                }
            """.trimIndent(),
            "sample/spi/MyService.java" to """
                package sample.spi;
                public interface MyService {
                    String name();
                }
            """.trimIndent(),
            "sample/spi/impl/MyServiceImpl.java" to """
                package sample.spi.impl;
                import sample.spi.MyService;
                public class MyServiceImpl implements MyService {
                    public String name() { return "service"; }
                }
            """.trimIndent(),
            resources = mapOf(
                "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0\nMain-Class: sample.ManifestMain\n",
                "META-INF/services/sample.spi.MyService" to "sample.spi.impl.MyServiceImpl\n"
            )
        )
        val outputJar = Files.createTempFile("grunteon-feature-resource-mapping", ".jar")
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = outputJar.pathString,
                dumpMappings = true,
                transformerConfigs = listOf(
                    ClassRenamer.Config(),
                    net.spartanb312.grunteon.obfuscator.process.transformers.PostProcess.Config()
                )
            )
        )

        instance.execute()

        val mappingFile = outputJar.resolveSibling("${outputJar.fileName.toString().removeSuffix(".jar")}.mappings.json")
        assertTrue(Files.exists(mappingFile), "应输出包含 resources 的 .mappings.json 文件")

        val root = JsonParser.parseString(Files.readString(mappingFile)).asJsonObject
        val resources = root.getAsJsonObject("resources")
        val services = resources.getAsJsonObject("services")
        val serviceFiles = services.getAsJsonObject("files")
        val serviceImpls = services.getAsJsonObject("implementations")
        val manifest = resources.getAsJsonObject("manifest")
        val classResources = resources.getAsJsonObject("classResources")

        val mappedMain = instance.nameMapping.getMapping("sample/ManifestMain")!!
        val mappedService = instance.nameMapping.getMapping("sample/spi/MyService")!!.replace('/', '.')
        val mappedImpl = instance.nameMapping.getMapping("sample/spi/impl/MyServiceImpl")!!.replace('/', '.')

        assertEquals("META-INF/services/$mappedService", serviceFiles.get("META-INF/services/sample.spi.MyService").asString)
        assertEquals(mappedImpl, serviceImpls.get("sample.spi.impl.MyServiceImpl").asString)
        assertEquals(mappedMain.replace('/', '.'), manifest.getAsJsonObject("Main-Class").get("sample.ManifestMain").asString)
        assertEquals("${mappedMain}.class", classResources.get("sample/ManifestMain.class").asString)
    }

    @Test
    fun invokeDynamicHelperShapePoolAddsBlankHelperAndBootstrapPrelude() {
        val inputJar = compileJar(
            "sample/ShapePoolSample.java" to """
                package sample;
                public class ShapePoolSample {
                    public static String target(String value) { return value + value; }
                    public static String call() { return target("B"); }
                }
            """.trimIndent()
        )
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = null,
                transformerConfigs = listOf(
                    InvokeDynamic.Config(
                        replacePercentage = 100,
                        heavyProtection = true,
                        metadataClass = "sample/meta/ShapeMeta",
                        massiveRandomBlank = true,
                        reobfuscate = true,
                        enhancedFlowReobf = true
                    )
                )
            )
        )

        instance.execute()

        val helperOwner = instance.workRes.inputClassMap["sample/ShapePoolSample"]
        assertNotNull(helperOwner)
        val helperMethods = helperOwner.methods.filterNot { it.name == "<init>" || it.name == "<clinit>" || it.name == "target" || it.name == "call" }
        val blankAlphabet = setOf('_', '$', 'I', 'l', '1', 'O', '0')
        val blankHelper = helperMethods.firstOrNull { it.desc == "()V" && it.name.all { ch -> ch in blankAlphabet } }
        assertNotNull(blankHelper)

        val bootstrapMethod = helperMethods.firstOrNull { it.desc.contains("java/lang/invoke/MethodHandles\$Lookup") }
        assertNotNull(bootstrapMethod)
        val bootstrapCalls = bootstrapMethod.instructions.toArray()
            .mapNotNull { it as? MethodInsnNode }
            .map { "${it.owner}.${it.name}${it.desc}" }
        assertTrue(bootstrapCalls.any { it == "${helperOwner.name}.${blankHelper.name}()V" })
        assertTrue(bootstrapCalls.any { it == "java/lang/invoke/MethodType.methodType(Ljava/lang/Class;)Ljava/lang/invoke/MethodType;" })
    }

    @Test
    fun invokeDynamicMetadataClassCarriesExtendedPayload() {
        val inputJar = compileJar(
            "sample/MetaPayloadSample.java" to """
                package sample;
                public class MetaPayloadSample {
                    public static String target(String value) { return value + "?"; }
                    public static String call() { return target("C"); }
                }
            """.trimIndent()
        )
        val instance = Grunteon.create(
            ObfConfig(
                input = inputJar.pathString,
                output = null,
                transformerConfigs = listOf(
                    InvokeDynamic.Config(
                        replacePercentage = 100,
                        heavyProtection = true,
                        metadataClass = "sample/meta/PayloadMeta",
                        massiveRandomBlank = true,
                        reobfuscate = true,
                        enhancedFlowReobf = true
                    )
                )
            )
        )

        instance.execute()

        val metadataClass = instance.workRes.inputClassCollection.firstOrNull { it.name == "sample/meta/PayloadMeta" }
        assertNotNull(metadataClass)
        val metadataMethods = metadataClass.methods.map { it.name }.toSet()
        assertTrue("salt" in metadataMethods)
        assertTrue("flags" in metadataMethods)

        val helperOwner = instance.workRes.inputClassMap["sample/MetaPayloadSample"]
        assertNotNull(helperOwner)
        val bootstrapHelpers = helperOwner.methods.filter { it.desc.contains("java/lang/invoke/MethodHandles\$Lookup") }
        assertTrue(bootstrapHelpers.isNotEmpty())
        val bootstrapCalls = bootstrapHelpers
            .flatMap { method ->
                method.instructions.toArray()
                    .mapNotNull { it as? MethodInsnNode }
                    .map { "${it.owner}.${it.name}${it.desc}" }
            }
        assertTrue(bootstrapCalls.any { it == "sample/meta/PayloadMeta.flags()I" })
        assertTrue(bootstrapCalls.any { it == "sample/meta/PayloadMeta.salt()Ljava/lang/String;" })
    }

    private fun compileJar(
        vararg sources: Pair<String, String>,
        resources: Map<String, String> = emptyMap()
    ): Path {
        val compiler = ToolProvider.getSystemJavaCompiler()
        requireNotNull(compiler) { "褰撳墠 JDK 涓嶅寘鍚?JavaCompiler" }
        val root = Files.createTempDirectory("grunteon-feature-test")
        val srcDir = root.resolve("src")
        val classesDir = root.resolve("classes")
        Files.createDirectories(srcDir)
        Files.createDirectories(classesDir)
        val sourceFiles = sources.map { (relative, code) ->
            val file = srcDir.resolve(relative)
            Files.createDirectories(file.parent)
            file.writeText(code)
            file.toFile()
        }
        val fileManager = compiler.getStandardFileManager(null, null, null)
        fileManager.use { manager ->
            val units = manager.getJavaFileObjectsFromFiles(sourceFiles)
            val result = compiler.getTask(
                null,
                manager,
                null,
                listOf(
                    "-encoding", "UTF-8",
                    "-parameters",
                    "-g",
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classesDir.absolutePathString()
                ),
                null,
                units
            ).call()
            assertEquals(true, result, "涓存椂鏍蜂緥缂栬瘧澶辫触")
        }
        val jar = root.resolve("input.jar")
        JarOutputStream(Files.newOutputStream(jar)).use { jos ->
            Files.walk(classesDir).use { walk ->
                walk.filter { Files.isRegularFile(it) && it.extension == "class" }
                    .sorted()
                    .forEach { file ->
                        val entryName = classesDir.relativize(file).invariantSeparatorsPathString
                        jos.putNextEntry(JarEntry(entryName))
                        jos.write(file.readBytes())
                        jos.closeEntry()
                    }
            }
            resources.toSortedMap().forEach { (entryName, content) ->
                jos.putNextEntry(JarEntry(entryName))
                jos.write(content.toByteArray(Charsets.UTF_8))
                jos.closeEntry()
            }
        }
        return jar
    }

    private fun collectStringConstants(classNode: ClassNode): Set<String> {
        return classNode.methods.asSequence()
            .flatMap { method -> method.instructions.toArray().asSequence() }
            .mapNotNull { insn ->
                val ldc = insn as? LdcInsnNode ?: return@mapNotNull null
                ldc.cst as? String
            }
            .toSet()
    }

    private fun invokeStatic(jar: Path, className: String, methodName: String, vararg args: Any?): Any? {
        URLClassLoader(arrayOf(jar.toUri().toURL()), javaClass.classLoader).use { loader ->
            val klass = loader.loadClass(className)
            val method = klass.declaredMethods.firstOrNull {
                it.name == methodName && it.parameterCount == args.size
            }
            assertNotNull(method, "找不到方法 $className#$methodName/${args.size}")
            return method.invoke(null, *args)
        }
    }
}
