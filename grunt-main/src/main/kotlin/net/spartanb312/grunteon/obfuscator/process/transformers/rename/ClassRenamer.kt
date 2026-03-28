package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import com.google.gson.JsonObject
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number.NumberBasicEncrypt
import net.spartanb312.grunteon.obfuscator.util.Counter
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.extensions.isExcluded
import net.spartanb312.grunteon.obfuscator.util.extensions.isMainMethod
import net.spartanb312.grunteon.obfuscator.util.filters.buildClassNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAllBy
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.ClassNode

class ClassRenamer : Transformer<ClassRenamer.Config>(
    name = enText("process.rename.class_renamer", "ClassRenamer"),
    category = Category.Renaming,
    parallel = false
) {

    override val defConfig: TransformerConfig get() = Config()
    override val confType: Class<Config> get() = Config::class.java

    init {
        after(NumberBasicEncrypt::class.java, "Renamer should run after encryptor")
    }

    class Config : TransformerConfig() {
        val dictionary by setting(
            name = enText("process.rename.class_renamer.dictionary", "Dictionary"),
            value = NameGenerator.DictionaryType.Alphabet,
            desc = enText("process.rename.class_renamer.dictionary.desc", "Dictionary for renamer")
        )
        val parent by setting(
            name = enText("process.rename.class_renamer.package", "Package"),
            value = "net/spartanb312/obf/",
            desc = enText("process.rename.class_renamer.package.desc", "Parent package for target name")
        )
        val prefix by setting(
            name = enText("process.rename.class_renamer.prefix", "Prefix"),
            value = "",
            desc = enText("process.rename.class_renamer.prefix.desc", "Prefix for target name")
        )
        val reversed by setting(
            name = enText("process.rename.class_renamer.reversed", "Reversed name"),
            value = false,
            desc = enText("process.rename.class_renamer.reversed.desc", "Append special char to reverse name")
        )
        val shuffled by setting(
            name = enText("process.rename.class_renamer.shuffled", "Shuffled name"),
            value = false,
            desc = enText("process.rename.class_renamer.shuffled.desc", "Shuffled mappings for classes"),
        )
        val corruptedName by setting(
            name = enText("process.rename.class_renamer.corrupted", "Corrupted name"),
            value = false,
            desc = enText("process.rename.class_renamer.corrupted.desc", "Corrupted name for class in zip"),
        )
        val corruptedExclusion by setting(
            name = enText("process.rename.class_renamer.corrupted_exclusion", "Corrupted exclusion"),
            value = listOf(),
            desc = enText(
                "process.rename.class_renamer.corrupted_exclusion.desc",
                "Class exclusion for corrupted name"
            ),
        )

        val corruptExPredicate = buildClassNamePredicates(corruptedExclusion)

        fun malNamePrefix(name: String): String = if (corruptedName) {
            if (corruptExPredicate.matchedAnyBy(name)) {
                Logger.info("    MalName excluded for $name")
                ""
            } else "\u0000"
        } else ""

        val reversePrefix get() = if (reversed) "\u202E" else ""
    }

    private val counter = Counter()

    private class MappingApplier {
        val classMappings = Object2ObjectOpenHashMap<String, String>()
        val revMappings = Object2ObjectOpenHashMap<String, String>()
        val mappingObjects = Object2ObjectOpenHashMap<String, JsonObject>()

        context(instance: Grunteon)
        fun applyRemap(type: String, mappings: Map<String, String>, remapClassNames: Boolean = false) {
            if (instance.configGroup.dumpMappings) {
                val obj = JsonObject()
                mappings.forEach { (prev, new) ->
                    obj.addProperty(prev, new)
                    classMappings[prev] = new
                    revMappings[new] = prev
                }
                mappingObjects[type] = JsonObject().apply { add(type, obj) }
            }
            val remapper = SimpleRemapper(Opcodes.ASM9, mappings)
            instance.workRes.inputClassMap.toList().forEach { (name, node) ->
                val copy = ClassNode()
                val adapter = ClassRemapper(copy, remapper)
                node.accept(adapter)
                instance.workRes.inputClassMap[name] = copy
            }
            if (remapClassNames) {
                instance.workRes.inputClassMap.toList().forEach { (name, node) ->
                    mappings[name]?.let { newName ->
                        instance.workRes.inputClassMap.remove(name)
                        instance.workRes.inputClassMap[newName] = node
                    }
                }
            }
        }
    }

    context(instance: Grunteon)
    override fun transform(config: Config) {
        Logger.info(" - ClassRenamer: Renaming classes...")
        Logger.info("    Generating mappings for classes...")
        buildFilterPredicate(config)
        val dictionary = NameGenerator.getDictionary(config.dictionary)
        val nameGenerator = NameGenerator(dictionary)
        val mappings = mutableMapOf<String, String>()
        val classes =
            if (config.shuffled) instance.workRes.inputClassCollection.shuffled() else instance.workRes.inputClassCollection
        classes.asSequence()
            .filter { clazz ->
                val include = includePredicate.matchedAllBy(clazz.name)
                val exclude = excludePredicate.matchedAnyBy(clazz.name)
                val hardExclude = clazz.isExcluded
                include && !exclude && !hardExclude
            }.forEach { clazz ->
                if (clazz.methods.any { it.isMainMethod }) return@forEach
                if (clazz.name == "net/spartanb312/everett/launch/Entry") return@forEach
                mappings[clazz.name] =
                    config.parent + config.malNamePrefix(clazz.name) + config.reversePrefix + config.prefix + nameGenerator.nextName()
                counter.add()
            }
        Logger.info("    Applying mappings for classes...")
        MappingApplier().applyRemap("classes", mappings, true)
        Logger.info("    Renamed ${counter.get()} classes")
    }

    context(instance: Grunteon)
    override fun PipelineBuilder.buildStageImpl(config: Config) {
        barrier()
        seq {
            val instance = contextOf<Grunteon>()
            Logger.info(" - ClassRenamer: Renaming classes...")
            Logger.info("    Generating mappings for classes...")
            buildFilterPredicate(config)
            val dictionary = NameGenerator.getDictionary(config.dictionary)
            val nameGenerator = NameGenerator(dictionary)
            val classes =
                if (config.shuffled) instance.workRes.inputClassCollection.shuffled() else instance.workRes.inputClassCollection
            classes.asSequence()
                .filter { clazz ->
                    val include = includePredicate.matchedAllBy(clazz.name)
                    val exclude = excludePredicate.matchedAnyBy(clazz.name)
                    val hardExclude = clazz.isExcluded
                    include && !exclude && !hardExclude
                }.forEach { clazz ->
                    if (clazz.methods.any { it.isMainMethod }) return@forEach
                    if (clazz.name == "net/spartanb312/everett/launch/Entry") return@forEach
                    instance.mappingManager.addMapping(
                        MappingManager.MappingType.Classes,
                        clazz.name,
                        config.parent + config.malNamePrefix(clazz.name) + config.reversePrefix + config.prefix + nameGenerator.nextName()
                    )
                    counter.add()
                }
            Logger.info("    Applying mappings for classes...")
        }
        instance.mappingManager.applyRemap(MappingManager.MappingType.Classes)
        post {
            Logger.info("    Renamed ${counter.get()} classes")
        }
    }
}