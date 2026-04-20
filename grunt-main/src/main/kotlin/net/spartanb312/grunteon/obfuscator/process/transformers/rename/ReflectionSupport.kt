package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.before
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Logger
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.util.concurrent.ConcurrentHashMap

class ReflectionSupport : Transformer<ReflectionSupport.Config>(
    name = enText("process.rename.reflection_support", "ReflectionSupport"),
    category = Category.Renaming,
    description = enText(
        "process.rename.reflection_support.desc",
        "Collect reflection literals and preserve/remap them around renaming"
    )
) {
    @Serializable
    data class Config(
        @SettingDesc(enText = "Print unresolved reflection warnings")
        val printLog: Boolean = true,
        @SettingDesc(enText = "Support reflective class lookups")
        val clazz: Boolean = true,
        @SettingDesc(enText = "Support reflective method lookups")
        val method: Boolean = true,
        @SettingDesc(enText = "Support reflective field lookups")
        val field: Boolean = true
    ) : TransformerConfig

    init {
        before(Category.Encryption, "ReflectionSupport should run before encryption so reflection strings stay readable")
        before(ClassRenamer::class.java, "ReflectionSupport should run before ClassRenamer")
        before(FieldRenamer::class.java, "ReflectionSupport should run before FieldRenamer")
        before(MethodRenamer::class.java, "ReflectionSupport should run before MethodRenamer")
    }

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        seq {
            enabled = true
            printLog = config.printLog
            classBlacklist.clear()
            methodBlacklist.clear()
            fieldBlacklist.clear()
            stringBlacklist.clear()

            var count = 0
            instance.workRes.inputClassCollection.forEach { classNode ->
                classNode.methods.forEach { methodNode ->
                    methodNode.instructions?.forEach { insnNode ->
                        if (insnNode !is MethodInsnNode) return@forEach

                        if (config.method || config.field) {
                            if (insnNode.opcode == Opcodes.INVOKEVIRTUAL && insnNode.owner == "java/lang/Class") {
                                val pre = insnNode.previous
                                val name = insnNode.name
                                if (config.method && (name == "getMethod" || name == "getDeclaredMethod")) {
                                    val methodName = findReflectionStringArgument(insnNode)
                                    if (methodName != null) {
                                        methodBlacklist += methodName
                                        count++
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: $name")
                                    }
                                }
                                if (config.field && (name == "getField" || name == "getDeclaredField")) {
                                    if (pre is LdcInsnNode && pre.cst is String) {
                                        fieldBlacklist += pre.cst as String
                                        count++
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: $name")
                                    }
                                }
                            }
                            if (insnNode.owner == "java/lang/invoke/MethodHandles\$Lookup") {
                                val name = insnNode.name
                                if (config.method && (name == "findVirtual" || name == "findStatic" || name == "findSpecial")) {
                                    val methodName = findReflectionStringArgument(insnNode)
                                    if (methodName != null) {
                                        methodBlacklist += methodName
                                        count++
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve lookup call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: ${insnNode.owner}.$name")
                                    }
                                }
                                if (config.field && (name == "findGetter" || name == "findSetter" || name == "findStaticGetter" || name == "findStaticSetter")) {
                                    val fieldName = findReflectionStringArgument(insnNode)
                                    if (fieldName != null) {
                                        fieldBlacklist += fieldName
                                        count++
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve lookup call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: ${insnNode.owner}.$name")
                                    }
                                }
                            }
                        }

                        if (config.clazz) {
                            val pre = insnNode.previous
                            when {
                                insnNode.owner == "java/lang/Class" && insnNode.name == "forName" -> {
                                    if (pre is LdcInsnNode && pre.cst is String) {
                                        stringBlacklist += pre.cst as String
                                        count++
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: ${insnNode.name}")
                                    }
                                }

                                insnNode.name == "findClass" && insnNode.desc == "(Ljava/lang/String;)Ljava/lang/Class;" -> {
                                    if (pre is LdcInsnNode && pre.cst is String) {
                                        stringBlacklist += pre.cst as String
                                        count++
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: ${insnNode.name}")
                                    }
                                }

                                insnNode.owner == "java/lang/ClassLoader" && insnNode.name == "loadClass" -> {
                                    if (pre is LdcInsnNode && pre.cst is String) {
                                        stringBlacklist += pre.cst as String
                                        count++
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: ${insnNode.owner}.${insnNode.name}")
                                    }
                                }

                                insnNode.owner == "java/lang/ClassLoader" && insnNode.name == "findClass" -> {
                                    if (pre is LdcInsnNode && pre.cst is String) {
                                        stringBlacklist += pre.cst as String
                                        count++
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: ${insnNode.owner}.${insnNode.name}")
                                    }
                                }

                                insnNode.name == "getResource" && insnNode.desc == "(Ljava/lang/String;)Ljava/net/URL;" -> {
                                    if (pre is LdcInsnNode && pre.cst is String) {
                                        stringBlacklist += pre.cst as String
                                        count++
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: ${insnNode.name}")
                                    }
                                }

                                insnNode.name == "getResourceAsStream" && insnNode.desc == "(Ljava/lang/String;)Ljava/io/InputStream;" -> {
                                    if (pre is LdcInsnNode && pre.cst is String) {
                                        stringBlacklist += pre.cst as String
                                        count++
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: ${insnNode.name}")
                                    }
                                }

                                insnNode.name == "getResources" && insnNode.desc == "(Ljava/lang/String;)Ljava/util/Enumeration;" -> {
                                    if (pre is LdcInsnNode && pre.cst is String) {
                                        stringBlacklist += pre.cst as String
                                        count++
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: ${insnNode.name}")
                                    }
                                }

                                insnNode.owner == "java/lang/ClassLoader" && insnNode.name == "getSystemResource" -> {
                                    if (pre is LdcInsnNode && pre.cst is String) {
                                        stringBlacklist += pre.cst as String
                                        count++
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: ${insnNode.owner}.${insnNode.name}")
                                    }
                                }

                                insnNode.owner == "java/lang/ClassLoader" && insnNode.name == "getSystemResourceAsStream" -> {
                                    if (pre is LdcInsnNode && pre.cst is String) {
                                        stringBlacklist += pre.cst as String
                                        count++
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: ${insnNode.owner}.${insnNode.name}")
                                    }
                                }

                                insnNode.owner == "java/lang/ClassLoader" && insnNode.name == "getSystemResources" -> {
                                    if (pre is LdcInsnNode && pre.cst is String) {
                                        stringBlacklist += pre.cst as String
                                        count++
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: ${insnNode.owner}.${insnNode.name}")
                                    }
                                }

                                insnNode.owner == "java/lang/invoke/MethodType" &&
                                        insnNode.name == "fromMethodDescriptorString" &&
                                        insnNode.desc == "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;" -> {
                                    val descriptorLiteral = findMethodDescriptorStringArgument(insnNode)
                                    if (descriptorLiteral != null) {
                                        stringBlacklist += descriptorLiteral
                                        count++
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: ${insnNode.owner}.${insnNode.name}")
                                    }
                                }

                                insnNode.owner == "org/objectweb/asm/Type" &&
                                        insnNode.desc == "(Ljava/lang/String;)Lorg/objectweb/asm/Type;" &&
                                        (insnNode.name == "getType" || insnNode.name == "getMethodType" || insnNode.name == "getObjectType") -> {
                                    if (pre is LdcInsnNode && pre.cst is String) {
                                        stringBlacklist += pre.cst as String
                                        count++
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: ${insnNode.owner}.${insnNode.name}")
                                    }
                                }

                                insnNode.owner == "java/lang/constant/ClassDesc" &&
                                        insnNode.name == "ofDescriptor" &&
                                        insnNode.desc == "(Ljava/lang/String;)Ljava/lang/constant/ClassDesc;" -> {
                                    if (pre is LdcInsnNode && pre.cst is String) {
                                        stringBlacklist += pre.cst as String
                                        count++
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: ${insnNode.owner}.${insnNode.name}")
                                    }
                                }

                                insnNode.owner == "java/lang/constant/MethodTypeDesc" &&
                                        insnNode.name == "ofDescriptor" &&
                                        insnNode.desc == "(Ljava/lang/String;)Ljava/lang/constant/MethodTypeDesc;" -> {
                                    if (pre is LdcInsnNode && pre.cst is String) {
                                        stringBlacklist += pre.cst as String
                                        count++
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: ${insnNode.owner}.${insnNode.name}")
                                    }
                                }

                                insnNode.owner == "org/objectweb/asm/Handle" &&
                                        (insnNode.name == "<init>") &&
                                        (insnNode.desc == "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V"
                                                || insnNode.desc == "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V") -> {
                                    val handleArgs = findAsmHandleArguments(insnNode)
                                    if (handleArgs != null) {
                                        stringBlacklist += handleArgs.owner
                                        stringBlacklist += handleArgs.name
                                        stringBlacklist += handleArgs.desc
                                        count += 3
                                    } else if (config.printLog) {
                                        Logger.warn("Can't solve reflection call in ${classNode.name}.${methodNode.name}${methodNode.desc}. Operation: ${insnNode.owner}.${insnNode.name}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Logger.info(" - ReflectionSupport:")
            Logger.info("    Collected $count reflection hints")
        }
    }

    companion object {
        @Volatile
        var enabled: Boolean = false

        @Volatile
        var printLog: Boolean = true

        val methodBlacklist = ConcurrentHashMap.newKeySet<String>()
        val fieldBlacklist = ConcurrentHashMap.newKeySet<String>()
        val stringBlacklist = ConcurrentHashMap.newKeySet<String>()
        val classBlacklist get() = stringBlacklist

        fun isClassStringExcluded(value: String): Boolean = enabled && stringBlacklist.contains(value)
        fun isMethodNameExcluded(name: String): Boolean = enabled && methodBlacklist.contains(name)
        fun isFieldNameExcluded(name: String): Boolean = enabled && fieldBlacklist.contains(name)

        private fun findReflectionStringArgument(insnNode: MethodInsnNode): String? {
            var cursor = insnNode.previous
            var budget = 12
            while (cursor != null && budget-- > 0) {
                when (cursor) {
                    is LdcInsnNode -> {
                        val cst = cursor.cst
                        if (cst is String) return cst
                    }

                    else -> {
                        if (cursor.opcode == Opcodes.INVOKEVIRTUAL || cursor.opcode == Opcodes.INVOKESTATIC || cursor.opcode == Opcodes.INVOKEINTERFACE || cursor.opcode == Opcodes.INVOKESPECIAL) {
                            return null
                        }
                    }
                }
                cursor = cursor.previous
            }
            return null
        }

        private fun findMethodDescriptorStringArgument(insnNode: MethodInsnNode): String? {
            var cursor = insnNode.previous
            var budget = 16
            while (cursor != null && budget-- > 0) {
                when (cursor) {
                    is LdcInsnNode -> {
                        val cst = cursor.cst
                        if (cst is String) return cst
                    }

                    is MethodInsnNode -> {
                        // fromMethodDescriptorString commonly appears as:
                        // LDC "(...)"; LDC Type; INVOKEVIRTUAL Class.getClassLoader; INVOKESTATIC MethodType.fromMethodDescriptorString
                        if (cursor.owner == "java/lang/Class" &&
                            cursor.name == "getClassLoader" &&
                            cursor.desc == "()Ljava/lang/ClassLoader;"
                        ) {
                            cursor = cursor.previous
                            continue
                        }
                        return null
                    }
                }
                cursor = cursor.previous
            }
            return null
        }

        private fun findAsmHandleArguments(insnNode: MethodInsnNode): AsmHandleArguments? {
            val literals = ArrayList<String>(3)
            var cursor = insnNode.previous
            var budget = 20
            while (cursor != null && budget-- > 0) {
                when (cursor) {
                    is LdcInsnNode -> {
                        val cst = cursor.cst
                        if (cst is String) {
                            literals += cst
                            if (literals.size == 3) {
                                return AsmHandleArguments(
                                    owner = literals[2],
                                    name = literals[1],
                                    desc = literals[0]
                                )
                            }
                        }
                    }

                    is MethodInsnNode -> return null
                }
                cursor = cursor.previous
            }
            return null
        }

        fun findOwnerClassLiteral(insnNode: MethodInsnNode): String? {
            var cursor: AbstractInsnNode? = insnNode.previous
            var budget = 20
            while (cursor != null && budget-- > 0) {
                when (cursor) {
                    is LdcInsnNode -> {
                        val cst = cursor.cst
                        if (cst is Type && cst.sort == Type.OBJECT) {
                            return cst.internalName
                        }
                        if (cst is String && (cst.contains('/') || cst.contains('.'))) {
                            return cst.replace('.', '/')
                        }
                    }
                }
                cursor = cursor.previous
            }
            return null
        }

        private data class AsmHandleArguments(
            val owner: String,
            val name: String,
            val desc: String
        )
    }
}
