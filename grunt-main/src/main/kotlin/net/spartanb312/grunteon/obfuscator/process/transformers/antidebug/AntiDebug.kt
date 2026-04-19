package net.spartanb312.grunteon.obfuscator.process.transformers.antidebug

import kotlinx.serialization.Serializable
import net.spartanb312.genesis.kotlin.clazz
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.GENERATED_CLASS
import net.spartanb312.grunteon.obfuscator.util.GENERATED_METHOD
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.appendAnnotation
import net.spartanb312.grunteon.obfuscator.util.extensions.getOrCreateClinit
import net.spartanb312.grunteon.obfuscator.util.extensions.isInterface
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.util.Locale

class AntiDebug : Transformer<AntiDebug.Config>(
    name = enText("process.anti_debug.anti_debug", "AntiDebug"),
    category = Category.AntiDebug,
    description = enText(
        "process.anti_debug.anti_debug.desc",
        "Inject runtime debugger and javaagent checks into class initialization"
    )
) {
    @Serializable
    data class Config(
        @SettingDesc(enText = "Specify class include/exclude rules")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc(enText = "Detect JDWP agent arguments")
        val checkJDWP: Boolean = true,
        @SettingDesc(enText = "Detect legacy -Xdebug and debug flags")
        val checkXDebug: Boolean = true,
        @SettingDesc(enText = "Detect -javaagent arguments")
        val checkJavaAgent: Boolean = true,
        @SettingDesc(enText = "Additional lowercase keywords matched against JVM input arguments")
        val customKeywords: List<String> = listOf(),
        @SettingDesc(enText = "Failure message thrown when debugger is detected")
        val message: String = "Debugger detected",
        @SettingDesc(enText = "Specify class exclusions.")
        val exclusion: List<String> = listOf(
            "net/dummy/**",
            "net/dummy/Class"
        )
    ) : TransformerConfig

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        var injectedCount = 0
        pre {
            val strategy = config.classFilter.buildFilterStrategy()
            val eligible = instance.workRes.inputClassCollection
                .asSequence()
                .filter { strategy.testClass(it) }
                .filterNot { it.isInterface }
                .toList()
            if (eligible.isEmpty()) return@pre

            val keywords = buildKeywordList(config)
            if (keywords.isEmpty()) return@pre

            val random = Xoshiro256PPRandom(getSeed("AntiDebug"))
            val runtimeClass = buildRuntimeSupport(random, config, keywords)
            instance.workRes.addGeneratedClass(runtimeClass)

            eligible.forEach { classNode ->
                attachVerifyCall(classNode, runtimeClass.name)
                if (classNode.version < Opcodes.V1_8) classNode.version = Opcodes.V1_8
                injectedCount++
            }
        }
        post {
            Logger.info(" - AntiDebug:")
            Logger.info("    Added anti-debug checks to $injectedCount classes")
        }
    }

    private fun buildKeywordList(config: Config): List<String> {
        val keywords = linkedSetOf<String>()
        if (config.checkJDWP) keywords += "jdwp"
        if (config.checkXDebug) {
            keywords += "-xdebug"
            keywords += "-xrunjdwp"
            keywords += "transport=dt_socket"
            keywords += "transport=dt_shmem"
        }
        if (config.checkJavaAgent) keywords += "-javaagent"
        config.customKeywords
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .forEach { keywords += it }
        return keywords.toList()
    }

    private fun buildRuntimeSupport(
        random: UniformRandomProvider,
        config: Config,
        keywords: List<String>
    ): ClassNode {
        val className = "net/spartanb312/grunteon/antidebug/AntiDebugRuntime_${randomString(random, 6)}"
        return clazz(
            PUBLIC + FINAL + SUPER,
            className,
            "java/lang/Object"
        ).appendAnnotation(GENERATED_CLASS).apply {
            version = Opcodes.V1_8
            methods.add(buildCtor())
            methods.add(buildRuntimeArgsMethod().appendAnnotation(GENERATED_METHOD))
            methods.add(buildCheckMethod(className, keywords).appendAnnotation(GENERATED_METHOD))
            methods.add(buildFailMethod(config.message).appendAnnotation(GENERATED_METHOD))
            methods.add(buildVerifyMethod(className).appendAnnotation(GENERATED_METHOD))
        }
    }

    private fun attachVerifyCall(classNode: ClassNode, runtimeOwner: String) {
        val existing = classNode.methods.firstOrNull { it.name == "<clinit>" }
        val clinit = existing ?: classNode.getOrCreateClinit().also {
            it.access = Opcodes.ACC_STATIC
            it.instructions.add(InsnNode(Opcodes.RETURN))
            classNode.methods.add(it)
        }
        clinit.instructions.insert(
            MethodInsnNode(Opcodes.INVOKESTATIC, runtimeOwner, "verify", "()V", false)
        )
    }

    private fun buildCtor() = method(
        PRIVATE,
        "<init>",
        "()V"
    ) {
        INSTRUCTIONS {
            ALOAD(0)
            INVOKESPECIAL("java/lang/Object", "<init>", "()V")
            RETURN
        }
        MAXS(1, 1)
    }

    private fun buildRuntimeArgsMethod() = method(
        PRIVATE + STATIC,
        "runtimeArgs",
        "()Ljava/lang/String;"
    ) {
        INSTRUCTIONS {
            INVOKESTATIC("java/lang/management/ManagementFactory", "getRuntimeMXBean", "()Ljava/lang/management/RuntimeMXBean;")
            INVOKEINTERFACE("java/lang/management/RuntimeMXBean", "getInputArguments", "()Ljava/util/List;")
            INVOKEVIRTUAL("java/lang/Object", "toString", "()Ljava/lang/String;")
            GETSTATIC("java/util/Locale", "ROOT", "Ljava/util/Locale;")
            INVOKEVIRTUAL("java/lang/String", "toLowerCase", "(Ljava/util/Locale;)Ljava/lang/String;")
            ARETURN
        }
        MAXS(1, 0)
    }

    private fun buildCheckMethod(owner: String, keywords: List<String>) = method(
        PRIVATE + STATIC,
        "hasDebugger",
        "()Z"
    ) {
        INSTRUCTIONS {
            INVOKESTATIC(owner, "runtimeArgs", "()Ljava/lang/String;")
            ASTORE(0)
            keywords.forEachIndexed { index, keyword ->
                ALOAD(0)
                LDC(keyword)
                INVOKEVIRTUAL("java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z")
                IFEQ(L["NEXT_$index"])
                ICONST_1
                IRETURN
                LABEL(L["NEXT_$index"])
            }
            ICONST_0
            IRETURN
        }
        MAXS(2, 1)
    }

    private fun buildFailMethod(message: String) = method(
        PRIVATE + STATIC,
        "fail",
        "()V"
    ) {
        INSTRUCTIONS {
            NEW("java/lang/IllegalStateException")
            DUP
            LDC(message)
            INVOKESPECIAL("java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V")
            ATHROW
        }
        MAXS(3, 0)
    }

    private fun buildVerifyMethod(owner: String) = method(
        PUBLIC + STATIC,
        "verify",
        "()V"
    ) {
        INSTRUCTIONS {
            INVOKESTATIC(owner, "hasDebugger", "()Z")
            IFEQ(L["PASS"])
            INVOKESTATIC(owner, "fail", "()V")
            LABEL(L["PASS"])
            RETURN
        }
        MAXS(1, 0)
    }

    private fun randomString(
        random: UniformRandomProvider,
        length: Int
    ): String {
        val chars = CharArray(length)
        repeat(length) { index ->
            chars[index] = ALPHABET[random.nextInt(ALPHABET.length)]
        }
        return String(chars)
    }

    companion object {
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }
}
