package net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous

import kotlinx.serialization.Serializable
import net.spartanb312.genesis.kotlin.clazz
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.field
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.GENERATED_CLASS
import net.spartanb312.grunteon.obfuscator.util.GENERATED_FIELD
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
import org.objectweb.asm.tree.MethodNode
import java.util.Locale

class HWIDAuthentication : Transformer<HWIDAuthentication.Config>(
    name = enText("process.miscellaneous.hwid_authentication", "HWIDAuthentication"),
    category = Category.Miscellaneous,
    description = enText(
        "process.miscellaneous.hwid_authentication.desc",
        "Inject online/offline HWID verification into classes"
    )
) {
    @Serializable
    data class Config(
        @SettingDesc(enText = "Specify class include/exclude rules")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc(enText = "Load HWID list from remote URL")
        val onlineMode: Boolean = true,
        @SettingDesc(enText = "Offline HWID whitelist")
        val offlineHWID: List<String> = listOf("Put HWID here (For offline mode only)"),
        @SettingDesc(enText = "Remote URL used in online mode")
        val onlineURL: String = "https://pastebin.com/XXXXX",
        @SettingDesc(enText = "AES key used to build comparable HWIDs")
        val encryptKey: String = "1186118611861186",
        @SettingDesc(enText = "How many whitelist cache pools to generate")
        val cachePools: Int = 5,
        @SettingDesc(enText = "Show current HWID in the failure dialog")
        val showHWIDWhenFailed: Boolean = true,
        @SettingDesc(enText = "Encrypt generated constants")
        val encryptConst: Boolean = true,
        @SettingDesc(enText = "Specify class exclusions.")
        val exclusion: List<String> = listOf(
            "net/dummy/**",
            "net/dummy/Class"
        )
    ) : TransformerConfig

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        var generatedPools = 0
        var injectedCount = 0
        pre {
            val strategy = config.classFilter.buildFilterStrategy()
            val eligible = instance.workRes.inputClassCollection
                .asSequence()
                .filter { strategy.testClass(it) }
                .filterNot { it.isInterface }
                .toList()
            if (eligible.isEmpty()) return@pre

            val random = Xoshiro256PPRandom(getSeed("HWIDAuthentication"))
            val utilClass = buildRuntimeSupport(random, config)
            instance.workRes.addGeneratedClass(utilClass)

            val poolAmount = config.cachePools.coerceAtLeast(1).coerceAtMost(eligible.size)
            val pools = ArrayList<CachePool>(poolAmount)
            repeat(poolAmount) { index ->
                val poolClass = buildPoolClass(random, config, index)
                instance.workRes.addGeneratedClass(poolClass.classNode)
                pools += poolClass
                generatedPools++
            }

            eligible.forEachIndexed { index, classNode ->
                val verifyMethod = buildVerifyMethod(
                    owner = classNode,
                    runtimeOwner = utilClass.name,
                    pool = pools[index % pools.size],
                    random = random
                )
                classNode.methods.add(verifyMethod)
                attachVerifyCall(classNode, verifyMethod)
                if (classNode.version < Opcodes.V1_7) classNode.version = Opcodes.V1_7
                injectedCount++
            }
        }
        post {
            Logger.info(" - HWIDAuthentication:")
            Logger.info("    Added $injectedCount HWID verifications in $generatedPools cache pools")
        }
    }

    private data class CachePool(
        val classNode: ClassNode,
        val fieldName: String
    )

    private fun buildRuntimeSupport(
        random: UniformRandomProvider,
        config: Config
    ): ClassNode {
        val className = "net/spartanb312/grunteon/hwid/HWIDRuntime_${randomString(random, 6)}"
        val processedKey = config.encryptKey.processKey()
        return clazz(
            PUBLIC + FINAL + SUPER,
            className,
            "java/lang/Object"
        ).appendAnnotation(GENERATED_CLASS).apply {
            version = Opcodes.V1_8
            methods.add(buildCtor())
            methods.add(buildEnvMethod().appendAnnotation(GENERATED_METHOD))
            methods.add(buildRawMethod(className).appendAnnotation(GENERATED_METHOD))
            methods.add(buildEncodeMethod(processedKey).appendAnnotation(GENERATED_METHOD))
            methods.add(buildCurrentMethod(className).appendAnnotation(GENERATED_METHOD))
            methods.add(buildFailMethod(config.showHWIDWhenFailed).appendAnnotation(GENERATED_METHOD))
        }
    }

    private fun buildPoolClass(
        random: UniformRandomProvider,
        config: Config,
        index: Int
    ): CachePool {
        val className = "net/spartanb312/grunteon/hwid/HWIDPool_${index}_${randomString(random, 5)}"
        val fieldName = "cache_${randomString(random, 8)}"
        val classNode = clazz(
            PUBLIC + FINAL + SUPER,
            className,
            "java/lang/Object"
        ).appendAnnotation(GENERATED_CLASS).apply {
            version = Opcodes.V1_8
            fields.add(
                field(
                    PUBLIC + STATIC,
                    fieldName,
                    "Ljava/util/List;",
                    "Ljava/util/List<Ljava/lang/String;>;",
                    null
                ).appendAnnotation(GENERATED_FIELD)
            )
            methods.add(buildCtor())
            methods.add(buildPoolClinit(className, fieldName, config).appendAnnotation(GENERATED_METHOD))
        }
        return CachePool(classNode, fieldName)
    }

    private fun attachVerifyCall(classNode: ClassNode, verifyMethod: MethodNode) {
        val existing = classNode.methods.firstOrNull { it.name == "<clinit>" }
        val clinit = existing ?: classNode.getOrCreateClinit().also {
            it.access = Opcodes.ACC_STATIC
            it.instructions.add(InsnNode(Opcodes.RETURN))
            classNode.methods.add(it)
        }
        clinit.instructions.insert(
            MethodInsnNode(Opcodes.INVOKESTATIC, classNode.name, verifyMethod.name, "()V", false)
        )
    }

    private fun buildVerifyMethod(
        owner: ClassNode,
        runtimeOwner: String,
        pool: CachePool,
        random: UniformRandomProvider
    ): MethodNode = method(
        PRIVATE + STATIC,
        "verify_${randomString(random, 10)}",
        "()V"
    ) {
        INSTRUCTIONS {
            INVOKESTATIC(runtimeOwner, "current", "()Ljava/lang/String;")
            ASTORE(0)
            GETSTATIC(pool.classNode.name, pool.fieldName, "Ljava/util/List;")
            ALOAD(0)
            INVOKEINTERFACE("java/util/List", "contains", "(Ljava/lang/Object;)Z")
            IFNE(L["PASS"])
            ALOAD(0)
            INVOKESTATIC(runtimeOwner, "fail", "(Ljava/lang/String;)V")
            LABEL(L["PASS"])
            RETURN
        }
        MAXS(2, 1)
    }.appendAnnotation(GENERATED_METHOD)

    private fun buildCtor(): MethodNode = method(
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

    private fun buildEnvMethod(): MethodNode = method(
        PRIVATE + STATIC,
        "env",
        "(Ljava/lang/String;)Ljava/lang/String;"
    ) {
        INSTRUCTIONS {
            ALOAD(0)
            INVOKESTATIC("java/lang/System", "getenv", "(Ljava/lang/String;)Ljava/lang/String;")
            DUP
            IFNONNULL(L["OK"])
            POP
            LDC("")
            LABEL(L["OK"])
            ARETURN
        }
        MAXS(1, 1)
    }

    private fun buildRawMethod(owner: String): MethodNode = method(
        PUBLIC + STATIC,
        "raw",
        "()Ljava/lang/String;"
    ) {
        INSTRUCTIONS {
            NEW("java/lang/StringBuilder")
            DUP
            INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V")
            LDC("PROCESS_IDENTIFIER")
            INVOKESTATIC(owner, "env", "(Ljava/lang/String;)Ljava/lang/String;")
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
            LDC("PROCESSOR_LEVEL")
            INVOKESTATIC(owner, "env", "(Ljava/lang/String;)Ljava/lang/String;")
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
            LDC("PROCESSOR_REVISION")
            INVOKESTATIC(owner, "env", "(Ljava/lang/String;)Ljava/lang/String;")
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
            LDC("PROCESSOR_ARCHITECTURE")
            INVOKESTATIC(owner, "env", "(Ljava/lang/String;)Ljava/lang/String;")
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
            LDC("PROCESSOR_ARCHITEW6432")
            INVOKESTATIC(owner, "env", "(Ljava/lang/String;)Ljava/lang/String;")
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
            LDC("NUMBER_OF_PROCESSORS")
            INVOKESTATIC(owner, "env", "(Ljava/lang/String;)Ljava/lang/String;")
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
            LDC("COMPUTERNAME")
            INVOKESTATIC(owner, "env", "(Ljava/lang/String;)Ljava/lang/String;")
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
            INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;")
            ARETURN
        }
        MAXS(2, 0)
    }

    private fun buildEncodeMethod(processedKey: String): MethodNode = method(
        PUBLIC + STATIC,
        "encode",
        "(Ljava/lang/String;)Ljava/lang/String;"
    ) {
        INSTRUCTIONS {
            TRYCATCH(L["TRY_START"], L["TRY_END"], L["CATCH"], "java/lang/Exception")
            LABEL(L["TRY_START"])
            LDC("AES")
            INVOKESTATIC("javax/crypto/Cipher", "getInstance", "(Ljava/lang/String;)Ljavax/crypto/Cipher;")
            ASTORE(1)
            NEW("javax/crypto/spec/SecretKeySpec")
            DUP
            LDC(processedKey)
            GETSTATIC("java/nio/charset/StandardCharsets", "UTF_8", "Ljava/nio/charset/Charset;")
            INVOKEVIRTUAL("java/lang/String", "getBytes", "(Ljava/nio/charset/Charset;)[B")
            LDC("AES")
            INVOKESPECIAL("javax/crypto/spec/SecretKeySpec", "<init>", "([BLjava/lang/String;)V")
            ASTORE(2)
            ALOAD(1)
            ICONST_1
            ALOAD(2)
            INVOKEVIRTUAL("javax/crypto/Cipher", "init", "(ILjava/security/Key;)V")
            ALOAD(1)
            ALOAD(0)
            GETSTATIC("java/nio/charset/StandardCharsets", "UTF_8", "Ljava/nio/charset/Charset;")
            INVOKEVIRTUAL("java/lang/String", "getBytes", "(Ljava/nio/charset/Charset;)[B")
            INVOKEVIRTUAL("javax/crypto/Cipher", "doFinal", "([B)[B")
            ASTORE(3)
            NEW("java/lang/String")
            DUP
            INVOKESTATIC("java/util/Base64", "getEncoder", "()Ljava/util/Base64\$Encoder;")
            ALOAD(3)
            INVOKEVIRTUAL("java/util/Base64\$Encoder", "encode", "([B)[B")
            GETSTATIC("java/nio/charset/StandardCharsets", "UTF_8", "Ljava/nio/charset/Charset;")
            INVOKESPECIAL("java/lang/String", "<init>", "([BLjava/nio/charset/Charset;)V")
            LDC("/")
            LDC("s")
            INVOKEVIRTUAL("java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;")
            LDC("=")
            LDC("e")
            INVOKEVIRTUAL("java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;")
            LDC("+")
            LDC("p")
            INVOKEVIRTUAL("java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;")
            LABEL(L["TRY_END"])
            ARETURN
            LABEL(L["CATCH"])
            POP
            LDC("Unknown HWID")
            ARETURN
        }
        MAXS(4, 4)
    }

    private fun buildCurrentMethod(owner: String): MethodNode = method(
        PUBLIC + STATIC,
        "current",
        "()Ljava/lang/String;"
    ) {
        INSTRUCTIONS {
            INVOKESTATIC(owner, "raw", "()Ljava/lang/String;")
            INVOKESTATIC(owner, "encode", "(Ljava/lang/String;)Ljava/lang/String;")
            ARETURN
        }
        MAXS(1, 0)
    }

    private fun buildFailMethod(showHWIDWhenFailed: Boolean): MethodNode = method(
        PUBLIC + STATIC,
        "fail",
        "(Ljava/lang/String;)V"
    ) {
        INSTRUCTIONS {
            if (showHWIDWhenFailed) {
                ACONST_NULL
                NEW("java/lang/StringBuilder")
                DUP
                INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V")
                LDC("HWID verification failed\\n")
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
                ALOAD(0)
                INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")
                INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;")
                LDC("Grunteon HWID")
                ICONST_0
                INVOKESTATIC(
                    "javax/swing/JOptionPane",
                    "showMessageDialog",
                    "(Ljava/awt/Component;Ljava/lang/Object;Ljava/lang/String;I)V"
                )
            }
            NEW("java/lang/IllegalStateException")
            DUP
            LDC("HWID verification failed")
            INVOKESPECIAL("java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V")
            ATHROW
        }
        MAXS(4, 1)
    }

    private fun buildPoolClinit(
        owner: String,
        fieldName: String,
        config: Config
    ): MethodNode = method(
        STATIC,
        "<clinit>",
        "()V"
    ) {
        INSTRUCTIONS {
            NEW("java/util/ArrayList")
            DUP
            INVOKESPECIAL("java/util/ArrayList", "<init>", "()V")
            PUTSTATIC(owner, fieldName, "Ljava/util/List;")
            if (!config.onlineMode) {
                config.offlineHWID.forEach { hwid ->
                    GETSTATIC(owner, fieldName, "Ljava/util/List;")
                    LDC(hwid)
                    INVOKEINTERFACE("java/util/List", "add", "(Ljava/lang/Object;)Z")
                    POP
                }
                RETURN
            } else {
                TRYCATCH(L["TRY_START"], L["TRY_END"], L["CATCH"], "java/lang/Exception")
                LABEL(L["TRY_START"])
                NEW("java/net/URL")
                DUP
                LDC(config.onlineURL)
                INVOKESPECIAL("java/net/URL", "<init>", "(Ljava/lang/String;)V")
                INVOKEVIRTUAL("java/net/URL", "openStream", "()Ljava/io/InputStream;")
                ASTORE(0)
                NEW("java/io/BufferedReader")
                DUP
                NEW("java/io/InputStreamReader")
                DUP
                ALOAD(0)
                GETSTATIC("java/nio/charset/StandardCharsets", "UTF_8", "Ljava/nio/charset/Charset;")
                INVOKESPECIAL("java/io/InputStreamReader", "<init>", "(Ljava/io/InputStream;Ljava/nio/charset/Charset;)V")
                INVOKESPECIAL("java/io/BufferedReader", "<init>", "(Ljava/io/Reader;)V")
                ASTORE(1)
                LABEL(L["LOOP_CHECK"])
                ALOAD(1)
                INVOKEVIRTUAL("java/io/BufferedReader", "readLine", "()Ljava/lang/String;")
                DUP
                ASTORE(2)
                IFNULL(L["TRY_END"])
                GETSTATIC(owner, fieldName, "Ljava/util/List;")
                ALOAD(2)
                INVOKEINTERFACE("java/util/List", "add", "(Ljava/lang/Object;)Z")
                POP
                GOTO(L["LOOP_CHECK"])
                LABEL(L["TRY_END"])
                RETURN
                LABEL(L["CATCH"])
                POP
                RETURN
            }
        }
        MAXS(5, 3)
    }.appendAnnotation(GENERATED_METHOD)

    private fun randomString(random: UniformRandomProvider, length: Int): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
        return buildString(length) {
            repeat(length) {
                append(alphabet[random.nextInt(alphabet.length)])
            }
        }
    }

    private fun String.processKey(): String = when {
        length < 16 -> this + "1186118611861186".substring(length)
        length > 16 -> this.substring(0, 8) + this.substring(length - 8, length)
        else -> this
    }.uppercase(Locale.ROOT).lowercase(Locale.ROOT)
}
