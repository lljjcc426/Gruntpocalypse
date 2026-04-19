package net.spartanb312.grunteon.obfuscator.process.transformers.minecraft

import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.PipelineBuilder
import net.spartanb312.grunteon.obfuscator.process.SettingDesc
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.barrier
import net.spartanb312.grunteon.obfuscator.process.pre
import net.spartanb312.grunteon.obfuscator.process.seq
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.ClassRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.FieldRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.MappingSource
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.extensions.isMixinClass
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode

class MixinFieldRenamer : Transformer<MixinFieldRenamer.Config>(
    name = enText("process.minecraft.mixin_field_renamer", "MixinFieldRename"),
    category = net.spartanb312.grunteon.obfuscator.process.Category.Renaming,
    description = enText(
        "process.minecraft.mixin_field_renamer.desc",
        "Rename fields inside mixin classes and related descendants"
    )
), MappingSource {

    init {
        after(FieldRenamer::class.java, "MixinFieldRename should run after FieldRenamer")
        after(ClassRenamer::class.java, "MixinFieldRename should run after ClassRenamer")
    }

    @Serializable
    data class Config(
        @SettingDesc(enText = "Dictionary for mixin field renamer")
        val dictionary: NameGenerator.DictionaryType = NameGenerator.DictionaryType.Alphabet,
        @SettingDesc(enText = "Prefix for mixin field names")
        val prefix: String = "",
        @SettingDesc(enText = "Field exclusion rules")
        val exclusion: List<String> = listOf(
            "net/spartanb312/Example1",
            "net/spartanb312/Example2.field"
        ),
        @SettingDesc(enText = "Excluded original field names")
        val excludedName: List<String> = listOf("INSTANCE", "Companion")
    ) : TransformerConfig

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        barrier()
        pre {
            Logger.info(" > MixinFieldRename: Generating mappings...")
        }
        seq {
            val mixinClasses = instance.workRes.inputClassCollection.filter { with(instance) { it.isMixinClass } }
            if (mixinClasses.isEmpty()) {
                Logger.info("    No mixin classes found")
                return@seq
            }

            val dictionary = NameGenerator(
                NameGenerator.getDictionary(config.dictionary),
                instance.obfConfig.dictionaryStartIndex
            )
            val workList: List<Pair<FieldNode, ClassNode>> =
                mixinClasses.flatMap { owner -> owner.fields.map { fieldNode -> fieldNode to owner } }
            var counter = 0

            workList.forEach { (fieldNode, owner) ->
                if (fieldNode.name in config.excludedName) return@forEach
                if (hasProtectedMixinAnnotation(fieldNode)) return@forEach
                val newName = config.prefix + dictionary.nextName()
                descendantsOf(owner).forEach { classNode ->
                    val key = classNode.name + "." + fieldNode.name
                    if (key in config.exclusion) return@forEach
                    instance.nameMapping.putFieldMapping(
                        classNode.name,
                        fieldNode.name,
                        fieldNode.desc,
                        newName
                    )
                }
                counter++
            }

            Logger.info("    Generated mapping for $counter mixin fields")
        }
    }

    context(instance: Grunteon)
    private fun descendantsOf(owner: ClassNode): Set<ClassNode> {
        val result = linkedSetOf<ClassNode>()
        val stack = ArrayDeque<ClassNode>()
        stack.add(owner)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (!result.add(current)) continue
            instance.workRes.inputClassCollection.forEach {
                if (it.superName == current.name || it.interfaces.contains(current.name)) {
                    stack.add(it)
                }
            }
        }
        return result
    }

    private fun hasProtectedMixinAnnotation(fieldNode: FieldNode): Boolean {
        return fieldNode.visibleAnnotations?.any { MIXIN_FIELD_ANNOTATIONS.contains(it.desc) } == true ||
            fieldNode.invisibleAnnotations?.any { MIXIN_FIELD_ANNOTATIONS.contains(it.desc) } == true
    }

    companion object {
        private val MIXIN_FIELD_ANNOTATIONS = setOf(
            "Lorg/spongepowered/asm/mixin/gen/Accessor;",
            "Lorg/spongepowered/asm/mixin/gen/Invoker;",
            "Lorg/spongepowered/asm/mixin/Shadow;",
            "Lorg/spongepowered/asm/mixin/Overwrite;"
        )
    }
}
