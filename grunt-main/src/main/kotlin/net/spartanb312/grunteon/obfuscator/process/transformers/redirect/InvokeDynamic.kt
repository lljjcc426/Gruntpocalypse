package net.spartanb312.grunteon.obfuscator.process.transformers.redirect

import kotlinx.serialization.Serializable
import net.spartanb312.genesis.kotlin.annotation
import net.spartanb312.genesis.kotlin.clazz
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.NativeCandidate
import net.spartanb312.grunteon.obfuscator.util.DISABLE_INVOKE_DYNAMIC
import net.spartanb312.grunteon.obfuscator.util.GENERATED_CLASS
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
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import org.apache.commons.rng.UniformRandomProvider
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
        val metadataClassName = config.metadataClass.replace('.', '/')
        val heavyMetadata = if (config.heavyProtection && metadataClassName.isNotBlank()) {
            buildHeavyMetadata(instance, metadataClassName)
        } else {
            emptyMap()
        }
        pre {
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }
        val redirectCounter = reducibleScopeValue { MergeableCounter() }
        val helperCounter = reducibleScopeValue { MergeableCounter() }
        val generatedMethods = reducibleScopeValue {
            MergeableObjectList<GeneratedMethod>(FastObjectArrayList())
        }
        val generatedClasses = reducibleScopeValue {
            MergeableObjectList<GeneratedClass>(FastObjectArrayList())
        }

        if (config.heavyProtection && metadataClassName.isNotBlank()) {
            seq {
                instance.workRes.addGeneratedClass(createMetadataAnnotationClass(metadataClassName))
            }
        }

        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            if (classNode.isExcluded(DISABLE_INVOKE_DYNAMIC)) return@parForEachClassesFiltered
            val redirectCounter = redirectCounter.local
            val helperCounter = helperCounter.local
            val generatedMethods = generatedMethods.local
            val generatedClasses = generatedClasses.local
            val classRandom = Xoshiro256PPRandom(getSeed(classNode.name, "InvokeDynamic"))
            val bootstrapName = helperMethodName(classRandom, config, "indy_bootstrap_")
            val decryptName = helperMethodName(classRandom, config, "indy_decrypt_")
            val blankName = helperMethodName(classRandom, config, "indy_blank_")
            val decryptKey = Random(classNode.name.hashCode()).nextInt(8, 0x800)
            val helperOwner = helperOwner(classNode, classRandom, config)
            val helperShape = chooseHelperShape(config, classRandom)
            var helperGenerated = false

            classNode.methods.asSequence()
                .filter { !it.isAbstract && !it.isNative && !it.isInitializer }
                .forEach { method ->
                    if (method.isExcluded(DISABLE_INVOKE_DYNAMIC)) return@forEach
                    val excluded = methodExPredicate.matchedAnyBy(methodFullDesc(classNode, method))
                    if (excluded) return@forEach
                    val randomGen = Xoshiro256PPRandom(getSeed(classNode.name, method.name, method.desc))
                    val invokeCandidates = collectInvokeDynamicCandidates(method, bootstrapName, decryptName)
                    if (invokeCandidates.isEmpty()) return@forEach
                    val selectedInvokes = selectInvokeDynamicTargets(method, invokeCandidates, config.replacePercentage, randomGen)
                    selectedInvokes.forEach { instruction ->

                        val indy = buildInvokeDynamicInsn(
                            classNode = classNode,
                            helperOwner = helperOwner,
                            targetInsn = instruction,
                            bootstrapName = bootstrapName,
                            decryptKey = decryptKey,
                            config = config,
                            randomGen = randomGen,
                            metadata = heavyMetadata[instruction.owner]
                        )
                        method.instructions.insertBefore(instruction, indy)
                        method.instructions.remove(instruction)
                        redirectCounter.add()
                        if (!helperGenerated) {
                            generatedMethods.add(
                                GeneratedMethod(helperOwner, createDecryptMethod(helperOwner, decryptName, decryptKey, config))
                            )
                            generatedMethods.add(
                                GeneratedMethod(helperOwner, createBootstrapMethod(helperOwner, bootstrapName, decryptName, blankName, helperShape, config))
                            )
                            if (config.heavyProtection && metadataClassName.isNotBlank()) {
                                generatedMethods.add(
                                    GeneratedMethod(helperOwner, createHeavyDecryptMethod(helperOwner, decryptName, config))
                                )
                                generatedMethods.add(
                                    GeneratedMethod(helperOwner, createHeavyBootstrapMethod(helperOwner, bootstrapName, decryptName, blankName, helperShape, metadataClassName, config))
                                )
                            }
                            if (helperShape != HelperShape.LIGHT) {
                                generatedMethods.add(
                                    GeneratedMethod(helperOwner, createBlankHelperMethod(helperOwner, blankName, helperShape, config))
                                )
                            }
                            if (helperOwner !== classNode) {
                                generatedClasses.add(GeneratedClass(helperOwner))
                            }
                            helperGenerated = true
                            val baseHelpers = if (config.heavyProtection && metadataClassName.isNotBlank()) 4 else 2
                            helperCounter.add(baseHelpers + if (helperShape != HelperShape.LIGHT) 1 else 0)
                        }
                    }
                }
        }

        seq {
            generatedClasses.global
                .distinctBy { it.classNode.name }
                .forEach { instance.workRes.addGeneratedClass(it.classNode) }
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

    private fun selectInvokeDynamicTargets(
        method: MethodNode,
        candidates: List<MethodInsnNode>,
        replacePercentage: Int,
        randomGen: UniformRandomProvider
    ): List<MethodInsnNode> {
        if (candidates.isEmpty()) return emptyList()
        val normalizedRate = replacePercentage.coerceIn(0, 100)
        if (normalizedRate == 0) return emptyList()
        if (normalizedRate == 100) return candidates

        val weightBoost = when {
            candidates.size >= 8 -> 1.35
            candidates.size >= 4 -> 1.15
            else -> 1.0
        }
        val instructionWeight = when {
            method.instructions.size() >= 80 -> 1.25
            method.instructions.size() >= 30 -> 1.1
            else -> 1.0
        }
        val weightedRate = (normalizedRate * weightBoost * instructionWeight).toInt().coerceIn(1, 100)
        val targetCount = ((candidates.size * weightedRate) / 100.0).toInt()
            .coerceAtLeast(1)
            .coerceAtMost(candidates.size)
        if (targetCount == candidates.size) return candidates

        val selected = BooleanArray(candidates.size)
        var selectedCount = 0
        val anchor = randomGen.nextInt(candidates.size)
        val clusterWidth = targetCount.coerceAtMost(candidates.size)
        var left = (anchor - clusterWidth / 2).coerceAtLeast(0)
        var right = (left + clusterWidth - 1).coerceAtMost(candidates.lastIndex)
        if (right - left + 1 < clusterWidth) {
            left = (right - clusterWidth + 1).coerceAtLeast(0)
        }
        for (index in left..right) {
            if (!selected[index]) {
                selected[index] = true
                selectedCount++
            }
            if (selectedCount >= targetCount) break
        }
        while (selectedCount < targetCount) {
            val index = randomGen.nextInt(candidates.size)
            if (!selected[index]) {
                selected[index] = true
                selectedCount++
            }
        }
        return FastObjectArrayList<MethodInsnNode>(targetCount).apply {
            candidates.indices.forEach { index ->
                if (selected[index]) add(candidates[index])
            }
        }
    }

    private fun collectInvokeDynamicCandidates(
        method: MethodNode,
        bootstrapName: String,
        decryptName: String
    ): List<MethodInsnNode> {
        val candidates = FastObjectArrayList<MethodInsnNode>()
        var cursor = method.instructions.first
        while (cursor != null) {
            val call = cursor as? MethodInsnNode
            if (call != null &&
                call.opcode != Opcodes.INVOKESPECIAL &&
                call.opcode != Opcodes.INVOKEINTERFACE &&
                call.name != bootstrapName &&
                call.name != decryptName
            ) {
                candidates.add(call)
            }
            cursor = cursor.next
        }
        return candidates
    }

    private fun buildInvokeDynamicInsn(
        classNode: ClassNode,
        helperOwner: ClassNode,
        targetInsn: MethodInsnNode,
        bootstrapName: String,
        decryptKey: Int,
        config: Config,
        randomGen: UniformRandomProvider,
        metadata: HeavyMetadata?
    ): InvokeDynamicInsnNode {
        val owner = encrypt(targetInsn.owner.replace('/', '.'), decryptKey)
        val name = encrypt(targetInsn.name, decryptKey)
        val desc = encrypt(targetInsn.desc, decryptKey)
        val invokeMode = if (targetInsn.opcode == Opcodes.INVOKESTATIC) 0 else 1
        val indyName = if (config.massiveRandomBlank) randomBlankName(randomGen) else targetInsn.name
        val indyDesc = if (targetInsn.opcode == Opcodes.INVOKESTATIC) {
            targetInsn.desc
        } else {
            targetInsn.desc.replace("(", "(Ljava/lang/Object;")
        }
        val metadataIndex = metadata?.indexOf(targetInsn.name, targetInsn.desc) ?: -1
        if (config.heavyProtection && metadata != null && metadataIndex >= 0) {
            return InvokeDynamicInsnNode(
                indyName,
                indyDesc,
                Handle(
                    Opcodes.H_INVOKESTATIC,
                    helperOwner.name,
                    bootstrapName,
                    MethodType.methodType(
                        CallSite::class.java,
                        MethodHandles.Lookup::class.java,
                        String::class.java,
                        MethodType::class.java,
                        String::class.java,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    ).toMethodDescriptorString(),
                    false
                ),
                owner,
                metadataIndex,
                decryptKey,
                invokeMode
            )
        }
        return InvokeDynamicInsnNode(
            indyName,
            indyDesc,
            Handle(
                Opcodes.H_INVOKESTATIC,
                helperOwner.name,
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

    private fun createBootstrapMethod(
        classNode: ClassNode,
        methodName: String,
        decryptName: String,
        blankName: String,
        helperShape: HelperShape,
        config: Config
    ): MethodNode = method(
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
    }.also {
        applyHelperShapePrelude(it, classNode, blankName, helperShape)
        if (config.reobfuscate) applyHelperReobf(it, config.enhancedFlowReobf)
    }

    private fun createDecryptMethod(classNode: ClassNode, methodName: String, key: Int, config: Config): MethodNode = method(
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
    }.also {
        if (config.heavyProtection) {
            applyHeavyDecryptNoise(it, key, Random(classNode.name.hashCode() xor methodName.hashCode()))
        }
        if (config.reobfuscate) applyHelperReobf(it, config.enhancedFlowReobf)
    }

    private fun createHeavyDecryptMethod(classNode: ClassNode, methodName: String, config: Config): MethodNode = method(
        (if (classNode.isInterface) PUBLIC else PRIVATE) + STATIC,
        methodName,
        "(Ljava/lang/String;I)Ljava/lang/String;"
    ) {
        INSTRUCTIONS {
            NEW("java/lang/StringBuilder")
            DUP
            INVOKESPECIAL("java/lang/StringBuilder", "<init>", "()V")
            ASTORE(2)
            ICONST_0
            ISTORE(3)
            GOTO(L["CHECK"])
            LABEL(L["LOOP"])
            FRAME(Opcodes.F_APPEND, 2, arrayOf("java/lang/StringBuilder", Opcodes.INTEGER), 0, null)
            ALOAD(2)
            ALOAD(0)
            ILOAD(3)
            INVOKEVIRTUAL("java/lang/String", "charAt", "(I)C")
            ILOAD(1)
            IXOR
            I2C
            INVOKEVIRTUAL("java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;")
            POP
            IINC(3, 1)
            LABEL(L["CHECK"])
            FRAME(Opcodes.F_SAME, 0, null, 0, null)
            ILOAD(3)
            ALOAD(0)
            INVOKEVIRTUAL("java/lang/String", "length", "()I")
            IF_ICMPLT(L["LOOP"])
            ALOAD(2)
            INVOKEVIRTUAL("java/lang/StringBuilder", "toString", "()Ljava/lang/String;")
            ARETURN
        }
        MAXS(3, 4)
    }.also {
        if (config.heavyProtection) {
            applyHeavyDecryptNoise(it, classNode.name.hashCode(), Random(classNode.name.hashCode() xor methodName.hashCode()))
        }
        if (config.reobfuscate) applyHelperReobf(it, config.enhancedFlowReobf)
    }

    private fun createHeavyBootstrapMethod(
        classNode: ClassNode,
        methodName: String,
        decryptName: String,
        blankName: String,
        helperShape: HelperShape,
        metadataClassName: String,
        config: Config
    ): MethodNode = method(
        (if (classNode.isInterface) PUBLIC else PRIVATE) + STATIC,
        methodName,
        MethodType.methodType(
            CallSite::class.java,
            MethodHandles.Lookup::class.java,
            String::class.java,
            MethodType::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ).toMethodDescriptorString()
    ) {
        INSTRUCTIONS {
            TRYCATCH(L["TRY0"], L["TRY1"], L["FAIL"], "java/lang/Exception")
            ALOAD(3)
            ILOAD(5)
            INVOKESTATIC(classNode.name, decryptName, "(Ljava/lang/String;I)Ljava/lang/String;")
            INVOKESTATIC("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;")
            ASTORE(7)
            ALOAD(7)
            LDC_TYPE("L$metadataClassName;")
            INVOKEVIRTUAL("java/lang/Class", "getAnnotation", "(Ljava/lang/Class;)Ljava/lang/annotation/Annotation;")
            CHECKCAST(metadataClassName)
            ASTORE(8)
            ALOAD(8)
            IFNULL(L["FAIL_NULL"])
            ALOAD(8)
            INVOKEINTERFACE(metadataClassName, "d1", "()[Ljava/lang/String;")
            ASTORE(9)
            ALOAD(8)
            INVOKEINTERFACE(metadataClassName, "d2", "()[Ljava/lang/String;")
            ASTORE(10)
            ALOAD(8)
            INVOKEINTERFACE(metadataClassName, "mv", "()I")
            POP
            ALOAD(8)
            INVOKEINTERFACE(metadataClassName, "flags", "()I")
            POP
            ALOAD(8)
            INVOKEINTERFACE(metadataClassName, "salt", "()Ljava/lang/String;")
            ILOAD(5)
            INVOKESTATIC(classNode.name, decryptName, "(Ljava/lang/String;I)Ljava/lang/String;")
            POP
            ALOAD(9)
            ILOAD(4)
            AALOAD
            ILOAD(5)
            INVOKESTATIC(classNode.name, decryptName, "(Ljava/lang/String;I)Ljava/lang/String;")
            ASTORE(11)
            ALOAD(10)
            ILOAD(4)
            AALOAD
            ILOAD(5)
            INVOKESTATIC(classNode.name, decryptName, "(Ljava/lang/String;I)Ljava/lang/String;")
            ASTORE(12)
            ALOAD(12)
            LDC_TYPE("L${classNode.name};")
            INVOKEVIRTUAL("java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;")
            INVOKESTATIC(
                "java/lang/invoke/MethodType",
                "fromMethodDescriptorString",
                "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;"
            )
            ASTORE(13)
            LABEL(L["TRY0"])
            ILOAD(6)
            ICONST_1
            IF_ICMPNE(L["STATIC"])
            NEW("java/lang/invoke/ConstantCallSite")
            DUP
            ALOAD(0)
            ALOAD(7)
            ALOAD(11)
            ALOAD(13)
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
            LABEL(L["TRY1"])
            ARETURN
            LABEL(L["STATIC"])
            FRAME(
                Opcodes.F_FULL, 14, arrayOf(
                    "java/lang/invoke/MethodHandles\$Lookup",
                    "java/lang/String",
                    "java/lang/invoke/MethodType",
                    "java/lang/String",
                    Opcodes.INTEGER,
                    Opcodes.INTEGER,
                    Opcodes.INTEGER,
                    "java/lang/Class",
                    metadataClassName,
                    "[Ljava/lang/String;",
                    "[Ljava/lang/String;",
                    "java/lang/String",
                    "java/lang/String",
                    "java/lang/invoke/MethodType"
                ), 0, arrayOf()
            )
            NEW("java/lang/invoke/ConstantCallSite")
            DUP
            ALOAD(0)
            ALOAD(7)
            ALOAD(11)
            ALOAD(13)
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
            ARETURN
            LABEL(L["FAIL_NULL"])
            ACONST_NULL
            ARETURN
            LABEL(L["FAIL"])
            FRAME(Opcodes.F_SAME1, 0, null, 1, arrayOf("java/lang/Exception"))
            POP
            ACONST_NULL
            ARETURN
        }
        MAXS(6, 14)
    }.also {
        applyHelperShapePrelude(it, classNode, blankName, helperShape)
        if (config.reobfuscate) applyHelperReobf(it, config.enhancedFlowReobf)
    }

    private fun buildHeavyMetadata(instance: Grunteon, metadataClassName: String): Map<String, HeavyMetadata> {
        val heavyMap = linkedMapOf<String, HeavyMetadata>()
        instance.workRes.inputClassCollection.forEach { classNode ->
            val methods = classNode.methods
                .filterNot { it.isInitializer }
                .map { HeavyMetadataEntry(it.name, it.desc) }
            if (methods.isEmpty()) return@forEach
            val key = Random(classNode.name.hashCode()).nextInt(8, 0x800)
            val metadata = HeavyMetadata(
                key = key,
                entries = methods,
                index = methods.mapIndexed { index, entry -> "${entry.name}<>${entry.desc}" to index }.toMap()
            )
            classNode.visibleAnnotations = classNode.visibleAnnotations ?: mutableListOf()
            classNode.visibleAnnotations.add(
                AnnotationNode("L$metadataClassName;").apply {
                    values = mutableListOf(
                        "mv", 100,
                        "salt", encrypt(classNode.name, key),
                        "flags", methods.size,
                        "d1", methods.map { encrypt(it.name, key) },
                        "d2", methods.map { encrypt(it.desc, key) }
                    )
                }
            )
            heavyMap[classNode.name] = metadata
        }
        return heavyMap
    }

    private fun createMetadataAnnotationClass(metadataClassName: String): ClassNode = clazz(
        access = PUBLIC + ABSTRACT + INTERFACE + ANNOTATION,
        name = metadataClassName,
        superName = "java/lang/Object",
        interfaces = listOf("java/lang/annotation/Annotation"),
        signature = null,
        version = Java8
    ) {
        +annotation("Ljava/lang/annotation/Retention;") {
            ENUM("value", "Ljava/lang/annotation/RetentionPolicy;", "RUNTIME")
        }
        +method(PUBLIC + ABSTRACT, "mv", "()I", null, null) {
            ANNOTATIONDEFAULT { this[null] = 0 }
        }
        +method(PUBLIC + ABSTRACT, "salt", "()Ljava/lang/String;", null, null) {
            ANNOTATIONDEFAULT { this[null] = "" }
        }
        +method(PUBLIC + ABSTRACT, "flags", "()I", null, null) {
            ANNOTATIONDEFAULT { this[null] = 0 }
        }
        +method(PUBLIC + ABSTRACT, "d1", "()[Ljava/lang/String;", null, null) {
            ANNOTATIONDEFAULT { ARRAY(null) }
        }
        +method(PUBLIC + ABSTRACT, "d2", "()[Ljava/lang/String;", null, null) {
            ANNOTATIONDEFAULT { ARRAY(null) }
        }
    }.apply {
        appendAnnotation(GENERATED_CLASS)
    }

    private fun helperOwner(classNode: ClassNode, random: UniformRandomProvider, config: Config): ClassNode {
        return classNode
    }

    private fun chooseHelperShape(config: Config, random: UniformRandomProvider): HelperShape {
        return when {
            config.heavyProtection && config.enhancedFlowReobf -> HelperShape.HEAVY
            config.heavyProtection || config.massiveRandomBlank || config.reobfuscate -> {
                if (random.nextBoolean()) HelperShape.BALANCED else HelperShape.HEAVY
            }
            else -> HelperShape.LIGHT
        }
    }

    private fun helperMethodName(random: UniformRandomProvider, config: Config, prefix: String): String {
        return if (config.massiveRandomBlank) {
            randomBlankName(random)
        } else {
            prefix + random.getRandomString(10)
        }
    }

    private fun randomBlankName(random: UniformRandomProvider): String {
        val alphabet = charArrayOf('_', '$', 'I', 'l', '1', 'O', '0')
        return buildString(12) {
            repeat(12) {
                append(alphabet[random.nextInt(alphabet.size)])
            }
        }
    }

    private fun createBlankHelperMethod(
        classNode: ClassNode,
        methodName: String,
        helperShape: HelperShape,
        config: Config
    ): MethodNode = method(
        (if (classNode.isInterface) PUBLIC else PRIVATE) + STATIC,
        methodName,
        "()V"
    ) {}.also {
        when (helperShape) {
            HelperShape.BALANCED -> {
                it.instructions.add(LdcInsnNode(classNode.name.hashCode()))
                it.instructions.add(InsnNode(Opcodes.POP))
                it.instructions.add(InsnNode(Opcodes.RETURN))
                it.maxStack = 1
                it.maxLocals = 0
            }
            HelperShape.HEAVY -> {
                val check = LabelNode()
                val end = LabelNode()
                it.instructions.add(InsnNode(Opcodes.ICONST_0))
                it.instructions.add(VarInsnNode(Opcodes.ISTORE, 0))
                it.instructions.add(check)
                it.instructions.add(VarInsnNode(Opcodes.ILOAD, 0))
                it.instructions.add(InsnNode(Opcodes.ICONST_1))
                it.instructions.add(JumpInsnNode(Opcodes.IF_ICMPGE, end))
                it.instructions.add(IincInsnNode(0, 1))
                it.instructions.add(JumpInsnNode(Opcodes.GOTO, check))
                it.instructions.add(end)
                it.instructions.add(InsnNode(Opcodes.RETURN))
                it.maxStack = 2
                it.maxLocals = 1
            }
            HelperShape.LIGHT -> {
                it.instructions.add(InsnNode(Opcodes.RETURN))
                it.maxStack = 0
                it.maxLocals = 0
            }
        }
        if (config.reobfuscate) applyHelperReobf(it, config.enhancedFlowReobf)
    }

    private fun applyHelperShapePrelude(
        method: MethodNode,
        classNode: ClassNode,
        blankName: String,
        helperShape: HelperShape
    ) {
        if (helperShape == HelperShape.LIGHT) return
        val first = method.instructions.first ?: return
        when (helperShape) {
            HelperShape.BALANCED -> {
                method.instructions.insertBefore(first, MethodInsnNode(Opcodes.INVOKESTATIC, classNode.name, blankName, "()V", false))
                method.maxStack = maxOf(method.maxStack, 1)
            }
            HelperShape.HEAVY -> {
                val skip = LabelNode()
                method.instructions.insertBefore(first, skip)
                method.instructions.insertBefore(first, MethodInsnNode(Opcodes.INVOKESTATIC, classNode.name, blankName, "()V", false))
                method.instructions.insertBefore(first, JumpInsnNode(Opcodes.IFLT, skip))
                method.instructions.insertBefore(first, InsnNode(Opcodes.POP))
                method.instructions.insertBefore(
                    first,
                    MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "java/lang/invoke/MethodType",
                        "methodType",
                        "(Ljava/lang/Class;)Ljava/lang/invoke/MethodType;",
                        false
                    )
                )
                method.instructions.insertBefore(first, LdcInsnNode(Type.getType("Ljava/lang/Object;")))
                method.instructions.insertBefore(first, InsnNode(Opcodes.POP))
                method.instructions.insertBefore(first, InsnNode(Opcodes.DUP))
                method.instructions.insertBefore(first, LdcInsnNode(classNode.name.length))
                method.maxStack = maxOf(method.maxStack, 2)
            }
            HelperShape.LIGHT -> Unit
        }
    }

    private fun applyHeavyDecryptNoise(method: MethodNode, key: Int, random: Random) {
        val start = method.instructions.first ?: return
        method.instructions.insertBefore(start, LdcInsnNode(key xor random.nextInt()))
        method.instructions.insertBefore(start, InsnNode(Opcodes.POP))
    }

    private fun applyHelperReobf(method: MethodNode, enhanced: Boolean) {
        val first = method.instructions.first ?: return
        val skip = LabelNode()
        method.instructions.insertBefore(first, LdcInsnNode(if (enhanced) 2 else 1))
        method.instructions.insertBefore(first, JumpInsnNode(Opcodes.IFLT, skip))
        if (enhanced) {
            method.instructions.insertBefore(first, InsnNode(Opcodes.NOP))
            method.instructions.insertBefore(first, LdcInsnNode(0))
            method.instructions.insertBefore(first, InsnNode(Opcodes.POP))
        }
        method.instructions.insertBefore(first, skip)
        method.maxStack = maxOf(method.maxStack, if (enhanced) 2 else 1)
    }

    private fun encrypt(string: String, xor: Int): String {
        val stringBuilder = StringBuilder(string.length)
        for (element in string) {
            stringBuilder.append((element.code xor xor).toChar())
        }
        return stringBuilder.toString()
    }

    private data class HeavyMetadataEntry(
        val name: String,
        val desc: String
    )

    private data class HeavyMetadata(
        val key: Int,
        val entries: List<HeavyMetadataEntry>,
        val index: Map<String, Int>
    ) {
        fun indexOf(name: String, desc: String): Int {
            return index["$name<>$desc"] ?: -1
        }
    }

    private data class GeneratedMethod(
        val owner: ClassNode,
        val method: MethodNode
    )

    private data class GeneratedClass(
        val classNode: ClassNode
    )

    private enum class HelperShape {
        LIGHT,
        BALANCED,
        HEAVY
    }
}
