package net.spartanb312.grunteon.obfuscator.process.transformers.controlflow

import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.NativeCandidate
import net.spartanb312.grunteon.obfuscator.util.DISABLE_CONTROL_FLOW
import net.spartanb312.grunteon.obfuscator.util.GENERATED_CLASS
import net.spartanb312.grunteon.obfuscator.util.GENERATED_METHOD
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.appendAnnotation
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isInitializer
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.extensions.methodFullDesc
import net.spartanb312.grunteon.obfuscator.util.filters.NamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.buildMethodNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.isExcluded
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import net.spartanb312.grunteon.obfuscator.util.getRandomString
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

class Controlflow : Transformer<Controlflow.Config>(
    name = enText("process.controlflow.controlflow", "Controlflow"),
    category = Category.Controlflow,
    description = enText(
        "process.controlflow.controlflow.desc",
        "Rewrite jumps into opaque predicates and switch-based dispatches"
    )
) {
    @Serializable
    data class Config(
        @SettingDesc(enText = "Specify class include/exclude rules")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc(enText = "Transformation intensity")
        @IntRangeVal(min = 1, max = 3)
        val intensity: Int = 1,
        @SettingDesc(enText = "Place controlflow before encryption in the web adapter")
        val executeBeforeEncrypt: Boolean = false,
        @SettingDesc(enText = "Extract switches")
        val switchExtractor: Boolean = true,
        @SettingDesc(enText = "Switch extract rate")
        @IntRangeVal(min = 0, max = 100)
        val extractRate: Int = 30,
        @SettingDesc(enText = "Replace goto with bogus conditions")
        val bogusConditionJump: Boolean = true,
        @SettingDesc(enText = "Goto replacement rate")
        @IntRangeVal(min = 0, max = 100)
        val gotoReplaceRate: Int = 80,
        @SettingDesc(enText = "Wrap compare jumps")
        val mangledCompareJump: Boolean = true,
        @SettingDesc(enText = "If replacement rate")
        @IntRangeVal(min = 0, max = 100)
        val ifReplaceRate: Int = 50,
        @SettingDesc(enText = "If compare replacement rate")
        @IntRangeVal(min = 0, max = 100)
        val ifICompareReplaceRate: Int = 100,
        @SettingDesc(enText = "Protect switch instructions")
        val switchProtect: Boolean = true,
        @SettingDesc(enText = "Switch protect rate")
        @IntRangeVal(min = 0, max = 100)
        val protectRate: Int = 30,
        @SettingDesc(enText = "Replace goto with table switches")
        val tableSwitchJump: Boolean = true,
        @SettingDesc(enText = "Mutate jumps into table switches")
        val mutateJumps: Boolean = true,
        @SettingDesc(enText = "Mutate jump rate")
        @IntRangeVal(min = 0, max = 100)
        val mutateRate: Int = 10,
        @SettingDesc(enText = "Switch replacement rate")
        @IntRangeVal(min = 0, max = 100)
        val switchReplaceRate: Int = 30,
        @SettingDesc(enText = "Max switch cases")
        @IntRangeVal(min = 1, max = 10)
        val maxSwitchCase: Int = 5,
        @SettingDesc(enText = "Reverse existed if")
        val reverseExistedIf: Boolean = true,
        @SettingDesc(enText = "Reverse chance")
        @IntRangeVal(min = 0, max = 100)
        val reverseChance: Int = 50,
        @SettingDesc(enText = "Generate trapped switch cases")
        val trappedSwitchCase: Boolean = true,
        @SettingDesc(enText = "Trap chance")
        @IntRangeVal(min = 0, max = 100)
        val trapChance: Int = 50,
        @SettingDesc(enText = "Build arithmetic expressions")
        val arithmeticExprBuilder: Boolean = true,
        @SettingDesc(enText = "Builder intensity")
        @IntRangeVal(min = 1, max = 3)
        val builderIntensity: Int = 1,
        @SettingDesc(enText = "Insert junk builder parameters")
        val junkBuilderParameter: Boolean = true,
        @SettingDesc(enText = "Mark generated builder with native annotation")
        val builderNativeAnnotation: Boolean = false,
        @SettingDesc(enText = "Use local variable builders")
        val useLocalVar: Boolean = true,
        @SettingDesc(enText = "Generate junk code")
        val junkCode: Boolean = true,
        @SettingDesc(enText = "Max junk code blocks")
        @IntRangeVal(min = 0, max = 5)
        val maxJunkCode: Int = 2,
        @SettingDesc(enText = "Expanded junk code")
        val expandedJunkCode: Boolean = true,
        @SettingDesc(enText = "Specify method exclusions.")
        val exclusion: List<String> = listOf(
            "net/dummy/**",
            "net/dummy/Class",
            "net/dummy/Class.method",
            "net/dummy/Class.method()V"
        )
    ) : TransformerConfig

    private lateinit var methodExPredicate: NamePredicates
    private var junkCallPool: List<JunkCallMethod> = emptyList()

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        // Controlflow pre-work reads shared class collections and helper pools.
        // Flush any pending parallel stages before touching that state.
        barrier()
        pre {
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
            junkCallPool = buildJunkCallPool(config)
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        val generatedClasses = reducibleScopeValue {
            MergeableObjectList(ObjectArrayList<ClassNode>())
        }
        val builderDummyTypes = globalScopeValue {
            instance.workRes.inputClassCollection
                .asSequence()
                .filter { (it.access and Opcodes.ACC_PUBLIC) != 0 && (it.access and Opcodes.ACC_ANNOTATION) == 0 }
                .map { it.name }
                .toList()
        }
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            if (classNode.isExcluded(DISABLE_CONTROL_FLOW)) return@parForEachClassesFiltered
            val counter = counter.local
            val generatedClasses = generatedClasses.local
            val dummyTypes = builderDummyTypes.global
            var builderOwner: ClassNode? = null
            fun resolveBuilderOwner(random: UniformRandomProvider): ClassNode {
                builderOwner?.let { return it }
                val owner = ClassNode().apply {
                    version = classNode.version
                    access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER
                    name = "${classNode.name}\$processor_${random.getRandomString(8)}"
                    superName = "java/lang/Object"
                    methods = mutableListOf(MethodNode(
                        Opcodes.ACC_PUBLIC,
                        "<init>",
                        "()V",
                        null,
                        null
                    ).apply {
                        instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
                        instructions.add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false))
                        instructions.add(InsnNode(Opcodes.RETURN))
                        maxLocals = 1
                        maxStack = 1
                    })
                    appendAnnotation(GENERATED_CLASS)
                }
                generatedClasses.add(owner)
                builderOwner = owner
                return owner
            }
            classNode.methods.asSequence()
                .filter { !it.isAbstract && !it.isNative && it.instructions != null && it.instructions.size() > 0 }
                .forEach { method ->
                    if (method.isExcluded(DISABLE_CONTROL_FLOW)) return@forEach
                    if (methodExPredicate.matchedAnyBy(methodFullDesc(classNode, method))) return@forEach
                    val random = Xoshiro256PPRandom(getSeed(classNode.name, method.name, method.desc, "Controlflow"))
                    counter.add(transformMethod(classNode, method, config, random, dummyTypes, ::resolveBuilderOwner))
                }
        }
        seq {
            generatedClasses.global
                .distinctBy { it.name }
                .forEach(instance.workRes::addGeneratedClass)
        }
        post {
            Logger.info(" - Controlflow:")
            Logger.info("    Replaced ${counter.global.get()} jumps")
        }
    }

    context(instance: Grunteon)
    private fun buildJunkCallPool(config: Config): List<JunkCallMethod> {
        val source = if (config.expandedJunkCode) {
            instance.workRes.allClassCollection
        } else {
            instance.workRes.inputClassCollection
        }
        return ObjectArrayList<JunkCallMethod>().apply {
            source.forEach { owner ->
                if ((owner.access and Opcodes.ACC_PUBLIC) == 0) return@forEach
                owner.methods.forEach { method ->
                    if ((method.access and Opcodes.ACC_PUBLIC) == 0 ||
                        (method.access and Opcodes.ACC_STATIC) == 0 ||
                        method.isAbstract ||
                        method.isNative ||
                        method.isInitializer
                    ) return@forEach
                    val argTypes = Type.getArgumentTypes(method.desc)
                    if (!argTypes.all(::isJunkFriendlyType)) return@forEach
                    val returnType = Type.getReturnType(method.desc)
                    if (!isJunkFriendlyType(returnType) && returnType.sort != Type.VOID) return@forEach
                    add(JunkCallMethod(owner.name, method.name, method.desc, argTypes, returnType))
                }
            }
        }
    }

    private fun transformMethod(
        classNode: ClassNode,
        method: MethodNode,
        config: Config,
        random: UniformRandomProvider,
        builderDummyTypes: List<String>,
        builderOwnerProvider: (UniformRandomProvider) -> ClassNode
    ): Int {
        var replaced = 0
        val returnType = Type.getReturnType(method.desc)
        val intensity = config.intensity.coerceIn(1, 3)
        val rounds = intensity.coerceAtMost(2)
        repeat(rounds) {
            if (config.switchProtect) {
                for (instruction in method.instructions.toArray()) {
                    val replacement = when (instruction) {
                        is TableSwitchInsnNode -> {
                            if (random.nextInt(100) >= config.protectRate.coerceIn(0, 100)) null else createProtectedSwitch(instruction, random)
                        }

                        is LookupSwitchInsnNode -> {
                            if (random.nextInt(100) >= config.protectRate.coerceIn(0, 100)) null else createProtectedSwitch(instruction, random)
                        }

                        else -> null
                    } ?: continue
                    method.instructions.insertBefore(instruction, replacement)
                    method.instructions.remove(instruction)
                    replaced++
                }
            }

            if (config.mutateJumps) {
                for (instruction in method.instructions.toArray()) {
                    val jump = instruction as? JumpInsnNode ?: continue
                    if (!isConditionalJump(jump.opcode) || jump.opcode == Opcodes.GOTO) continue
                    if (random.nextInt(100) >= config.mutateRate.coerceIn(0, 100)) continue
                    method.instructions.insertBefore(jump, createMutatedJump(jump, random))
                    method.instructions.remove(jump)
                    replaced++
                }
            }

            if (config.switchExtractor) {
                for (instruction in method.instructions.toArray()) {
                    val replacement = when (instruction) {
                        is TableSwitchInsnNode -> {
                            if (random.nextInt(100) >= config.extractRate.coerceIn(0, 100)) null else createExtractedSwitch(instruction, method)
                        }

                        is LookupSwitchInsnNode -> {
                            if (random.nextInt(100) >= config.extractRate.coerceIn(0, 100)) null else createExtractedSwitch(instruction, method)
                        }

                        else -> null
                    } ?: continue
                    method.instructions.insertBefore(instruction, replacement)
                    method.instructions.remove(instruction)
                    replaced++
                }
            }

            if (config.bogusConditionJump) {
                for (instruction in method.instructions.toArray()) {
                    val jump = instruction as? JumpInsnNode ?: continue
                    if (jump.opcode != Opcodes.GOTO) continue
                    if (random.nextInt(100) >= config.gotoReplaceRate.coerceIn(0, 100)) continue
                    method.instructions.insertBefore(jump, createOpaqueGoto(classNode, method, jump.label, random, config, builderDummyTypes, builderOwnerProvider, returnType))
                    method.instructions.remove(jump)
                    replaced++
                }
            }

            if (config.mangledCompareJump) {
                for (instruction in method.instructions.toArray()) {
                    val jump = instruction as? JumpInsnNode ?: continue
                    if (!isConditionalJump(jump.opcode)) continue
                    val rate = if (isIfCompareJump(jump.opcode)) config.ifICompareReplaceRate else config.ifReplaceRate
                    if (random.nextInt(100) >= rate.coerceIn(0, 100)) continue
                    method.instructions.insertBefore(jump, createWrappedJump(classNode, method, jump, random, config, builderDummyTypes, builderOwnerProvider))
                    method.instructions.remove(jump)
                    replaced++
                }
            }

            if (config.tableSwitchJump) {
                for (instruction in method.instructions.toArray()) {
                    val jump = instruction as? JumpInsnNode ?: continue
                    if (jump.opcode != Opcodes.GOTO) continue
                    if (random.nextInt(100) >= config.switchReplaceRate.coerceIn(0, 100)) continue
                    method.instructions.insertBefore(jump, createSwitchGoto(classNode, method, jump.label, random, config, builderDummyTypes, builderOwnerProvider, returnType))
                    method.instructions.remove(jump)
                    replaced++
                }
            }
        }

        return replaced
    }

    private fun createExtractedSwitch(switchInsn: TableSwitchInsnNode, method: MethodNode): InsnList {
        val slot = method.maxLocals++
        val min = switchInsn.min
        val labels = switchInsn.labels.toTypedArray()
        return InsnList().apply {
            add(VarInsnNode(Opcodes.ISTORE, slot))
            for (index in labels.indices) {
                add(VarInsnNode(Opcodes.ILOAD, slot))
                add(LdcInsnNode(min + index))
                add(JumpInsnNode(Opcodes.IF_ICMPEQ, labels[index]))
            }
            add(JumpInsnNode(Opcodes.GOTO, switchInsn.dflt))
        }
    }

    private fun createExtractedSwitch(switchInsn: LookupSwitchInsnNode, method: MethodNode): InsnList {
        val slot = method.maxLocals++
        val keys = switchInsn.keys
        val labels = switchInsn.labels
        return InsnList().apply {
            add(VarInsnNode(Opcodes.ISTORE, slot))
            for (index in keys.indices) {
                add(VarInsnNode(Opcodes.ILOAD, slot))
                add(LdcInsnNode(keys[index]))
                add(JumpInsnNode(Opcodes.IF_ICMPEQ, labels[index]))
            }
            add(JumpInsnNode(Opcodes.GOTO, switchInsn.dflt))
        }
    }

    private fun createProtectedSwitch(switchInsn: TableSwitchInsnNode, random: UniformRandomProvider): InsnList {
        val min = switchInsn.min
        val pairs = switchInsn.labels.mapIndexed { index, label -> (min + index) to label }
        return createProtectedSwitch(pairs, switchInsn.dflt, random)
    }

    private fun createProtectedSwitch(switchInsn: LookupSwitchInsnNode, random: UniformRandomProvider): InsnList {
        val pairs = switchInsn.keys.mapIndexed { index, key -> key to switchInsn.labels[index] }
        return createProtectedSwitch(pairs, switchInsn.dflt, random)
    }

    private fun createProtectedSwitch(
        pairs: List<Pair<Int, LabelNode>>,
        defaultLabel: LabelNode,
        random: UniformRandomProvider
    ): InsnList {
        val magic = random.nextInt()
        val sorted = pairs
            .map { (key, target) -> Triple(key xor magic, LabelNode(), target) }
            .sortedBy { it.first }
        val dispatchDefault = LabelNode()
        return InsnList().apply {
            add(LdcInsnNode(magic))
            add(InsnNode(Opcodes.IXOR))
            add(LookupSwitchInsnNode(dispatchDefault, sorted.map { it.first }.toIntArray(), sorted.map { it.second }.toTypedArray()))
            add(dispatchDefault)
            add(JumpInsnNode(Opcodes.GOTO, defaultLabel))
            sorted.forEach { (_, branchLabel, targetLabel) ->
                add(branchLabel)
                add(JumpInsnNode(Opcodes.GOTO, targetLabel))
            }
        }
    }

    private fun createWrappedJump(
        classNode: ClassNode,
        method: MethodNode,
        jump: JumpInsnNode,
        random: UniformRandomProvider,
        config: Config,
        builderDummyTypes: List<String>,
        builderOwnerProvider: (UniformRandomProvider) -> ClassNode
    ): InsnList {
        val returnType = Type.getReturnType(method.desc)
        if (config.tableSwitchJump && random.nextInt(100) < config.switchReplaceRate.coerceIn(0, 100)) {
            return if (usesObjectOrNullCompare(jump.opcode)) {
                createCompareLookupBridge(
                    classNode,
                    method,
                    jump,
                    random,
                    config,
                    builderDummyTypes,
                    builderOwnerProvider,
                    returnType
                )
            } else {
                createCompareSwitchBridge(
                    classNode,
                    method,
                    jump,
                    random,
                    config,
                    builderDummyTypes,
                    builderOwnerProvider,
                    returnType
                )
            }
        }
        val delegateLabel = LabelNode()
        val continueLabel = LabelNode()
        val reversedOpcode = reversedIfOpcode(jump.opcode)
        return InsnList().apply {
            if (config.reverseExistedIf && reversedOpcode != null && random.nextInt(100) < config.reverseChance) {
                add(JumpInsnNode(reversedOpcode, continueLabel))
                add(createOpaqueGoto(classNode, method, jump.label, random, config, builderDummyTypes, builderOwnerProvider, returnType))
                add(continueLabel)
            } else {
                add(JumpInsnNode(jump.opcode, delegateLabel))
                add(JumpInsnNode(Opcodes.GOTO, continueLabel))
                add(delegateLabel)
                add(createOpaqueGoto(classNode, method, jump.label, random, config, builderDummyTypes, builderOwnerProvider, returnType))
                add(continueLabel)
            }
        }
    }

    private fun createCompareSwitchBridge(
        classNode: ClassNode,
        method: MethodNode,
        jump: JumpInsnNode,
        random: UniformRandomProvider,
        config: Config,
        builderDummyTypes: List<String>,
        builderOwnerProvider: (UniformRandomProvider) -> ClassNode,
        returnType: Type
    ): InsnList {
        val caseCount = rangedInt(random, 2, config.maxSwitchCase.coerceAtLeast(2) + 1)
        val labels = Array(caseCount) { LabelNode() }
        val defaultLabel = LabelNode()
        val continueLabel = LabelNode()
        val trueLabel = LabelNode()
        val dispatchLabel = LabelNode()
        val startCase = rangedInt(random, 0, 512)
        val trueIndex = random.nextInt(caseCount)
        val falseIndex = (trueIndex + rangedInt(random, 1, caseCount)) % caseCount
        val key = rangedInt(random, 16, 0x4000)
        return InsnList().apply {
            add(JumpInsnNode(jump.opcode, trueLabel))
            add(buildArithmeticInt(classNode, method, (startCase + falseIndex) xor key, random, config, builderDummyTypes, builderOwnerProvider))
            add(JumpInsnNode(Opcodes.GOTO, dispatchLabel))
            add(trueLabel)
            add(buildArithmeticInt(classNode, method, (startCase + trueIndex) xor key, random, config, builderDummyTypes, builderOwnerProvider))
            add(dispatchLabel)
            add(buildArithmeticInt(classNode, method, key, random, config, builderDummyTypes, builderOwnerProvider))
            add(InsnNode(Opcodes.IXOR))
            add(TableSwitchInsnNode(startCase, startCase + caseCount - 1, defaultLabel, *labels))
            labels.forEachIndexed { index, label ->
                add(label)
                when (index) {
                    trueIndex -> add(createOpaqueGoto(classNode, method, jump.label, random, config, builderDummyTypes, builderOwnerProvider, returnType))
                    falseIndex -> add(JumpInsnNode(Opcodes.GOTO, continueLabel))
                    else -> {
                        if (config.trappedSwitchCase && config.junkCode && random.nextInt(100) < config.trapChance) {
                            add(createDeadJunkBranch(method, returnType, random, config))
                        }
                        add(JumpInsnNode(Opcodes.GOTO, continueLabel))
                    }
                }
            }
            add(defaultLabel)
            if (config.trappedSwitchCase && config.junkCode && random.nextInt(100) < config.trapChance) {
                add(createDeadJunkBranch(method, returnType, random, config))
            }
            add(JumpInsnNode(Opcodes.GOTO, continueLabel))
            add(continueLabel)
        }
    }

    private fun createCompareLookupBridge(
        classNode: ClassNode,
        method: MethodNode,
        jump: JumpInsnNode,
        random: UniformRandomProvider,
        config: Config,
        builderDummyTypes: List<String>,
        builderOwnerProvider: (UniformRandomProvider) -> ClassNode,
        returnType: Type
    ): InsnList {
        val continueLabel = LabelNode()
        val trueLabel = LabelNode()
        val dispatchLabel = LabelNode()
        val defaultLabel = LabelNode()
        val trueDispatch = LabelNode()
        val falseDispatch = LabelNode()
        val trueKey = rangedInt(random, 256, 0x4000)
        val falseKey = rangedInt(random, 256, 0x4000).let { if (it == trueKey) it xor 0x55 else it }
        return InsnList().apply {
            add(JumpInsnNode(jump.opcode, trueLabel))
            add(buildArithmeticInt(classNode, method, falseKey, random, config, builderDummyTypes, builderOwnerProvider))
            add(JumpInsnNode(Opcodes.GOTO, dispatchLabel))
            add(trueLabel)
            add(buildArithmeticInt(classNode, method, trueKey, random, config, builderDummyTypes, builderOwnerProvider))
            add(dispatchLabel)
            add(LookupSwitchInsnNode(defaultLabel, intArrayOf(falseKey, trueKey), arrayOf(falseDispatch, trueDispatch)))
            add(falseDispatch)
            add(JumpInsnNode(Opcodes.GOTO, continueLabel))
            add(trueDispatch)
            add(createOpaqueGoto(classNode, method, jump.label, random, config, builderDummyTypes, builderOwnerProvider, returnType))
            add(defaultLabel)
            if (config.trappedSwitchCase && config.junkCode && random.nextInt(100) < config.trapChance) {
                add(createDeadJunkBranch(method, returnType, random, config))
            }
            add(JumpInsnNode(Opcodes.GOTO, continueLabel))
            add(continueLabel)
        }
    }

    private fun createMutatedJump(jump: JumpInsnNode, random: UniformRandomProvider): InsnList {
        val trueLabel = LabelNode()
        val dispatchLabel = LabelNode()
        val falseLabel = LabelNode()
        val defaultLabel = LabelNode()
        val key = rangedInt(random, 16, 0x4000)
        return InsnList().apply {
            add(JumpInsnNode(jump.opcode, trueLabel))
            add(LdcInsnNode(key))
            add(JumpInsnNode(Opcodes.GOTO, dispatchLabel))
            add(trueLabel)
            add(LdcInsnNode(key xor 1))
            add(dispatchLabel)
            add(LdcInsnNode(key))
            add(InsnNode(Opcodes.IXOR))
            add(TableSwitchInsnNode(0, 1, defaultLabel, falseLabel, jump.label))
            add(defaultLabel)
            add(JumpInsnNode(Opcodes.GOTO, falseLabel))
            add(falseLabel)
        }
    }

    private fun createOpaqueGoto(
        classNode: ClassNode,
        method: MethodNode,
        target: LabelNode,
        random: UniformRandomProvider,
        config: Config,
        builderDummyTypes: List<String>,
        builderOwnerProvider: (UniformRandomProvider) -> ClassNode,
        returnType: Type
    ): InsnList {
        val action = OpaqueAction.entries[random.nextInt(OpaqueAction.entries.size)]
        val mode = OpaqueCompare.entries[random.nextInt(OpaqueCompare.entries.size)]
        val left = rangedInt(random, 16, Int.MAX_VALUE / 4)
        val right = rangedInt(random, 16, Int.MAX_VALUE / 4)
        val computed = action.apply(left, right)
        val compareTarget = when (mode) {
            OpaqueCompare.EQ -> computed
            OpaqueCompare.NE -> computed xor 1
            OpaqueCompare.LT -> computed + rangedInt(random, 1, 1 shl 10)
            OpaqueCompare.GE -> computed - rangedInt(random, 0, 1 shl 10)
            OpaqueCompare.GT -> computed - rangedInt(random, 1, 1 shl 10)
            OpaqueCompare.LE -> computed + rangedInt(random, 0, 1 shl 10)
        }
        val continueLabel = LabelNode()
        return InsnList().apply {
            add(buildArithmeticInt(classNode, method, left, random, config, builderDummyTypes, builderOwnerProvider))
            add(buildArithmeticInt(classNode, method, right, random, config, builderDummyTypes, builderOwnerProvider))
            add(InsnNode(action.opcode))
            add(buildArithmeticInt(classNode, method, compareTarget, random, config, builderDummyTypes, builderOwnerProvider))
            add(JumpInsnNode(mode.opcode, target))
            if (config.junkCode) {
                add(createDeadJunkBranch(method, returnType, random, config))
            }
            add(JumpInsnNode(Opcodes.GOTO, continueLabel))
            add(continueLabel)
            add(JumpInsnNode(Opcodes.GOTO, target))
        }
    }

    private fun createSwitchGoto(
        classNode: ClassNode,
        method: MethodNode,
        target: LabelNode,
        random: UniformRandomProvider,
        config: Config,
        builderDummyTypes: List<String>,
        builderOwnerProvider: (UniformRandomProvider) -> ClassNode,
        returnType: Type
    ): InsnList {
        val caseCount = rangedInt(random, 2, config.maxSwitchCase.coerceAtLeast(2) + 1)
        val labels = Array(caseCount) { LabelNode() }
        val defaultLabel = LabelNode()
        val startCase = rangedInt(random, 0, 1000)
        val trueIndex = random.nextInt(caseCount)
        val key = rangedInt(random, 16, 0x4000)
        return InsnList().apply {
            add(buildArithmeticInt(classNode, method, (startCase + trueIndex) xor key, random, config, builderDummyTypes, builderOwnerProvider))
            add(buildArithmeticInt(classNode, method, key, random, config, builderDummyTypes, builderOwnerProvider))
            add(InsnNode(Opcodes.IXOR))
            add(TableSwitchInsnNode(startCase, startCase + caseCount - 1, defaultLabel, *labels))
            labels.forEachIndexed { index, label ->
                add(label)
                if (index == trueIndex) {
                    add(JumpInsnNode(Opcodes.GOTO, target))
                } else {
                    if (config.trappedSwitchCase && config.junkCode && random.nextInt(100) < config.trapChance) {
                        add(createDeadJunkBranch(method, returnType, random, config))
                    }
                    add(JumpInsnNode(Opcodes.GOTO, defaultLabel))
                }
            }
            add(defaultLabel)
            if (config.trappedSwitchCase && config.junkCode && random.nextInt(100) < config.trapChance) {
                add(createDeadJunkBranch(method, returnType, random, config))
            }
            add(JumpInsnNode(Opcodes.GOTO, target))
        }
    }

    private fun amplifyRate(rate: Int, intensity: Int): Int {
        return (rate.coerceIn(0, 100) * (0.6 + intensity * 0.4)).toInt().coerceIn(0, 100)
    }

    private fun buildArithmeticInt(
        classNode: ClassNode,
        method: MethodNode,
        value: Int,
        random: UniformRandomProvider,
        config: Config,
        builderDummyTypes: List<String>,
        builderOwnerProvider: (UniformRandomProvider) -> ClassNode
    ): InsnList {
        if (!config.arithmeticExprBuilder) {
            return buildPlainInt(classNode, method, value, config)
        }
        return buildBuilderCall(classNode, value, random, config, builderDummyTypes, builderOwnerProvider(random))
    }

    private fun buildPlainInt(classNode: ClassNode, method: MethodNode, value: Int, config: Config): InsnList {
        if (!config.useLocalVar) {
            return InsnList().apply {
                add(LdcInsnNode(value))
            }
        }
        val slot = method.maxLocals++
        return InsnList().apply {
            add(LdcInsnNode(value))
            add(VarInsnNode(Opcodes.ISTORE, slot))
            add(VarInsnNode(Opcodes.ILOAD, slot))
        }
    }

    private fun buildBuilderCall(
        classNode: ClassNode,
        value: Int,
        random: UniformRandomProvider,
        config: Config,
        builderDummyTypes: List<String>,
        builderOwner: ClassNode
    ): InsnList {
        val builderName = "cf_builder_${random.getRandomString(10)}"
        val dummyDescriptors = if (config.junkBuilderParameter) {
            listOf(
                builderDummyDescriptor(builderDummyTypes, random),
                builderDummyDescriptor(builderDummyTypes, random)
            )
        } else {
            emptyList()
        }
        val desc = if (dummyDescriptors.isEmpty()) "()I" else "(${dummyDescriptors.joinToString("")})I"
        val methodNode = MethodNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            builderName,
            desc,
            null,
            null
        ).apply {
            val body = buildArithmeticIntRecursive(value, random, config.builderIntensity.coerceIn(1, 3))
            instructions.add(body)
            if (config.useLocalVar) {
                val slot = dummyDescriptors.size
                instructions.add(VarInsnNode(Opcodes.ISTORE, slot))
                instructions.add(VarInsnNode(Opcodes.ILOAD, slot))
                maxLocals = slot + 1
            } else {
                maxLocals = dummyDescriptors.size
            }
            instructions.add(InsnNode(Opcodes.IRETURN))
            maxStack = 6
        }.appendAnnotation(GENERATED_METHOD)
        if (config.builderNativeAnnotation) {
            NativeCandidate.registerGeneratedMethod(methodNode)
        }
        builderOwner.methods.add(methodNode)
        return InsnList().apply {
            repeat(dummyDescriptors.size) {
                add(InsnNode(Opcodes.ACONST_NULL))
            }
            add(MethodInsnNode(Opcodes.INVOKESTATIC, builderOwner.name, builderName, desc, false))
        }
    }

    private fun builderDummyDescriptor(pool: List<String>, random: UniformRandomProvider): String {
        if (pool.isEmpty()) return "Ljava/lang/Object;"
        return "L${pool[random.nextInt(pool.size)]};"
    }

    private fun buildArithmeticIntRecursive(value: Int, random: UniformRandomProvider, depth: Int): InsnList {
        if (depth <= 1) {
            return buildArithmeticIntPrimitive(value, random)
        }
        return when (random.nextInt(3)) {
            0 -> {
                val key = random.nextInt()
                InsnList().apply {
                    add(buildArithmeticIntRecursive(value xor key, random, depth - 1))
                    add(buildArithmeticIntRecursive(key, random, depth - 1))
                    add(InsnNode(Opcodes.IXOR))
                }
            }

            1 -> {
                val key = rangedInt(random, 1, 1 shl 16)
                InsnList().apply {
                    add(buildArithmeticIntRecursive(value + key, random, depth - 1))
                    add(buildArithmeticIntRecursive(key, random, depth - 1))
                    add(InsnNode(Opcodes.ISUB))
                }
            }

            else -> {
                val key = rangedInt(random, 1, 1 shl 16)
                InsnList().apply {
                    add(buildArithmeticIntRecursive(value - key, random, depth - 1))
                    add(buildArithmeticIntRecursive(key, random, depth - 1))
                    add(InsnNode(Opcodes.IADD))
                }
            }
        }
    }

    private fun buildArithmeticIntPrimitive(value: Int, random: UniformRandomProvider): InsnList {
        return when (random.nextInt(4)) {
            0 -> {
                val key = random.nextInt()
                InsnList().apply {
                    add(LdcInsnNode(value xor key))
                    add(LdcInsnNode(key))
                    add(InsnNode(Opcodes.IXOR))
                }
            }

            1 -> {
                val key = rangedInt(random, 1, 1 shl 16)
                InsnList().apply {
                    add(LdcInsnNode(value + key))
                    add(LdcInsnNode(key))
                    add(InsnNode(Opcodes.ISUB))
                }
            }

            2 -> {
                val key = rangedInt(random, 1, 1 shl 16)
                InsnList().apply {
                    add(LdcInsnNode(value - key))
                    add(LdcInsnNode(key))
                    add(InsnNode(Opcodes.IADD))
                }
            }

            else -> {
                val mask = rangedInt(random, 1, 1 shl 16)
                InsnList().apply {
                    add(LdcInsnNode(value or mask))
                    add(LdcInsnNode(value and mask.inv()))
                    add(InsnNode(Opcodes.IAND))
                }
            }
        }
    }

    private fun createDeadJunkBranch(method: MethodNode, returnType: Type, random: UniformRandomProvider, config: Config): InsnList {
        val block = InsnList()
        val upper = config.maxJunkCode.coerceAtLeast(0)
        val junkCount = if (upper == 0) 0 else rangedInt(random, 1, upper + 1)
        repeat(junkCount) {
            when (random.nextInt(6)) {
                0 -> {
                    block.add(
                        if (config.arithmeticExprBuilder) {
                            buildArithmeticIntRecursive(random.nextInt(), random, config.builderIntensity.coerceIn(1, 3))
                        } else {
                            buildArithmeticIntPrimitive(random.nextInt(), random)
                        }
                    )
                    block.add(InsnNode(Opcodes.POP))
                }

                1 -> {
                    block.add(MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "java/lang/System",
                        "nanoTime",
                        "()J",
                        false
                    ))
                    block.add(InsnNode(Opcodes.POP2))
                }

                2 -> {
                    block.add(LdcInsnNode(random.nextFloat()))
                    block.add(InsnNode(Opcodes.POP))
                }

                3 -> {
                    block.add(LdcInsnNode(random.nextDouble()))
                    block.add(InsnNode(Opcodes.POP2))
                }

                4 -> {
                    block.add(LdcInsnNode(getNoiseString(random)))
                    block.add(InsnNode(Opcodes.POP))
                }

                else -> {
                    if (!appendJunkMethodCall(block, random)) {
                        block.add(LdcInsnNode(getNoiseString(random)))
                        block.add(InsnNode(Opcodes.POP))
                    }
                }
            }
        }
        appendDeadBranchTerminal(block, method, returnType, random)
        return block
    }

    private fun appendDeadBranchTerminal(block: InsnList, method: MethodNode, returnType: Type, random: UniformRandomProvider) {
        if (method.isInitializer) {
            block.add(InsnNode(Opcodes.ACONST_NULL))
            block.add(InsnNode(Opcodes.ATHROW))
            return
        }
        when (returnType.sort) {
            Type.VOID -> {
                appendOptionalJunkReturnValue(block, Type.VOID_TYPE, random)
                block.add(InsnNode(Opcodes.RETURN))
            }
            Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> {
                if (!appendOptionalJunkReturnValue(block, returnType, random)) {
                    block.add(pushRandomValue(returnType, random))
                }
                block.add(InsnNode(Opcodes.IRETURN))
            }
            Type.FLOAT -> {
                if (!appendOptionalJunkReturnValue(block, returnType, random)) {
                    block.add(pushRandomValue(returnType, random))
                }
                block.add(InsnNode(Opcodes.FRETURN))
            }
            Type.LONG -> {
                if (!appendOptionalJunkReturnValue(block, returnType, random)) {
                    block.add(pushRandomValue(returnType, random))
                }
                block.add(InsnNode(Opcodes.LRETURN))
            }
            Type.DOUBLE -> {
                if (!appendOptionalJunkReturnValue(block, returnType, random)) {
                    block.add(pushRandomValue(returnType, random))
                }
                block.add(InsnNode(Opcodes.DRETURN))
            }
            Type.OBJECT, Type.ARRAY -> {
                appendOptionalJunkReturnValue(block, Type.VOID_TYPE, random)
                block.add(InsnNode(Opcodes.ACONST_NULL))
                block.add(InsnNode(Opcodes.ARETURN))
            }
            else -> {
                block.add(InsnNode(Opcodes.ACONST_NULL))
                block.add(InsnNode(Opcodes.ATHROW))
            }
        }
    }

    private fun appendOptionalJunkReturnValue(block: InsnList, returnType: Type, random: UniformRandomProvider): Boolean {
        if (!this::methodExPredicate.isInitialized) return false
        if (junkCallPool.isEmpty()) return false
        val target = junkCallPool
            .filter { it.returnType.sort == returnType.sort }
            .let { if (it.isEmpty()) emptyList() else it }
            .let { if (it.isEmpty()) null else it[random.nextInt(it.size)] }
            ?: return false
        target.argumentTypes.forEach { type ->
            block.add(pushRandomValue(type, random))
        }
        block.add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            target.owner,
            target.name,
            target.desc,
            false
        ))
        return true
    }

    private fun appendJunkMethodCall(block: InsnList, random: UniformRandomProvider): Boolean {
        if (!this::methodExPredicate.isInitialized) return false
        if (junkCallPool.isEmpty()) return false
        val target = junkCallPool[random.nextInt(junkCallPool.size)]
        target.argumentTypes.forEach { type ->
            block.add(pushRandomValue(type, random))
        }
        block.add(MethodInsnNode(
            Opcodes.INVOKESTATIC,
            target.owner,
            target.name,
            target.desc,
            false
        ))
        when (target.returnType.sort) {
            Type.VOID -> {}
            Type.LONG, Type.DOUBLE -> block.add(InsnNode(Opcodes.POP2))
            else -> block.add(InsnNode(Opcodes.POP))
        }
        return true
    }

    private fun pushRandomValue(type: Type, random: UniformRandomProvider): AbstractInsnNode {
        return when (type.sort) {
            Type.BOOLEAN -> LdcInsnNode(random.nextInt(2))
            Type.BYTE -> LdcInsnNode(random.nextInt(256) - 128)
            Type.CHAR -> LdcInsnNode(rangedInt(random, 32, 127))
            Type.SHORT -> LdcInsnNode(random.nextInt(Short.MAX_VALUE.toInt() * 2 + 1) - Short.MAX_VALUE.toInt() - 1)
            Type.INT -> LdcInsnNode(random.nextInt())
            Type.FLOAT -> LdcInsnNode(random.nextFloat())
            Type.LONG -> LdcInsnNode(random.nextLong())
            Type.DOUBLE -> LdcInsnNode(random.nextDouble())
            else -> LdcInsnNode(0)
        }
    }

    private fun isJunkFriendlyType(type: Type): Boolean {
        return when (type.sort) {
            Type.BOOLEAN,
            Type.BYTE,
            Type.CHAR,
            Type.SHORT,
            Type.INT,
            Type.FLOAT,
            Type.LONG,
            Type.DOUBLE -> true
            else -> false
        }
    }

    private fun getNoiseString(random: UniformRandomProvider): String {
        val length = rangedInt(random, 4, 12)
        val chars = CharArray(length) {
            ('a'.code + random.nextInt(26)).toChar()
        }
        return String(chars)
    }

    private fun rangedInt(random: UniformRandomProvider, start: Int, end: Int): Int {
        require(start < end) { "start must < end" }
        return start + random.nextInt(end - start)
    }

    private fun isConditionalJump(opcode: Int): Boolean {
        return opcode in Opcodes.IFEQ..Opcodes.IF_ACMPNE || opcode == Opcodes.IFNULL || opcode == Opcodes.IFNONNULL
    }

    private fun reversedIfOpcode(opcode: Int): Int? {
        return when (opcode) {
            Opcodes.IFEQ -> Opcodes.IFNE
            Opcodes.IFNE -> Opcodes.IFEQ
            Opcodes.IFLT -> Opcodes.IFGE
            Opcodes.IFGE -> Opcodes.IFLT
            Opcodes.IFGT -> Opcodes.IFLE
            Opcodes.IFLE -> Opcodes.IFGT
            Opcodes.IF_ICMPEQ -> Opcodes.IF_ICMPNE
            Opcodes.IF_ICMPNE -> Opcodes.IF_ICMPEQ
            Opcodes.IF_ICMPLT -> Opcodes.IF_ICMPGE
            Opcodes.IF_ICMPGE -> Opcodes.IF_ICMPLT
            Opcodes.IF_ICMPGT -> Opcodes.IF_ICMPLE
            Opcodes.IF_ICMPLE -> Opcodes.IF_ICMPGT
            Opcodes.IF_ACMPEQ -> Opcodes.IF_ACMPNE
            Opcodes.IF_ACMPNE -> Opcodes.IF_ACMPEQ
            Opcodes.IFNULL -> Opcodes.IFNONNULL
            Opcodes.IFNONNULL -> Opcodes.IFNULL
            else -> null
        }
    }

    private fun isIfCompareJump(opcode: Int): Boolean {
        return opcode in Opcodes.IF_ICMPEQ..Opcodes.IF_ICMPLE
    }

    private fun usesObjectOrNullCompare(opcode: Int): Boolean {
        return opcode == Opcodes.IFNULL
                || opcode == Opcodes.IFNONNULL
                || opcode == Opcodes.IF_ACMPEQ
                || opcode == Opcodes.IF_ACMPNE
    }

    private data class JunkCallMethod(
        val owner: String,
        val name: String,
        val desc: String,
        val argumentTypes: Array<Type>,
        val returnType: Type
    )

    private enum class OpaqueAction(val opcode: Int, val apply: (Int, Int) -> Int) {
        AND(Opcodes.IAND, Int::and),
        OR(Opcodes.IOR, Int::or),
        XOR(Opcodes.IXOR, Int::xor)
    }

    private enum class OpaqueCompare(val opcode: Int) {
        EQ(Opcodes.IF_ICMPEQ),
        NE(Opcodes.IF_ICMPNE),
        LT(Opcodes.IF_ICMPLT),
        GE(Opcodes.IF_ICMPGE),
        GT(Opcodes.IF_ICMPGT),
        LE(Opcodes.IF_ICMPLE)
    }
}
