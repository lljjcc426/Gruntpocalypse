package net.spartanb312.grunteon.obfuscator.process.transformers.minecraft

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
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
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.MappingSource
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.extensions.isMixinClass
import net.spartanb312.grunteon.obfuscator.util.filters.buildClassNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import java.nio.charset.StandardCharsets

class MixinClassRenamer : Transformer<MixinClassRenamer.Config>(
    name = enText("process.minecraft.mixin_class_renamer", "MixinClassRename"),
    category = net.spartanb312.grunteon.obfuscator.process.Category.Renaming,
    description = enText(
        "process.minecraft.mixin_class_renamer.desc",
        "Rename mixin classes and remap mixin metadata files"
    )
), MappingSource {

    init {
        after(MixinFieldRenamer::class.java, "MixinClassRename should run after MixinFieldRename")
        after(ClassRenamer::class.java, "MixinClassRename should run after ClassRenamer")
    }

    @Serializable
    data class Config(
        @SettingDesc(enText = "Dictionary for mixin class renamer")
        val dictionary: NameGenerator.DictionaryType = NameGenerator.DictionaryType.Alphabet,
        @SettingDesc(enText = "Target package for mixin classes")
        val targetMixinPackage: String = "net/spartanb312/obf/mixins/",
        @SettingDesc(enText = "Mixin configuration file name")
        val mixinFile: String = "mixins.example.json",
        @SettingDesc(enText = "Mixin refmap file name")
        val refmapFile: String = "mixins.example.refmap.json",
        @SettingDesc(enText = "Mixin class exclusion rules")
        val exclusion: List<String> = listOf(
            "net/spartanb312/Example",
            "net/spartanb312/component/Component**",
            "net/spartanb312/package/**"
        )
    ) : TransformerConfig

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        barrier()
        pre {
            Logger.info(" > MixinClassRename: Generating mappings...")
        }
        seq {
            val mixinClasses = instance.workRes.inputClassCollection.filter { with(instance) { it.isMixinClass } }
            if (mixinClasses.isEmpty()) {
                Logger.info("    No mixin classes found")
                return@seq
            }

            val targetPackage = config.targetMixinPackage.removeSuffix("/") + "/"
            val exclusionPredicates = buildClassNamePredicates(config.exclusion)
            val nameGenerator = NameGenerator(
                NameGenerator.getDictionary(config.dictionary),
                instance.obfConfig.dictionaryStartIndex
            )
            val generatedMappings = linkedMapOf<String, String>()
            var counter = 0
            mixinClasses.forEach { classNode ->
                if (exclusionPredicates.matchedAnyBy(classNode.name)) return@forEach
                val newName = targetPackage + nameGenerator.nextName()
                instance.nameMapping.putClassMapping(classNode.name, newName)
                generatedMappings[classNode.name] = newName
                counter++
            }

            remapMixinFiles(instance, config, generatedMappings)
            Logger.info("    Generated mapping for $counter mixin classes")
        }
    }

    private fun remapMixinFiles(
        instance: Grunteon,
        config: Config,
        mappings: Map<String, String>
    ) {
        val gson = Gson()
        instance.workRes.getInputResource(config.mixinFile)?.let { resource ->
            val mainObject = JsonObject()
            var packagePrefix = ""
            gson.fromJson(String(resource.content, StandardCharsets.UTF_8), JsonObject::class.java).asMap()
                .forEach { (name, value) ->
                    when (name) {
                        "required", "minVersion", "compatibilityLevel", "refmap" -> mainObject.add(name, value)
                        "package" -> {
                            mainObject.addProperty(
                                "package",
                                config.targetMixinPackage.removeSuffix("/").replace("/", ".")
                            )
                            packagePrefix = value.asString
                        }

                        "mixins", "client", "server" -> {
                            val array = JsonArray()
                            value.asJsonArray.forEach { mixin ->
                                val clazz = packagePrefix + "." + mixin.asString
                                val key = clazz.replace(".", "/")
                                val mapping = mappings[key]
                                array.add(mapping?.substringAfterLast("/") ?: mixin.asString)
                            }
                            mainObject.add(name, array)
                        }

                        else -> mainObject.add(name, value)
                    }
                }
            resource.content = gson.toJson(mainObject).toByteArray(Charsets.UTF_8)
        }

        instance.workRes.getInputResource(config.refmapFile)?.let { resource ->
            val mainObject = JsonObject()
            gson.fromJson(String(resource.content, StandardCharsets.UTF_8), JsonObject::class.java).asMap()
                .forEach { (name, value) ->
                    when (name) {
                        "mappings" -> {
                            val obj = JsonObject()
                            value.asJsonObject.asMap().forEach { (clazz, data) ->
                                obj.add(mappings[clazz] ?: clazz, data)
                            }
                            mainObject.add(name, obj)
                        }

                        "data" -> {
                            val dataObject = JsonObject()
                            value.asJsonObject.asMap().forEach { (type, typeObj) ->
                                val newTypeObj = JsonObject()
                                typeObj.asJsonObject.asMap().forEach { (clazz, data) ->
                                    newTypeObj.add(mappings[clazz] ?: clazz, data)
                                }
                                dataObject.add(type, newTypeObj)
                            }
                            mainObject.add(name, dataObject)
                        }

                        else -> mainObject.add(name, value)
                    }
                }
            resource.content = gson.toJson(mainObject).toByteArray(Charsets.UTF_8)
        }
    }
}
