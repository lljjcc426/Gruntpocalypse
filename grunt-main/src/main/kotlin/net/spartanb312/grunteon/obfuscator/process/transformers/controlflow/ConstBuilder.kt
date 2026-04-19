package net.spartanb312.grunteon.obfuscator.process.transformers.controlflow

import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.GENERATED_FIELD
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
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import net.spartanb312.grunteon.obfuscator.util.getRandomString
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

class ConstBuilder : Transformer<ConstBuilder.Config>(
    name = enText("process.controlflow.const_builder", "ConstBuilder"),
    category = Category.Controlflow,
    description = enText(
        "process.controlflow.const_builder.desc",
        "Replace numeric constants with switch-based builders"
    )
) {
    @Serializable
    data class Config(
        @SettingDesc(enText = "Specify class include/exclude rules")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc(enText = "Enable number switch builder")
        val numberSwitchBuilder: Boolean = true,
        @SettingDesc(enText = "Split long constants into two int builders")
        val splitLong: Boolean = true,
        @SettingDesc(enText = "Use heavier constant encryption")
        val heavyEncrypt: Boolean = false,
        @SettingDesc(enText = "Skip later controlflow processing for generated builders")
        val skipControlFlow: Boolean = true,
        @SettingDesc(enText = "Constant replacement rate")
        @IntRangeVal(min = 0, max = 100)
        val replacePercentage: Int = 10,
        @SettingDesc(enText = "Maximum switch cases")
        @IntRangeVal(min = 1, max = 10)
        val maxCases: Int = 5,
        @SettingDesc(enText = "Specify method exclusions.")
        val exclusion: List<String> = listOf(
            "net/dummy/**",
            "net/dummy/Class",
            "net/dummy/Class.method",
            "net/dummy/Class.method()V"
        )
    ) : TransformerConfig

    private lateinit var methodExPredicate: NamePredicates

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            methodExPredicate = buildMethodNamePredicates(config.exclusion)
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        if (!config.numberSwitchBuilder) return
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            val counter = counter.local
            classNode.methods.asSequence()
                .filter { !it.isAbstract && !it.isNative }
                .forEach { method ->
                    if (methodExPredicate.matchedAnyBy(methodFullDesc(classNode, method))) return@forEach
                    counter.add(with(instance) { transformMethod(classNode, method, config) })
                }
        }
        post {
            Logger.info(" - ConstBuilder:")
            Logger.info("    Generated ${counter.global.get()} number builders")
        }
    }

    context(instance: Grunteon)
    private fun transformMethod(classNode: ClassNode, method: MethodNode, config: Config): Int {
        val random = Xoshiro256PPRandom(getSeed(classNode.name, method.name, method.desc, "ConstBuilder"))
        var replaced = 0
        for (instruction in method.instructions.toArray()) {
            if (random.nextInt(100) >= config.replacePercentage) continue
            when (instruction) {
                is InsnNode -> {
                    if (instruction.opcode in Opcodes.ICONST_M1..Opcodes.ICONST_5) {
                        val value = instruction.opcode - Opcodes.ICONST_0
                        method.instructions.insertBefore(instruction, createIntBuilder(classNode, method, value, config, random))
                        method.instructions.remove(instruction)
                        replaced++
                    }
                }

                is IntInsnNode -> {
                    if (instruction.opcode != Opcodes.NEWARRAY) {
                        method.instructions.insertBefore(instruction, createIntBuilder(classNode, method, instruction.operand, config, random))
                        method.instructions.remove(instruction)
                        replaced++
                    }
                }

                is LdcInsnNode -> {
                    when (val cst = instruction.cst) {
                        is Int -> {
                            method.instructions.insertBefore(instruction, createIntBuilder(classNode, method, cst, config, random))
                            method.instructions.remove(instruction)
                            replaced++
                        }

                        is Long -> {
                            if (config.splitLong) {
                                method.instructions.insertBefore(instruction, createLongBuilder(classNode, method, cst, config, random))
                                method.instructions.remove(instruction)
                                replaced++
                            }
                        }
                    }
                }
            }
        }
        return replaced
    }

    private fun createLongBuilder(
        classNode: ClassNode,
        method: MethodNode,
        value: Long,
        config: Config,
        random: UniformRandomProvider
    ): InsnList {
        val high = (value ushr 32).toInt()
        val low = value.toInt()
        return InsnList().apply {
            add(createIntBuilder(classNode, method, high, config, random))
            add(InsnNode(Opcodes.I2L))
            add(LdcInsnNode(32))
            add(InsnNode(Opcodes.LSHL))
            add(createIntBuilder(classNode, method, low, config, random))
            add(InsnNode(Opcodes.I2L))
            add(LdcInsnNode(0xFFFFFFFFL))
            add(InsnNode(Opcodes.LAND))
            add(InsnNode(Opcodes.LOR))
        }
    }

    private fun createIntBuilder(
        classNode: ClassNode,
        method: MethodNode,
        value: Int,
        config: Config,
        random: UniformRandomProvider
    ): InsnList {
        val exitLabel = LabelNode()
        val caseCount = random.nextInt(config.maxCases.coerceAtLeast(1)) + 1
        val seeds = mutableSetOf<Int>()
        while (seeds.size < caseCount) {
            seeds += random.nextInt()
        }
        val keys = seeds.toMutableList().sorted().toMutableList()
        val selectedKey = keys[random.nextInt(keys.size)]
        val caseLabels = keys.map { LabelNode() }
        val defaultIndex = random.nextInt(caseLabels.size)
        val defaultLabel = caseLabels[defaultIndex]
        val nonDefaultKeys = keys.toMutableList().also { it.removeAt(defaultIndex) }
        val nonDefaultLabels = caseLabels.toMutableList().also { it.removeAt(defaultIndex) }
        return InsnList().apply {
            add(encryptInt(classNode, selectedKey, config.heavyEncrypt, random))
            add(LookupSwitchInsnNode(defaultLabel, nonDefaultKeys.toIntArray(), nonDefaultLabels.toTypedArray()))
            keys.forEachIndexed { index, key ->
                add(caseLabels[index])
                val branchValue = if (key == selectedKey) value else fakeValue(value, random)
                add(encryptInt(classNode, branchValue, config.heavyEncrypt, random))
                add(JumpInsnNode(Opcodes.GOTO, exitLabel))
            }
            add(exitLabel)
        }
    }

    private fun encryptInt(
        classNode: ClassNode,
        value: Int,
        heavy: Boolean,
        random: UniformRandomProvider
    ): InsnList {
        val key = random.nextInt(Short.MAX_VALUE.toInt().coerceAtLeast(1))
        return if (!heavy) {
            InsnList().apply {
                add(pushFieldValue(classNode, value xor key, random))
                add(pushFieldValue(classNode, key, random))
                add(InsnNode(Opcodes.IXOR))
            }
        } else {
            val first = random.nextInt(Short.MAX_VALUE.toInt()) + value
            val second = -random.nextInt(Short.MAX_VALUE.toInt()) + value
            InsnList().apply {
                add(pushFieldValue(classNode, first xor value, random))
                add(pushFieldValue(classNode, second xor value + key, random))
                add(InsnNode(Opcodes.IXOR))
                add(pushFieldValue(classNode, first xor value + key, random))
                add(InsnNode(Opcodes.IXOR))
                add(pushFieldValue(classNode, second, random))
                add(InsnNode(Opcodes.IXOR))
            }
        }
    }

    private fun pushFieldValue(classNode: ClassNode, value: Int, random: UniformRandomProvider): InsnList {
        val fieldName = "number_${random.getRandomString(12)}"
        val field = FieldNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
            fieldName,
            "I",
            null,
            value
        ).appendAnnotation(GENERATED_FIELD)
        classNode.fields.add(field)
        return InsnList().apply {
            add(FieldInsnNode(Opcodes.GETSTATIC, classNode.name, fieldName, "I"))
        }
    }

    private fun fakeValue(value: Int, random: UniformRandomProvider): Int {
        return when (random.nextInt(8)) {
            0 -> value - 1
            1 -> value + 1
            2 -> value * 2
            3 -> value / 2
            4 -> 0
            5 -> 1
            6 -> value * 5
            else -> value * 10
        }
    }
}
