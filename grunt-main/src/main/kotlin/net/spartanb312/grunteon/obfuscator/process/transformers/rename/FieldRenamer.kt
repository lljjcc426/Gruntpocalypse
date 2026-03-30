package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.FieldHierarchy
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.extensions.isPrivate
import net.spartanb312.grunteon.obfuscator.util.extensions.isProtected

class FieldRenamer : Transformer<FieldRenamer.Config>(
    name = enText("process.rename.field_renamer", "FieldRenamer"),
    category = Category.Renaming,
    parallel = false,
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
        val dictionary = NameGenerator.DictionaryType.Alphabet

        //val randomKeywordPrefix = false TODO: impl this
        val prefix = ""
        val reversed = false
        val shuffled = true
        val heavyOverloads = false
        val excludedNames = listOf("INSTANCE", "Companion")

        // getter
        val malPrefix = prefix //(if (randomKeywordPrefix) "$nextBadKeyword " else "") + prefix
        val suffix get() = if (reversed) "\u200E" else ""
    }

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        barrier()
        pre {
            Logger.info(" - FieldRenamer: Renaming fields...")
        }
        val fieldHierarchy = globalScopeValue {
            Logger.info("    Building field hierarchies...")
            val classHierarchy = ClassHierarchy.build(
                instance.workRes.inputClassCollection, // Only include input classes
                instance.workRes::getClassNode
            )
            FieldHierarchy.build(classHierarchy)
        }
        seq {
            val mappings = Object2ObjectOpenHashMap<String, String>()
            val fieldHierarchy = fieldHierarchy.global
            val classHierarchy = fieldHierarchy.classHierarchy
            val strategy = buildFilterStrategy(config)
            val nonExcluded = instance.workRes.inputClassCollection
                .filter {
                    strategy.testClass(it)
                }
                .sortedBy { it.name }
                .toList()

            // TODO: heavy overloads
            // val nonExcludedNameSet = nonExcluded.mapTo(ObjectOpenHashSet()) { it.name }
            val existedNameMap = Int2ObjectOpenHashMap<MutableSet<String>>()
            val nameGenerator = NameGenerator(NameGenerator.getDictionary(config.dictionary))
            context(classHierarchy, fieldHierarchy) {
                Logger.info("    Generating field mappings...")
                nonExcluded.forEach { classNode ->
                    val classIndex = classHierarchy.findClass(classNode.name)
                    if (classIndex == -1) throw Exception("你妈${classNode.name}死了，hierarchy里面找不到你妈")
                    val classEntry = ClassHierarchy.Entry(classIndex)
                    if (!classEntry.hasMissingDependency) {
                        for (fieldIndex in classEntry.fields.array) {
                            val fieldEntry = FieldHierarchy.Entry(fieldIndex)
                            // Source check
                            if (!fieldEntry.isSourceField) continue
                            if (fieldEntry.name in config.excludedNames) continue
                            // Check descendants
                            var checkPass = true
                            descendantsCheck@ for (descendant in classEntry.descendants.array) {
                                if (ClassHierarchy.Entry(descendant).hasMissingDependency) {
                                    checkPass = false
                                    break@descendantsCheck
                                }
                            }
                            if (!checkPass) continue

                            // Avoid shadow names
                            val checkSet = IntLinkedOpenHashSet()
                            checkSet.add(classEntry.index)
                            classEntry.descendants.forEach {
                                checkSet.add(it.index)
                            }
                            val checkList = ClassHierarchy.EntryArray(checkSet.toIntArray())
                            var newName: String
                            loop@ while (true) {
                                newName = config.malPrefix + nameGenerator.nextName() + config.suffix
                                var keepThisName = true
                                check@ for (check in checkList.array) {
                                    val nameSet = existedNameMap.getOrPut(check) { mutableSetOf() }
                                    if (nameSet.contains(newName)) {
                                        keepThisName = false
                                        break@check
                                    }
                                }
                                if (keepThisName) break
                            }
                            checkList.forEach { check ->
                                val nameSet = existedNameMap.getOrPut(check.index) { mutableSetOf() }
                                nameSet.add(newName)
                            }
                            val upApply = !fieldEntry.node.isPrivate || fieldEntry.node.isProtected
                            // Apply to children
                            if (upApply) {
                                val affected = mutableSetOf(classEntry.index)
                                affected.addAll(classEntry.descendants.array.toList()) // fixme: optimize this
                                affected.forEach { apply ->
                                    val key = "${ClassHierarchy.Entry(apply).name}.${fieldEntry.node.name}"
                                    mappings[key] = newName
                                }
                            } else mappings["${fieldEntry.owner.name}.${fieldEntry.name}"] = newName
                        }
                    }
                }
            }

            mappings.forEach { (pre, new) ->
                instance.mappingManager.addMapping(
                    MappingManager.MappingType.Fields,
                    pre, new
                )
            }
        }
        instance.mappingManager.applyRemap(MappingManager.MappingType.Fields)
        post {
            Logger.info(" - FieldRenamer:")
            Logger.info("    Renamed ${instance.mappingManager.mappings[MappingManager.MappingType.Fields.ordinal].size} fields")
        }
    }


}