package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.StageBuilder
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number.NumberBasicEncrypt
import net.spartanb312.grunteon.obfuscator.util.Counter
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.extensions.isExcluded
import net.spartanb312.grunteon.obfuscator.util.extensions.isMainMethod
import net.spartanb312.grunteon.obfuscator.util.filters.buildClassNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAllBy
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy

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
        instance.mappingApplier.applyRemap("classes", mappings, true)
        Logger.info("    Renamed ${counter.get()} classes")
    }

    override fun StageBuilder.buildStage(config: Config) {
        seq {
            val instance = contextOf<Grunteon>()
            Logger.info(" - ClassRenamer: Renaming classes...")
            Logger.info("    Generating mappings for classes...")
            buildFilterPredicate(config)
            dictionary = NameGenerator.getDictionary(config.dictionary)
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
                        config.parent + config.malNamePrefix(clazz.name) + config.reversePrefix + config.prefix + dictionary.nextName()
                    counter.add()
                }
            Logger.info("    Applying mappings for classes...")
            instance.mappingApplier.applyRemap("classes", mappings, true)
            Logger.info("    Renamed ${counter.get()} classes")
        }
    }
}