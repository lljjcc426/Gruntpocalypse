package net.spartanb312.grunteon.obfuscator.process.transformers.other

import javassist.ClassPool
import javassist.CtNewMethod
import kotlinx.serialization.Serializable
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.*
import net.spartanb312.genesis.kotlin.field
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.extensions.hasAnnotations
import net.spartanb312.grunteon.obfuscator.util.extensions.isInterface
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

class Watermark : Transformer<Watermark.Config>(
    name = enText("process.other.watermark", "Watermark"),
    category = Category.Other,
    description = enText(
        "process.other.watermark.desc",
        "Add watermarks"
    )
) {
    @Serializable
    data class Config(
        val classFilter: ClassFilterConfig = ClassFilterConfig(),
        @SettingDesc(enText = "Watermark member names")
        val names: List<String> = listOf("Grunt", "Gruntpocalypse", "Grunteon"),
        @SettingDesc(enText = "Watermark messages")
        val messages: List<String> = listOf(
            "PROTECTED BY GRUNTEON",
            "PROTECTED BY EVERETT",
            "PROTECTED BY YuShengJun"
        ),
        @SettingDesc(enText = "Add field watermark")
        val fieldMark: Boolean = true,
        @SettingDesc(enText = "Add method watermark")
        val methodMark: Boolean = true,
        @SettingDesc(enText = "Add annotation watermark")
        val annotationMark: Boolean = false,
        @SettingDesc(enText = "Annotation names used by watermark")
        val annotations: List<String> = listOf("ProtectedByGrunt", "JvavMetadata"),
        @SettingDesc(enText = "Annotation version values")
        val versions: List<String> = listOf("114514", "1919810", "69420"),
        @SettingDesc(enText = "Append fake interface to classes")
        val interfaceMark: Boolean = false,
        @SettingDesc(enText = "Interface name used by watermark")
        val fatherOfJava: String = "jvav/lang/YuShengJun",
        @SettingDesc(enText = "Add custom trash method")
        val customTrashMethod: Boolean = false,
        @SettingDesc(enText = "Custom trash method name")
        val customMethodName: String = "protected by YuShengJun",
        @SettingDesc(enText = "Custom trash method code")
        val customMethodCode: String = """
            public static String method() {
                return "Protected by YuShengJun";
            }
        """.trimIndent()
    ) : TransformerConfig

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            //Logger.info(" > Watermark: Adding watermarks...")
        }
        val filter = config.classFilter.buildFilterStrategy()
        val counter = reducibleScopeValue { MergeableCounter() }
        parForEachClassesFiltered(filter) { classNode ->
            if (classNode.isInterface) return@parForEachClassesFiltered
            val counter = counter.local
            if (config.interfaceMark) {
                classNode.interfaces = (classNode.interfaces ?: arrayListOf()).apply {
                    if (!contains(config.fatherOfJava)) add(config.fatherOfJava)
                }
                counter.add()
            }
            if (config.fieldMark) {
                classNode.fields = classNode.fields ?: arrayListOf()
                val marker = config.messages.random()
                when ((0..2).random()) {
                    0 -> classNode.fields.add(
                        field(
                            PRIVATE + STATIC,
                            config.names.random(),
                            "Ljava/lang/String;",
                            null,
                            marker
                        )
                    )

                    1 -> classNode.fields.add(
                        field(
                            PRIVATE + STATIC,
                            "_$marker _",
                            "I",
                            null,
                            listOf(114514, 1919810, 69420, 911, 8964).random()
                        )
                    )

                    2 -> classNode.fields.add(
                        field(
                            PRIVATE + STATIC,
                            config.names.random(),
                            "Ljava/lang/String;",
                            null,
                            marker
                        )
                    )
                }
                counter.add()
            }
            if (config.methodMark) {
                classNode.methods = classNode.methods ?: arrayListOf()
                val marker = config.messages.random()
                when ((0..2).random()) {
                    0 -> classNode.methods.add(
                        method(
                            PRIVATE + STATIC,
                            config.names.random(),
                            "()Ljava/lang/String;"
                        ) {
                            INSTRUCTIONS {
                                LDC(marker)
                                ARETURN
                            }
                        }
                    )

                    1 -> classNode.methods.add(
                        method(
                            PRIVATE + STATIC,
                            config.names.random(),
                            "()Ljava/lang/String;"
                        ) {
                            INSTRUCTIONS {
                                LDC(marker)
                                ARETURN
                            }
                        }
                    )

                    2 -> classNode.methods.add(
                        method(
                            PRIVATE + STATIC,
                            config.names.random(),
                            "()Ljava/lang/String;"
                        ) {
                            INSTRUCTIONS {
                                LDC(marker)
                                ARETURN
                            }
                        }
                    )
                }
                counter.add()
            }
            if (config.annotationMark && !classNode.hasAnnotations) {
                classNode.visibleAnnotations = (classNode.visibleAnnotations ?: arrayListOf()).apply {
                    add(buildWatermarkAnnotation(config))
                }
                counter.add()
            }
            if (config.customTrashMethod) {
                runCatching {
                    buildCustomTrashMethod(classNode.name, config)
                }.onSuccess {
                    classNode.methods.add(it)
                    counter.add()
                }.onFailure {
                    Logger.warn("    Watermark custom method failed for ${classNode.name}: ${it.message}")
                }
            }
        }
        post {
            if (config.interfaceMark && instance.workRes.getClassNode(config.fatherOfJava) == null) {
                instance.workRes.addGeneratedClass(buildWatermarkInterface(config.fatherOfJava))
            }
            Logger.info(" - Watermark:")
            Logger.info("    Added ${counter.global.get()} watermarks")
        }
    }

    private fun buildWatermarkAnnotation(config: Config): AnnotationNode {
        val desc = normalizeAnnotationDesc(config.annotations.randomOrNull() ?: "ProtectedByGrunt")
        return AnnotationNode(desc).apply {
            values = arrayListOf(
                "version", config.versions.randomOrNull().orEmpty(),
                "mapping", "jvav/lang/ZhangHaoYangException",
                "d1", config.messages.random(),
                "d2", config.messages.random()
            )
        }
    }

    private fun normalizeAnnotationDesc(annotation: String): String {
        return if (annotation.startsWith("L") && annotation.endsWith(";")) annotation
        else "Lnet/spartanb312/grunt/$annotation;"
    }

    private fun buildWatermarkInterface(name: String): ClassNode {
        return ClassNode().apply {
            version = Opcodes.V1_8
            access = Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT or Opcodes.ACC_INTERFACE
            this.name = name
            superName = "java/lang/Object"
            interfaces = arrayListOf()
        }
    }

    private fun buildCustomTrashMethod(ownerName: String, config: Config): MethodNode {
        val classPool = ClassPool.getDefault()
        val ctClass = classPool.makeClass(ownerName.replace('/', '.'))
        try {
            val trashMethod = CtNewMethod.make(config.customMethodCode, ctClass)
            ctClass.addMethod(trashMethod)
            val tempClassNode = ClassNode()
            ClassReader(ctClass.toBytecode()).accept(tempClassNode, ClassReader.EXPAND_FRAMES)
            return tempClassNode.methods.first { it.name == trashMethod.name }.apply {
                name = config.customMethodName
            }
        } finally {
            ctClass.detach()
        }
    }

}
