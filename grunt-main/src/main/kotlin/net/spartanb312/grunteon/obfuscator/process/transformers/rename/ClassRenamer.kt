package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.collection.shuffled
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.isMainMethod
import net.spartanb312.grunteon.obfuscator.util.filters.buildClassNamePredicates
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy

class ClassRenamer : Transformer<ClassRenamer.Config>(
    name = enText("process.rename.class_renamer", "ClassRenamer"),
    category = Category.Renaming,
    parallel = false
) {

    override val defConfig: TransformerConfig get() = Config()
    override val confType: Class<Config> get() = Config::class.java

    init {
        after(Category.Encryption, "Renamer should run after encryption category")
        after(Category.Controlflow, "Renamer should run after controlflow category")
        after(Category.AntiDebug, "Renamer should run after anti debug category")
        after(Category.Authentication, "Renamer should run after authentication category")
        after(Category.Exploit, "Renamer should run after exploit category")
        after(Category.Miscellaneous, "Renamer should run after miscellaneous category")
        after(Category.Optimization, "Renamer should run after optimization category")
        after(Category.Redirect, "Renamer should run after redirect category")
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

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        val counter = globalScopeValue { MergeableCounter() }
        seq {
            val instance = contextOf<Grunteon>()
            Logger.info(" - ClassRenamer: Renaming classes...")
            Logger.info("    Generating mappings for classes...")
            val strategy = buildFilterStrategy(config)
            val dictionary = NameGenerator.getDictionary(config.dictionary)
            val nameGenerator = NameGenerator(dictionary)
            val randomGen = Xoshiro256PPRandom(getSeed("Global"))
            val classes = if (config.shuffled) instance.workRes.inputClassCollection.shuffled(randomGen)
            else instance.workRes.inputClassCollection
            classes.asSequence()
                .filter { strategy.testClass(it) }
                .forEach { clazz ->
                    if (clazz.methods.any { it.isMainMethod }) return@forEach
                    if (clazz.name == "net/spartanb312/everett/launch/Entry") return@forEach
                    instance.mappingManager.addMapping(
                        MappingManager.MappingType.Classes,
                        clazz.name,
                        config.parent + config.malNamePrefix(clazz.name) + config.reversePrefix + config.prefix + nameGenerator.nextName()
                    )
                    counter.global.add()
                }
            Logger.info("    Applying mappings for classes...")
        }
        instance.mappingManager.applyRemap(MappingManager.MappingType.Classes)
        post {
            Logger.info(" - ClassRenamer:")
            Logger.info("    Renamed ${counter.global.get()} classes")
        }
    }
}