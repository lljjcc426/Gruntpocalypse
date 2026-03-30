package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.MethodHierarchy
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.util.IndyChecker
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.collection.forEachFast
import net.spartanb312.grunteon.obfuscator.util.extensions.*
import net.spartanb312.grunteon.obfuscator.util.interfaces.DisplayEnum

/**
 * Renaming methods
 * Last update on 2026/03/30 by FluixCarvin
 */
class MethodRenamer : Transformer<MethodRenamer.Config>(
    name = enText("process.rename.method_renamer", "MethodRenamer"),
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
        val mode by setting(
            name = enText("process.rename.method_renamer.mode", "Mode"),
            value = Mode.Full,
            desc = enText(
                "process.rename.method_renamer.mode.desc",
                "Interface method name overlap will also be obfuscated in full mode"
            )
        )
        val dictionary by setting(
            name = enText("process.rename.method_renamer.dictionary", "Dictionary"),
            value = NameGenerator.DictionaryType.Alphabet,
            desc = enText("process.rename.method_renamer.dictionary.desc", "Dictionary for renamer")
        )
        val enums by setting(
            name = enText("process.rename.method_renamer.enums", "Enums"),
            value = true,
            desc = enText(
                "process.rename.method_renamer.enums.desc",
                "Obfuscate methods in enum classes"
            )
        )
        val interfaces by setting(
            name = enText("process.rename.method_renamer.interfaces", "Interfaces"),
            value = true,
            desc = enText(
                "process.rename.method_renamer.interfaces.desc",
                "Obfuscate methods in interfaces"
            )
        )
        val heavyOverloads by setting(
            name = enText("process.rename.method_renamer.heavy_overloads", "Heavy overloads"),
            value = true,
            desc = enText(
                "process.rename.method_renamer.heavy_overloads.desc",
                "Overload method names as much as possible"
            )
        )
        val aggressiveShadowNames by setting(
            name = enText("process.rename.method_renamer.aggressive_shadow_names", "Aggressive shadow names"),
            value = true,
            desc = enText(
                "process.rename.method_renamer.aggressive_shadow_names.desc",
                "Shadow method names as much as possible"
            )
        )
    }

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        barrier()
        pre {
            Logger.info(" - MethodRenamer[${config.mode.displayName}]: Renaming methods...")
        }
        buildFull(config)
    }

    context(instance: Grunteon, _: PipelineBuilder)
    private fun buildFull(config: Config) {
        val methodHierarchy = globalScopeValue {
            Logger.info("    Building method hierarchies...")
            val classHierarchy = ClassHierarchy.build(
                instance.workRes.inputClassCollection, // Only include input classes
                instance.workRes::getClassNode
            )
            MethodHierarchy.build(classHierarchy)
        }
        val sourceMapping = globalScopeValue { Int2ObjectLinkedOpenHashMap<String>() }
        val sourceAndOverridesMapping = globalScopeValue { Int2ObjectOpenHashMap<String>() }
        seq {
            val methodHierarchy = methodHierarchy.global
            val classHierarchy = methodHierarchy.classHierarchy
            val strategy = buildFilterStrategy(config)
            val nonExcluded = instance.workRes.inputClassCollection
                .filter {
                    strategy.testClass(it)
                            && !it.isAnnotation
                            && (config.enums || !it.isEnum)
                            && (config.interfaces || !it.isInterface)
                }
                .sortedBy { it.name } // TODO: find a better way to keep naming deterministic without sorting
                .toList()

            val nonExcludedNameSet = nonExcluded.mapTo(ObjectOpenHashSet()) { it.name }

            context(classHierarchy, methodHierarchy) {
                Logger.info("    Splitting method groups...")
                val blackList = mutableSetOf<MethodHierarchy.Entry>()
                val relatedGroups = mutableListOf<MutableSet<MethodHierarchy.Entry>>() // as a family
                nonExcluded.forEach { classNode ->
                    val classIndex = classHierarchy.findClass(classNode.name)
                    if (classIndex == -1) throw Exception("你妈${classNode.name}死了，hierarchy里面找不到你妈")
                    val classEntry = ClassHierarchy.Entry(classIndex)
                    if (!classEntry.hasMissingDependency) {
                        val isEnum = classNode.isEnum
                        for (methodIndex in classEntry.methods.array) {
                            val methodEntry = MethodHierarchy.Entry(methodIndex)
                            // Source check
                            if (!methodEntry.isSourceMethod) continue
                            if (blackList.contains(methodEntry)) continue
                            // Info check
                            val methodNode = methodEntry.node
                            if (methodNode.isNative) continue
                            if (methodNode.isInitializer) continue
                            if (methodNode.isMainMethod) continue
                            if (isEnum && methodNode.name == "values") continue
                            // if (methodNode.reflectionExcluded) continue
                            // TODO: method exclusion
                            // val combined = combine(classNode.name, methodNode.name, methodNode.desc)
                            // Bind group
                            val related = methodEntry.connectedComponent
                            if (related.any { !instance.workRes.inputClassMap.containsKey(it.owner.name) }) continue
                            val group = if (related.size > 1) {
                                val relationship = related.array.map { MethodHierarchy.Entry(it) }.toMutableSet()
                                relationship.add(methodEntry) // idk
                                Logger.debug("    Found multi source method group (${relationship.size} classes): ")
                                relationship.forEach {
                                    Logger.trace("     - " + it.full)
                                }
                                relationship
                            } else mutableSetOf(methodEntry)
                            // apply group
                            blackList.addAll(group)
                            relatedGroups.add(group)
                        }
                    }
                }

                Logger.info("    Generating mappings for method groups...")
                val dictionary = NameGenerator.getDictionary(config.dictionary)
                val sourceAndOverridesMapping = sourceAndOverridesMapping.global
                val sourceMapping = sourceMapping.global
                // share a same name in a group
                val nameGenerators = mutableMapOf<ClassHierarchy.Entry, NameGenerator>()
                val existedNameMap = Array(classHierarchy.classCount) { ObjectOpenHashSet<String>() }
                relatedGroups.forEach { group ->
                    val present = group.first()
                    val namePrefix = "" // (if (randomKeywordPrefix) "$nextBadKeyword " else "") + prefix TODO: prefix
                    val suffix = "" // TODO : suffix
                    // Avoid shadow names
                    val dic = nameGenerators.getOrPut(present.owner) {
                        NameGenerator(dictionary)
                    }
                    val checkSet = IntLinkedOpenHashSet()
                    group.forEach { source ->
                        checkSet.add(source.owner.index)
                        // Disable up check for static and private fields TODO: check this
                        if ((!source.node.isStatic && !source.node.isPrivate) || !config.aggressiveShadowNames) {
                            // println("Disable up check for ${source.name}.${source.name}${source.desc}")
                            source.owner.descendants.forEach {
                                checkSet.add(it.index)
                            }
                        }
                    }
                    val checkList = ClassHierarchy.EntryArray(checkSet.toIntArray())
                    checkList.forEach { owner ->
                        if (!nonExcludedNameSet.contains(owner.classNode.name)) {
                            Logger.debug("${owner.classNode.name} is not included in working range. Discarded all group (${group.size} methods)")
                            checkList.forEach {
                                Logger.trace(" - ${it.name}")
                            }
                            return@forEach
                        }
                    }
                    var newName: String
                    loop@ while (true) {
                        newName = namePrefix + dic.nextName(config.heavyOverloads, present.desc) + suffix
                        // Cache once per outer iteration instead of recomputing for every entry in checkList
                        val nameWithDesc = newName + present.desc
                        var keepThisName = true
                        run check@{
                            checkList.forEach { owner ->
                                if (existedNameMap[owner.index].contains(nameWithDesc)) {
                                    keepThisName = false
                                    return@check
                                }
                            }
                        }
                        if (keepThisName) break
                    }
                    val nameWithDesc = newName + present.desc
                    checkList.forEach { owner ->
                        existedNameMap[owner.index].add(nameWithDesc)
                    }
                    // Apply to all affected
                    group.forEach { sourceMethod ->
                        sourceAndOverridesMapping[sourceMethod.index] = newName
                        sourceMapping[sourceMethod.index] = newName
                        // Disable up apply for private and static
                        if (!sourceMethod.node.isPrivate && !sourceMethod.node.isStatic) {
                            sourceMethod.overrideMethods.forEach {
                                sourceAndOverridesMapping[it.index] = newName
                            }
                        }
                    }
                }
            }
        }
        val indyResults = IndyChecker.check(methodHierarchy, sourceAndOverridesMapping)
        seq {
            Logger.info("    Applying mappings for methods...")
            val indyResults = indyResults.global

            // Remap
            Logger.info("    Generated indy mapping for ${indyResults.size} methods")
            indyResults.forEach { implicitInfo ->
                val key = ".${implicitInfo.indyInsnName}${implicitInfo.indyInsnDesc}"
                Logger.trace("     - $key")
                instance.mappingManager.addMapping(
                    MappingManager.MappingType.Methods,
                    key, implicitInfo.newName
                )
            }

            val mh = methodHierarchy.global
            val sourceMapping = sourceMapping.global
            val ch = mh.classHierarchy
            context(mh, ch) {
                var count = sourceMapping.size
                sourceMapping.forEachFast { sourceMethodIdx, newName ->
                    val sourceMethod = MethodHierarchy.Entry(sourceMethodIdx)
                    instance.mappingManager.addMapping(
                        MappingManager.MappingType.Methods,
                        sourceMethod.full, newName
                    )
                    sourceMethod.owner.descendants.forEach {
                        val descendantName = it.name
                        val full = "$descendantName.${sourceMethod.name}${sourceMethod.desc}"
                        instance.mappingManager.addMapping(
                            MappingManager.MappingType.Methods,
                            full, newName
                        )
                        count++
                    }
                }
                Logger.info("    Generated mapping for $count methods")
            }
        }
        instance.mappingManager.applyRemap(MappingManager.MappingType.Methods)
        post {
            Logger.info(" - MethodRenamer:")
            Logger.info("    Renamed ${instance.mappingManager.mappings[MappingManager.MappingType.Methods.ordinal].size} methods")
        }
    }


    enum class Mode(override val displayName: CharSequence) : DisplayEnum {
        Fast("Fast"),
        Full("Full"),
    }

    private fun combine(owner: String, name: String, desc: String) = "$owner.$name$desc"

}