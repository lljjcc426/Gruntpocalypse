package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.pipeline.after
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.MethodHierarchy
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.util.IndyChecker
import net.spartanb312.grunteon.obfuscator.util.Logger
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
            value = false,
            desc = enText(
                "process.rename.method_renamer.heavy_overloads.desc",
                "Overload method names as much as possible"
            )
        )
    }

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
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
        val infoMappings = globalScopeValue { mutableMapOf<MethodHierarchy.Entry, String>() }
        val mappings = globalScopeValue { mutableMapOf<String, String>() } // full name to new name
        seq {
            val methodHierarchy = methodHierarchy.global
            val classHierarchy = methodHierarchy.classHierarchy
            context(classHierarchy, methodHierarchy) {
                Logger.info("    Splitting method groups...")
                val strategy = buildFilterStrategy(config)
                val blackList = mutableSetOf<MethodHierarchy.Entry>()
                val relatedGroups = mutableListOf<MutableSet<MethodHierarchy.Entry>>() // as a family
                instance.workRes.inputClassCollection.asSequence()
                    .filter {
                        strategy.testClass(it)
                            && !it.isAnnotation
                            && (config.enums || !it.isEnum)
                            && (config.interfaces || !it.isInterface)
                    }.forEach { classNode ->
                        val classIndex = classHierarchy.findClass(classNode.name)
                        if (classIndex == -1) throw Exception("你妈${classNode.name}死了，hierarchy里面找不到你妈")
                        val classEntry = ClassHierarchy.Entry(classIndex)
                        if (!classEntry.hasMissingDependency) {
                            val isEnum = classNode.isEnum
                            for (methodIndex in classEntry.methods.indices) {
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
                                val group = if (related.size > 1) {
                                    val relationship = related.indices.map { MethodHierarchy.Entry(it) }.toMutableSet()
                                    relationship.add(methodEntry) // idk
                                    Logger.info("    Found multi source method group: ")
                                    relationship.forEach {
                                        Logger.info("     - " + it.full)
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
                val infoMappings = infoMappings.global
                // share a same name in a group
                val nameGenerators = mutableMapOf<ClassHierarchy.Entry, NameGenerator>()
                val existedNameMap = mutableMapOf<ClassHierarchy.Entry, MutableSet<String>>() // class, name$desc list
                val mappings = mappings.global
                relatedGroups.forEach { group ->
                    val present = group.first()
                    val namePrefix = "" // (if (randomKeywordPrefix) "$nextBadKeyword " else "") + prefix TODO: prefix
                    val suffix = "" // TODO : suffix
                    // Avoid shadow names
                    val dic = nameGenerators.getOrPut(present.owner) {
                        NameGenerator(dictionary)
                    }
                    val checkList = mutableSetOf<Pair<ClassHierarchy.Entry, String>>()
                    group.forEach { source ->
                        checkList.add(source.owner to source.full)
                        // TODO: check this
                        val descendants = source.owner.descendants.map {
                            val ownerName = classHierarchy.classNames[it]
                            ClassHierarchy.Entry(it) to "${ownerName}.${source.name}${source.desc}"
                        }
                        checkList.addAll(descendants)
                    }
                    var newName: String
                    loop@ while (true) {
                        newName = namePrefix + dic.nextName(config.heavyOverloads, present.desc) + suffix
                        var keepThisName = true
                        check@ for (check in checkList) {
                            val nameSet = existedNameMap.getOrPut(check.first) { mutableSetOf() }
                            if (nameSet.contains(newName + present.desc)) {
                                keepThisName = false
                                break@check
                            }
                        }
                        if (keepThisName) break
                    }
                    checkList.forEach { check ->
                        val nameSet = existedNameMap.getOrPut(check.first) { mutableSetOf() }
                        nameSet.add(newName + present.desc)
                    }
                    // Apply to all affected
                    val affectedSet = mutableSetOf<String>()
                    group.forEach { sourceMethod ->
                        mappings[sourceMethod.full] = newName
                        affectedSet.add(sourceMethod.full)
                        infoMappings[sourceMethod] = newName
                        sourceMethod.owner.descendants.forEach { oi ->
                            val ownerName = classHierarchy.classNames[oi]
                            val full = "${ownerName}.${sourceMethod.name}${sourceMethod.desc}"
                            mappings[full] = newName
                            affectedSet.add(full)
                            val owner = ClassHierarchy.Entry(oi)
                            val methods = owner.methods.indices.map { MethodHierarchy.Entry(it) }
                            val exist = methods.find {
                                it.name == sourceMethod.name && it.desc == sourceMethod.desc
                            }
                            if (exist != null) infoMappings[exist] = newName
                        }
                    }
                }
            }
        }
        val indyResults = IndyChecker.check(methodHierarchy, infoMappings)
        seq {
            Logger.info("    Applying mappings for methods...")
            val mappings = mappings.global
            val indyResults = indyResults.global

            // Remap
            indyResults.forEach { implicitInfo ->
                val key = ".${implicitInfo.indyInsnName}${implicitInfo.indyInsnDesc}"
                Logger.info("    Generated indy mapping for $key")
                mappings[key] = implicitInfo.newName
            }

            mappings.forEach { mapping ->
                instance.mappingManager.addMapping(
                    MappingManager.MappingType.Methods,
                    mapping.key, mapping.value
                )
            }
        }
        instance.mappingManager.applyRemap(MappingManager.MappingType.Methods)
        post {
            Logger.info(" - MethodRenamer:")
            Logger.info("    Renamed ${mappings.global.size} methods")
        }
    }


    enum class Mode(override val displayName: CharSequence) : DisplayEnum {
        Fast("Fast"),
        Full("Full"),
    }

    private fun combine(owner: String, name: String, desc: String) = "$owner.$name$desc"

}