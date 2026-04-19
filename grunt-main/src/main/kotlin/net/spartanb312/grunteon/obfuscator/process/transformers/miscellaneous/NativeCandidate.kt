package net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous

import com.google.gson.Gson
import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.ConstPoolEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.InvokeDynamic
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.extensions.appendAnnotation
import net.spartanb312.grunteon.obfuscator.util.extensions.isAbstract
import net.spartanb312.grunteon.obfuscator.util.extensions.isAnnotation
import net.spartanb312.grunteon.obfuscator.util.extensions.isEnum
import net.spartanb312.grunteon.obfuscator.util.extensions.isInitializer
import net.spartanb312.grunteon.obfuscator.util.extensions.isInterface
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import java.util.Collections

class NativeCandidate : Transformer<NativeCandidate.Config>(
    name = enText("process.miscellaneous.native_candidate", "NativeCandidate"),
    category = Category.Miscellaneous,
    description = enText(
        "process.miscellaneous.native_candidate.desc",
        "Append native workflow annotations to matching classes and methods"
    )
) {
    @Serializable
    data class Config(
        @SettingDesc(enText = "Annotation descriptor appended to native candidates")
        val nativeAnnotation: String = "Lnet/spartanb312/example/Native;",
        @SettingDesc(enText = "Search for short call-chain method candidates")
        val searchCandidate: Boolean = true,
        @SettingDesc(enText = "Maximum call count allowed for searched methods. 0 means unlimited")
        val upCallLimit: Int = 0,
        @SettingDesc(enText = "Specify class exclusions.")
        val exclusion: List<String> = listOf(
            "net/dummy/**",
            "net/dummy/Class"
        ),
        @SettingDesc(enText = "Annotation group rules in JSON")
        val annotationGroups: List<String> = listOf(
            """{ "annotation": "Lnet/spartanb312/grunt/Native;", "includeRegexes": ["^(?:[^./\\[;]+/)*[^./\\[;]+$"], "excludeRegexes": [] }""",
            """{ "annotation": "Lnet/spartanb312/grunt/VMProtect;", "includeRegexes": ["^(?:[^./\\[;]+\\/)*(?:[^./\\[;])+\\.(?:[^./\\[;()\\/])+(?:\\(((\\[*L[^./\\[;]([^./\\[;]*[^.\\[;][^./\\[;])*;)|(\\[*[ZBCSIJFD]+))*\\))((\\[*L[^./\\[;]([^./\\[;]*[^.\\[;][^./\\[;])*;)|V|(\\[*[ZBCSIJFD]))$"], "excludeRegexes": [] }"""
        )
    ) : TransformerConfig

    init {
        after(ConstPoolEncrypt::class.java, "NativeCandidate should run after ConstPoolEncrypt")
        after(InvokeDynamic::class.java, "NativeCandidate should run after InvokeDynamic")
    }

    private data class AnnotationGroup(
        val annotation: String,
        val includeRegexes: List<String>,
        val excludeRegexes: List<String>
    )

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        var addedCount = 0
        pre {
            activeAnnotation = config.nativeAnnotation

            val searchedMethods = mutableSetOf<MethodNode>()
            if (config.searchCandidate) {
                instance.workRes.inputClassCollection.asSequence()
                    .filter {
                        !it.isInterface && !it.isAnnotation && !it.isEnum && !it.isAbstract &&
                            !config.exclusion.contains(it.name)
                    }
                    .forEach { classNode ->
                        classNode.methods.forEach { methodNode ->
                            if (methodNode.isInitializer) return@forEach
                            if (appendedMethods.contains(methodNode)) return@forEach
                            val invokeCount = countCalls(methodNode)
                            if (config.upCallLimit == 0 || invokeCount <= config.upCallLimit) {
                                searchedMethods += methodNode
                            }
                        }
                    }
            }

            searchedMethods.forEach {
                it.appendAnnotation(config.nativeAnnotation)
                addedCount++
            }
            appendedMethods.toList().forEach {
                it.appendAnnotation(config.nativeAnnotation)
                addedCount++
            }

            val gson = Gson()
            config.annotationGroups.forEach { raw ->
                runCatching { gson.fromJson(raw, AnnotationGroup::class.java) }.getOrNull()?.let { group ->
                    val includeRegexes = group.includeRegexes.map(::Regex)
                    val excludeRegexes = group.excludeRegexes.map(::Regex)
                    instance.workRes.inputClassCollection.forEach { classNode ->
                        if (matchesClass(classNode, includeRegexes, excludeRegexes)) {
                            classNode.appendAnnotation(group.annotation)
                        }
                        classNode.methods.forEach { methodNode ->
                            if (matchesMethod(classNode, methodNode, includeRegexes, excludeRegexes)) {
                                methodNode.appendAnnotation(group.annotation)
                            }
                        }
                    }
                }
            }

            appendedMethods.clear()
        }
        post {
            Logger.info(" - NativeCandidate:")
            Logger.info("    Added $addedCount native candidate annotations")
        }
    }

    private fun countCalls(methodNode: MethodNode): Int {
        var count = 0
        for (insn in methodNode.instructions) {
            when (insn) {
                is FieldInsnNode, is MethodInsnNode, is InvokeDynamicInsnNode -> count++
            }
        }
        return count
    }

    private fun matchesClass(classNode: ClassNode, includeRegexes: List<Regex>, excludeRegexes: List<Regex>): Boolean {
        val name = classNode.name
        return includeRegexes.any { it.matches(name) } && excludeRegexes.none { it.matches(name) }
    }

    private fun matchesMethod(
        classNode: ClassNode,
        methodNode: MethodNode,
        includeRegexes: List<Regex>,
        excludeRegexes: List<Regex>
    ): Boolean {
        val fullName = "${classNode.name}.${methodNode.name}${methodNode.desc}"
        return includeRegexes.any { it.matches(fullName) } && excludeRegexes.none { it.matches(fullName) }
    }

    companion object {
        @Volatile
        var activeAnnotation: String = "Lnet/spartanb312/example/Native;"

        val appendedMethods: MutableSet<MethodNode> = Collections.synchronizedSet(LinkedHashSet())

        fun registerGeneratedMethod(methodNode: MethodNode) {
            appendedMethods += methodNode
        }
    }
}
