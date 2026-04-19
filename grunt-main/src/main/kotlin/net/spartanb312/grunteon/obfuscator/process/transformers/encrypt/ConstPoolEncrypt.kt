package net.spartanb312.grunteon.obfuscator.process.transformers.encrypt

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import kotlinx.serialization.Serializable
import net.spartanb312.genesis.kotlin.clinit
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.field
import net.spartanb312.genesis.kotlin.instructions
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number.NumberBasicEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.string.StringArrayedEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.NativeCandidate
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.ReflectionSupport
import net.spartanb312.grunteon.obfuscator.util.*
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.appendAnnotation
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isNative
import net.spartanb312.grunteon.obfuscator.util.numerical.asInt
import net.spartanb312.grunteon.obfuscator.util.numerical.asLong
import org.apache.commons.rng.UniformRandomProvider
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*

class ConstPoolEncrypt : Transformer<ConstPoolEncrypt.Config>(
    name = enText("process.encrypt.const_pool_encrypt", "ConstPollEncrypt"),
    category = Category.Encryption,
    description = enText(
        "process.encrypt.const_pool_encrypt.desc",
        "Move constants into generated companion pools"
    )
) {
    @Serializable
    data class Config(
        @SettingDesc(enText = "Specify class include/exclude rules")
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc(enText = "Encrypt integer constants")
        val integer: Boolean = true,
        @SettingDesc(enText = "Encrypt long constants")
        val long: Boolean = true,
        @SettingDesc(enText = "Encrypt float constants")
        val float: Boolean = true,
        @SettingDesc(enText = "Encrypt double constants")
        val double: Boolean = true,
        @SettingDesc(enText = "Encrypt string constants")
        val string: Boolean = true,
        @SettingDesc(enText = "Use heavier initializer encryption")
        val heavyEncrypt: Boolean = false,
        @SettingDesc(enText = "Keep generated pool class excluded from later scrambling")
        val dontScramble: Boolean = true,
        @SettingDesc(enText = "Mark decrypt initializer for future native workflow")
        val nativeAnnotation: Boolean = false,
        @SettingDesc(enText = "Specify class exclusions.")
        val exclusion: List<String> = listOf(
            "net/dummy/**",
            "net/dummy/Class"
        )
    ) : TransformerConfig

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        val counter = reducibleScopeValue { MergeableCounter() }
        val generatedPools = reducibleScopeValue {
            MergeableObjectList(ObjectArrayList<GeneratedPool>())
        }
        parForEachClassesFiltered(config.classFilter.buildFilterStrategy()) { classNode ->
            val random = Xoshiro256PPRandom(getSeed(classNode.name, "ConstPoolEncrypt"))
            val poolBuilder = PoolBuilder(classNode, config, random)
            val replaced = poolBuilder.process()
            if (replaced > 0) {
                counter.local.add(replaced)
                generatedPools.local.add(poolBuilder.build())
            }
        }
        seq {
            generatedPools.global.forEach {
                instance.workRes.addGeneratedClass(it.classNode)
            }
        }
        post {
            Logger.info(" - ConstPollEncrypt:")
            Logger.info("    Encrypted ${counter.global.get()} constants in ${generatedPools.global.size} pools")
        }
    }

    private data class RefKey(val desc: String, val value: Any)

    private sealed class ConstRef<T : Any>(
        val desc: String,
        val value: T,
        val fieldNode: FieldNode
    ) {
        class IntRef(value: Int, fieldNode: FieldNode) : ConstRef<Int>("I", value, fieldNode)
        class LongRef(value: Long, fieldNode: FieldNode) : ConstRef<Long>("J", value, fieldNode)
        class FloatRef(value: Float, fieldNode: FieldNode) : ConstRef<Float>("F", value, fieldNode)
        class DoubleRef(value: Double, fieldNode: FieldNode) : ConstRef<Double>("D", value, fieldNode)
        class StringRef(value: String, fieldNode: FieldNode) : ConstRef<String>("Ljava/lang/String;", value, fieldNode)
    }

    private data class GeneratedPool(val classNode: ClassNode)

    private inner class PoolBuilder(
        private val ownerClass: ClassNode,
        private val config: Config,
        private val random: UniformRandomProvider
    ) {
        private val refs = linkedMapOf<RefKey, ConstRef<*>>()
        private val poolClass = ClassNode().apply {
            version = ownerClass.version
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER
            name = "${ownerClass.name}\$ConstantPool"
            superName = "java/lang/Object"
            interfaces = mutableListOf()
            appendAnnotation(GENERATED_CLASS)
        }

        fun process(): Int {
            var replaced = 0
            ownerClass.methods.asSequence()
                .filter { !it.isAbstract && !it.isNative }
                .forEach { method ->
                    for (instruction in method.instructions.toArray()) {
                        when (instruction) {
                            is InsnNode -> {
                                when (instruction.opcode) {
                                    in Opcodes.ICONST_M1..Opcodes.ICONST_5 -> if (config.integer) {
                                        val value = instruction.opcode - Opcodes.ICONST_0
                                        replace(method, instruction, getOrCreateIntRef(value))
                                        replaced++
                                    }

                                    Opcodes.LCONST_0, Opcodes.LCONST_1 -> if (config.long) {
                                        val value = (instruction.opcode - Opcodes.LCONST_0).toLong()
                                        replace(method, instruction, getOrCreateLongRef(value))
                                        replaced++
                                    }

                                    Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2 -> if (config.float) {
                                        val value = when (instruction.opcode) {
                                            Opcodes.FCONST_0 -> 0f
                                            Opcodes.FCONST_1 -> 1f
                                            else -> 2f
                                        }
                                        replace(method, instruction, getOrCreateFloatRef(value))
                                        replaced++
                                    }

                                    Opcodes.DCONST_0, Opcodes.DCONST_1 -> if (config.double) {
                                        val value = if (instruction.opcode == Opcodes.DCONST_0) 0.0 else 1.0
                                        replace(method, instruction, getOrCreateDoubleRef(value))
                                        replaced++
                                    }
                                }
                            }

                            is IntInsnNode -> {
                                if (config.integer && instruction.opcode != Opcodes.NEWARRAY) {
                                    replace(method, instruction, getOrCreateIntRef(instruction.operand))
                                    replaced++
                                }
                            }

                            is LdcInsnNode -> {
                                when (val cst = instruction.cst) {
                                    is Int -> if (config.integer) {
                                        replace(method, instruction, getOrCreateIntRef(cst))
                                        replaced++
                                    }

                                    is Long -> if (config.long) {
                                        replace(method, instruction, getOrCreateLongRef(cst))
                                        replaced++
                                    }

                                    is Float -> if (config.float) {
                                        replace(method, instruction, getOrCreateFloatRef(cst))
                                        replaced++
                                    }

                                    is Double -> if (config.double) {
                                        replace(method, instruction, getOrCreateDoubleRef(cst))
                                        replaced++
                                    }

                                    is String -> if (config.string) {
                                        if (!ReflectionSupport.isClassStringExcluded(cst)) {
                                            replace(method, instruction, getOrCreateStringRef(cst))
                                            replaced++
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            return replaced
        }

        fun build(): GeneratedPool {
            poolClass.methods.add(createConstructor())
            val clinit = clinit {
                INSTRUCTIONS {
                    refs.values.forEach { ref ->
                        when (ref) {
                            is ConstRef.IntRef -> {
                                +encryptInt(ref.value)
                                PUTSTATIC(poolClass.name, ref.fieldNode.name, ref.fieldNode.desc)
                            }

                            is ConstRef.LongRef -> {
                                +encryptLong(ref.value)
                                PUTSTATIC(poolClass.name, ref.fieldNode.name, ref.fieldNode.desc)
                            }

                            is ConstRef.FloatRef -> {
                                +encryptFloat(ref.value)
                                PUTSTATIC(poolClass.name, ref.fieldNode.name, ref.fieldNode.desc)
                            }

                            is ConstRef.DoubleRef -> {
                                +encryptDouble(ref.value)
                                PUTSTATIC(poolClass.name, ref.fieldNode.name, ref.fieldNode.desc)
                            }

                            is ConstRef.StringRef -> {
                                val key = random.nextInt()
                                val seed = random.nextLong()
                                val classKey = ownerClass.name.hashCode()
                                val encrypted = StringArrayedEncrypt().encrypt(ref.value.toCharArray(), seed, key, classKey)
                                LDC(encrypted)
                                INVOKEVIRTUAL("java/lang/String", "toCharArray", "()[C")
                                LONG(seed)
                                INT(key)
                                INVOKESTATIC(poolClass.name, decryptMethodName, "([CJI)Ljava/lang/String;")
                                PUTSTATIC(poolClass.name, ref.fieldNode.name, ref.fieldNode.desc)
                            }
                        }
                    }
                    RETURN
                }
            }.appendAnnotation(GENERATED_METHOD)
            if (refs.values.any { it is ConstRef.StringRef }) {
                createDecryptMethod().also {
                    if (config.nativeAnnotation) NativeCandidate.registerGeneratedMethod(it)
                    poolClass.methods.add(it)
                }
            }
            poolClass.methods.add(clinit)
            return GeneratedPool(poolClass)
        }

        private val decryptMethodName = "const_pool_decrypt_${random.getRandomString(10)}"

        private fun replace(method: MethodNode, instruction: AbstractInsnNode, ref: ConstRef<*>) {
            method.instructions.insertBefore(
                instruction,
                FieldInsnNode(Opcodes.GETSTATIC, poolClass.name, ref.fieldNode.name, ref.fieldNode.desc)
            )
            method.instructions.remove(instruction)
        }

        private fun getOrCreateIntRef(value: Int): ConstRef.IntRef {
            val key = RefKey("I", value)
            val existing = refs[key]
            if (existing is ConstRef.IntRef) return existing
            return ConstRef.IntRef(value, createField("I")).also {
                refs[key] = it
                poolClass.fields.add(it.fieldNode)
            }
        }

        private fun getOrCreateLongRef(value: Long): ConstRef.LongRef {
            val key = RefKey("J", value)
            val existing = refs[key]
            if (existing is ConstRef.LongRef) return existing
            return ConstRef.LongRef(value, createField("J")).also {
                refs[key] = it
                poolClass.fields.add(it.fieldNode)
            }
        }

        private fun getOrCreateFloatRef(value: Float): ConstRef.FloatRef {
            val key = RefKey("F", value)
            val existing = refs[key]
            if (existing is ConstRef.FloatRef) return existing
            return ConstRef.FloatRef(value, createField("F")).also {
                refs[key] = it
                poolClass.fields.add(it.fieldNode)
            }
        }

        private fun getOrCreateDoubleRef(value: Double): ConstRef.DoubleRef {
            val key = RefKey("D", value)
            val existing = refs[key]
            if (existing is ConstRef.DoubleRef) return existing
            return ConstRef.DoubleRef(value, createField("D")).also {
                refs[key] = it
                poolClass.fields.add(it.fieldNode)
            }
        }

        private fun getOrCreateStringRef(value: String): ConstRef.StringRef {
            val key = RefKey("Ljava/lang/String;", value)
            val existing = refs[key]
            if (existing is ConstRef.StringRef) return existing
            return ConstRef.StringRef(value, createField("Ljava/lang/String;")).also {
                refs[key] = it
                poolClass.fields.add(it.fieldNode)
            }
        }

        private fun createField(desc: String): FieldNode {
            val fieldName = "const_${random.getRandomString(15)}"
            return field(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                fieldName,
                desc,
                null,
                null
            ).appendAnnotation(GENERATED_FIELD)
        }

        private fun createConstructor(): MethodNode = method(
            Opcodes.ACC_PRIVATE,
            "<init>",
            "()V"
        ) {
            INSTRUCTIONS {
                ALOAD(0)
                INVOKESPECIAL("java/lang/Object", "<init>", "()V")
                RETURN
            }
            MAXS(1, 1)
        }.appendAnnotation(GENERATED_METHOD)

        private fun encryptInt(value: Int): InsnList {
            val encryptor = NumberBasicEncrypt()
            if (config.heavyEncrypt) {
                val noise1 = random.nextInt(1, 0x400)
                val noise2 = random.nextInt(1, 0x400)
                return with(random) {
                    instructions {
                        +encryptor.run { encrypt(value) }
                        INT(noise1)
                        IXOR
                        INT(noise2)
                        IXOR
                        INT(noise1 xor noise2)
                        IXOR
                    }
                }
            }
            return with(random) {
                encryptor.run { encrypt(value) }
            }
        }

        private fun encryptLong(value: Long): InsnList {
            val encryptor = NumberBasicEncrypt()
            if (config.heavyEncrypt) {
                val noise = random.nextLong()
                return with(random) {
                    instructions {
                        +encryptor.run { encrypt(value xor noise) }
                        LDC(noise)
                        LXOR
                    }
                }
            }
            return with(random) {
                encryptor.run { encrypt(value) }
            }
        }

        private fun encryptFloat(value: Float): InsnList = instructions {
            +encryptInt(value.asInt())
            INVOKESTATIC("java/lang/Float", "intBitsToFloat", "(I)F")
        }

        private fun encryptDouble(value: Double): InsnList = instructions {
            +encryptLong(value.asLong())
            INVOKESTATIC("java/lang/Double", "longBitsToDouble", "(J)D")
        }

        private fun createDecryptMethod(): MethodNode = method(
            Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
            decryptMethodName,
            "([CJI)Ljava/lang/String;"
        ) {
            INSTRUCTIONS {
                LABEL(L["L0"])
                INT(ownerClass.name.hashCode())
                ILOAD(3)
                IXOR
                ISTORE(4)
                LABEL(L["L1"])
                ICONST_0
                ISTORE(5)
                LABEL(L["L2"])
                FRAME(Opcodes.F_APPEND, 2, arrayOf(Opcodes.INTEGER, Opcodes.INTEGER), 0, null)
                ILOAD(5)
                ALOAD(0)
                ARRAYLENGTH
                IF_ICMPGE(L["L3"])
                LABEL(L["L4"])
                ILOAD(4)
                LLOAD(1)
                L2I
                IXOR
                ILOAD(5)
                ICONST_M1
                IXOR
                IXOR
                ISTORE(4)
                LABEL(L["L5"])
                ILOAD(4)
                ILOAD(3)
                ILOAD(5)
                ALOAD(0)
                ARRAYLENGTH
                IMUL
                ISUB
                IXOR
                ISTORE(4)
                LABEL(L["L6"])
                ILOAD(4)
                INEG
                ILOAD(3)
                IMUL
                ILOAD(5)
                IOR
                ISTORE(4)
                LABEL(L["L7"])
                ALOAD(0)
                ILOAD(5)
                ALOAD(0)
                ILOAD(5)
                CALOAD
                ILOAD(4)
                IXOR
                I2C
                CASTORE
                LABEL(L["L8"])
                ILOAD(5)
                SIPUSH(255)
                IAND
                ISTORE(6)
                LABEL(L["L9"])
                ILOAD(3)
                ILOAD(6)
                ISHL
                ILOAD(3)
                ILOAD(6)
                INEG
                IUSHR
                IOR
                ISTORE(3)
                LABEL(L["L10"])
                LLOAD(1)
                ILOAD(6)
                I2L
                LXOR
                LSTORE(1)
                LABEL(L["L11"])
                IINC(5, 1)
                GOTO(L["L2"])
                LABEL(L["L3"])
                FRAME(Opcodes.F_CHOP, 1, null, 0, null)
                NEW("java/lang/String")
                DUP
                ALOAD(0)
                INVOKESPECIAL("java/lang/String", "<init>", "([C)V")
                ARETURN
            }
            MAXS(4, 7)
        }.appendAnnotation(GENERATED_METHOD)
    }
}
