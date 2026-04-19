package net.spartanb312.grunteon.obfuscator.process.transformers.redirect

import kotlinx.serialization.Serializable
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.NativeCandidate
import net.spartanb312.grunteon.obfuscator.util.DISABLE_INVOKE_DYNAMIC
import net.spartanb312.grunteon.obfuscator.util.GENERATED_METHOD
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.collection.FastObjectArrayList
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.*
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.isExcluded
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import net.spartanb312.grunteon.obfuscator.util.getRandomString
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import java.lang.invoke.CallSite
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.random.Random

class InvokeDynamic : Transformer<InvokeDynamic.Config>(
    name = enText("process.redirect.invoke_dynamic", "InvokeDynamic"),
    category = Category.Redirect,
    description = enText(
        "process.redirect.invoke_dynamic.desc",
        "Replace method invokes with invokedynamic bootstrap calls"
    )
) {
    @Serializable
    data class Config(
        @SettingDesc(enText = "Specify class include/exclude rules")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc(enText = "InvokeDynamic replacement rate")
        val replacePercentage: Int = 10,
        @SettingDesc(enText = "Generate heavy protection metadata")
        val heavyProtection: Boolean = false,
        @SettingDesc(enText = "Class name used for generated bootstrap metadata")
        val metadataClass: String = "net/spartanb312/grunt/GruntMetadata",
        @SettingDesc(enText = "Use random blank bootstrap names")
        val massiveRandomBlank: Boolean = false,
        @SettingDesc(enText = "Re-obfuscate generated helper methods")
        val reobfuscate: Boolean = true,
        @SettingDesc(enText = "Use enhanced controlflow during re-obfuscation")
        val enhancedFlowReobf: Boolean = false,
        @SettingDesc(enText = "Mark bootstrap helpers as native candidates")
        val bsmNativeAnnotation: Boolean = false,
        @SettingDesc(enText = "Specify method exclusions.")
        val exclusion: List<String> = listOf(
            "net/dummy/**",
            "net/dummy/Class",
            "net/dummy/Class.method",
            "net/dummy/Class.method()V"
        )
    ) : TransformerConfig

    private lateinit var methodExPredicate: NamePredicates

    init {
        after(Category.Renaming, "InvokeDynamic should run after renaming to avoid stale target names")
    }

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        barrier()
        pre {
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }
        val redirectCounter = reducibleScopeValue { MergeableCounter() }
        val helperCounter = reducibleScopeValue { MergeableCounter() }
        val generatedMethods = reducibleScopeValue {
            MergeableObjectList<GeneratedMethod>(FastObjectArrayList())
        }

        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            if (classNode.isExcluded(DISABLE_INVOKE_DYNAMIC)) return@parForEachClassesFiltered
            val redirectCounter = redirectCounter.local
            val helperCounter = helperCounter.local
            val generatedMethods = generatedMethods.local
            val classRandom = Xoshiro256PPRandom(getSeed(classNode.name, "InvokeDynamic"))
            val bootstrapName = "indy_bootstrap_${classRandom.getRandomString(10)}"
            val decryptName = "indy_decrypt_${classRandom.getRandomString(10)}"
            val decryptKey = Random(classNode.name.hashCode()).nextInt(8, 0x800)
            var helperGenerated = false

            classNode.methods.asSequence()
                .filter { !it.isAbstract && !it.isNative && !it.isInitializer }
                .forEach { method ->
                    if (method.isExcluded(DISABLE_INVOKE_DYNAMIC)) return@forEach
                    val excluded = methodExPredicate.matchedAnyBy(methodFullDesc(classNode, method))
                    if (excluded) return@forEach
                    val randomGen = Xoshiro256PPRandom(getSeed(classNode.name, method.name, method.desc))
                    method.instructions.toList().forEach { instruction ->
                        if (instruction !is MethodInsnNode) return@forEach
                        if (instruction.opcode == Opcodes.INVOKESPECIAL || instruction.opcode == Opcodes.INVOKEINTERFACE) return@forEach
                        if (instruction.name == bootstrapName || instruction.name == decryptName) return@forEach
                        if (randomGen.nextInt(100) >= config.replacePercentage) return@forEach

                        val indy = buildInvokeDynamicInsn(
                            classNode = classNode,
                            targetInsn = instruction,
                            bootstrapName = bootstrapName,
                            decryptKey = decryptKey
                        )
                        method.instructions.insertBefore(instruction, indy)
                        method.instructions.remove(instruction)
                        redirectCounter.add()
                        if (!helperGenerated) {
                            generatedMethods.add(
                                GeneratedMethod(classNode, createDecryptMethod(classNode, decryptName, decryptKey))
                            )
                            generatedMethods.add(
                                GeneratedMethod(classNode, createBootstrapMethod(classNode, bootstrapName, decryptName))
                            )
                            helperGenerated = true
                            helperCounter.add(2)
                        }
                    }
                }
        }

        seq {
            generatedMethods.global
                .groupBy { it.owner }
                .forEach { (owner, methods) ->
                    methods.forEach {
                        if (config.bsmNativeAnnotation) NativeCandidate.registerGeneratedMethod(it.method)
                        owner.methods.add(it.method.appendAnnotation(GENERATED_METHOD))
                    }
                }
        }
        post {
            Logger.info(" - InvokeDynamic:")
            Logger.info("    Replaced ${redirectCounter.global.get()} invokes")
            Logger.info("    Generated ${helperCounter.global.get()} helper methods")
        }
    }

    private fun buildInvokeDynamicInsn(
        classNode: ClassNode,
        targetInsn: MethodInsnNode,
        bootstrapName: String,
        decryptKey: Int
    ): InvokeDynamicInsnNode {
        val owner = encrypt(targetInsn.owner.replace('/', '.'), decryptKey)
        val name = encrypt(targetInsn.name, decryptKey)
        val desc = encrypt(targetInsn.desc, decryptKey)
        val invokeMode = if (targetInsn.opcode == Opcodes.INVOKESTATIC) 0 else 1
        val indyDesc = if (targetInsn.opcode == Opcodes.INVOKESTATIC) {
            targetInsn.desc
        } else {
            targetInsn.desc.replace("(", "(Ljava/lang/Object;")
        }
        return InvokeDynamicInsnNode(
            bootstrapName,
            indyDesc,
            Handle(
                Opcodes.H_INVOKESTATIC,
                classNode.name,
                bootstrapName,
                MethodType.methodType(
                    CallSite::class.java,
                    MethodHandles.Lookup::class.java,
                    String::class.java,
                    MethodType::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType
                ).toMethodDescriptorString(),
                false
            ),
            owner,
            name,
            desc,
            invokeMode
        )
    }

    private fun createBootstrapMethod(classNode: ClassNode, methodName: String, decryptName: String): MethodNode = method(
        (if (classNode.isInterface) PUBLIC else PRIVATE) + STATIC,
        methodName,
        MethodType.methodType(
            CallSite::class.java,
            MethodHandles.Lookup::class.java,
            String::class.java,
            MethodType::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType
        ).toMethodDescriptorString()
    ) {
        INSTRUCTIONS {
            TRYCATCH(L["A"], L["B"], L["C"], "java/lang/Exception")
            TRYCATCH(L["D"], L["E"], L["C"], "java/lang/Exception")
            ALOAD(3)
            ASTORE(7)
            ALOAD(4)
            ASTORE(8)
            ALOAD(5)
            ASTORE(9)
            ILOAD(6)
            ISTORE(10)
            ALOAD(9)
            INVOKESTATIC(classNode.name, decryptName, "(Ljava/lang/String;)Ljava/lang/String;")
            LDC_TYPE("L${classNode.name};")
            INVOKEVIRTUAL("java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;")
            INVOKESTATIC(
                "java/lang/invoke/MethodType",
                "fromMethodDescriptorString",
                "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;"
            )
            ASTORE(11)
            LABEL(L["A"])
            ILOAD(10)
            ICONST_1
            IF_ICMPNE(L["D"])
            NEW("java/lang/invoke/ConstantCallSite")
            DUP
            ALOAD(0)
            ALOAD(7)
            INVOKESTATIC(classNode.name, decryptName, "(Ljava/lang/String;)Ljava/lang/String;")
            INVOKESTATIC("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;")
            ALOAD(8)
            INVOKESTATIC(classNode.name, decryptName, "(Ljava/lang/String;)Ljava/lang/String;")
            ALOAD(11)
            INVOKEVIRTUAL(
                "java/lang/invoke/MethodHandles\$Lookup",
                "findVirtual",
                "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"
            )
            ALOAD(2)
            INVOKEVIRTUAL(
                "java/lang/invoke/MethodHandle",
                "asType",
                "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"
            )
            INVOKESPECIAL("java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V")
            LABEL(L["B"])
            ARETURN
            LABEL(L["D"])
            FRAME(
                Opcodes.F_FULL, 12, arrayOf(
                    "java/lang/invoke/MethodHandles\$Lookup",
                    "java/lang/String",
                    "java/lang/invoke/MethodType",
                    "java/lang/String",
                    "java/lang/String",
                    "java/lang/String",
                    Opcodes.INTEGER,
                    "java/lang/String",
                    "java/lang/String",
                    "java/lang/String",
                    Opcodes.INTEGER,
                    "java/lang/invoke/MethodType"
                ), 0, arrayOf()
            )
            NEW("java/lang/invoke/ConstantCallSite")
            DUP
            ALOAD(0)
            ALOAD(7)
            INVOKESTATIC(classNode.name, decryptName, "(Ljava/lang/String;)Ljava/lang/String;")
            INVOKESTATIC("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;")
            ALOAD(8)
            INVOKESTATIC(classNode.name, decryptName, "(Ljava/lang/String;)Ljava/lang/String;")
            ALOAD(11)
            INVOKEVIRTUAL(
                "java/lang/invoke/MethodHandles\$Lookup",
                "findStatic",
                "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"
            )
            ALOAD(2)
            INVOKEVIRTUAL(
                "java/lang/invoke/MethodHandle",
                "asType",
                "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"
            )
            INVOKESPECIAL("java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V")
            LABEL(L["E"])
            ARETURN
            LABEL(L["C"])
            FRAME(Opcodes.F_SAME1, 0, null, 1, arrayOf("java/lang/Exception"))
            POP
            ACONST_NULL
            ARETURN
        }
        MAXS(6, 12)
    }

    private fun createDecryptMethod(classNode: ClassNode, methodName: String, key: Int): MethodNode = method(
        (if (classNode.isInterface) PUBLIC else PRIVATE) + STATIC,
        methodName,
        "(Ljava/lang/String;)Ljava/lang/String;"
    ) {
        INSTRUCTIONS {
            NEW("java/lang/StringBuilder")
            DUP
            INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V")
            ASTORE(1)
            ICONST_0
            ISTORE(2)
            GOTO(L["C"])
            LABEL(L["B"])
            FRAME(Opcodes.F_APPEND, 2, arrayOf("java/lang/StringBuilder", Opcodes.INTEGER), 0)
            ALOAD(1)
            ALOAD(0)
            ILOAD(2)
            INVOKEVIRTUAL("java/lang/String", "charAt", "(I)C")
            LDC(key)
            IXOR
            I2C
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;")
            POP
            IINC(2, 1)
            LABEL(L["C"])
            FRAME(Opcodes.F_SAME, 0, null, 0)
            ILOAD(2)
            ALOAD(0)
            INVOKEVIRTUAL("java/lang/String", "length", "()I")
            IF_ICMPLT(L["B"])
            ALOAD(1)
            INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;")
            ARETURN
        }
        MAXS(3, 3)
    }

    private fun encrypt(string: String, xor: Int): String {
        val stringBuilder = StringBuilder(string.length)
        for (element in string) {
            stringBuilder.append((element.code xor xor).toChar())
        }
        return stringBuilder.toString()
    }

    private data class GeneratedMethod(
        val owner: ClassNode,
        val method: MethodNode
    )
}
