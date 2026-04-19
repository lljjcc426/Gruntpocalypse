package net.spartanb312.grunteon.obfuscator.web

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.spartanb312.grunteon.obfuscator.ObfConfig
import net.spartanb312.grunteon.obfuscator.process.ClassFilterConfig
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.transformers.antidebug.AntiDebug
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.process.transformers.PostProcess
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.ConstBuilder
import net.spartanb312.grunteon.obfuscator.process.transformers.controlflow.Controlflow
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.ArithmeticSubstitute
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.ConstPoolEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number.NumberBasicEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.string.StringArrayedEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.minecraft.MixinClassRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.minecraft.MixinFieldRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.ClonedClass
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.DeclaredFieldsExtract
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.HWIDAuthentication
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.NativeCandidate
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.ParameterObfuscate
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.TrashClass
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.ClassShrink
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.DeadCodeRemove
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.EnumOptimize
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.KotlinClassShrink
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.SourceDebugInfoHide
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.StringEqualsOptimize
import net.spartanb312.grunteon.obfuscator.process.transformers.other.DecompilerCrasher
import net.spartanb312.grunteon.obfuscator.process.transformers.other.FakeSyntheticBridge
import net.spartanb312.grunteon.obfuscator.process.transformers.other.ShuffleMembers
import net.spartanb312.grunteon.obfuscator.process.transformers.other.Watermark
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.FieldAccessProxy
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.InvokeDynamic
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.InvokeDispatcher
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.InvokeProxy
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.ClassRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.FieldRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.LocalVarRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.MethodRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.ReflectionSupport

object WebConfigSchema {
    fun loadSchemaText(): String {
        return checkNotNull(javaClass.classLoader.getResource("web/schema/config-schema.json")) {
            "Missing web/schema/config-schema.json"
        }.readText()
    }
}

object WebConfigAdapter {
    private val defaults = ObfConfig()

    fun toObfConfig(document: JsonObject): ObfConfig {
        val settings = SectionState(document.obj("Settings"))
        val corruptOutput = settings.bool("CorruptOutput", false)
        val controlflowState = SectionState(document.obj("Controlflow"))

        val transformers = buildList {
            addIfEnabled(document, "SourceDebugRemove") {
                SourceDebugInfoHide.Config(
                    classFilter = classFilter(list("Exclusion")),
                    sourceFiles = when {
                        !bool("SourceDebug", true) -> SourceDebugInfoHide.SourceFileAction.Off
                        bool("RenameSourceDebug", false) -> SourceDebugInfoHide.SourceFileAction.Replace
                        else -> SourceDebugInfoHide.SourceFileAction.Remove
                    },
                    lineNumbers = bool("LineDebug", true),
                    sourceNames = list("SourceNames", listOf(""))
                )
            }
            addIfEnabled(document, "Shrinking") {
                ClassShrink.Config(
                    classFilter = classFilter(list("Exclusion")),
                    innerClasses = bool("RemoveInnerClass", true),
                    unusedLabels = bool("RemoveUnusedLabel", true),
                    nopRemove = bool("RemoveNOP", false),
                    methodSignatures = true,
                    annotationRemovals = list("AnnotationRemovals", ClassShrink.Config().annotationRemovals)
                )
            }
            addIfEnabled(document, "KotlinOptimizer") {
                KotlinClassShrink.Config(
                    classFilter = classFilter(
                        list("IntrinsicsExclusion") + list("MetadataExclusion")
                    ),
                    metaData = bool("Annotations", true),
                    intrinsics = bool("Intrinsics", true),
                    intrinsicsRemoval = list(
                        "IntrinsicsRemoval",
                        KotlinClassShrink.Config().intrinsicsRemoval
                    ),
                    replaceLDC = bool("ReplaceLdc", true)
                )
            }
            addIfEnabled(document, "EnumOptimize") {
                EnumOptimize.Config(classFilter = classFilter(list("Exclusion")))
            }
            addIfEnabled(document, "DeadCodeRemove") {
                DeadCodeRemove.Config(
                    classFilter = classFilter(list("Exclusion")),
                    pop = true,
                    pop2 = true,
                    fallthrough = true
                )
            }
            addIfEnabled(document, "ClonedClass") {
                ClonedClass.Config(
                    count = int("Count", 0),
                    suffix = string("Suffix", "-cloned"),
                    removeAnnotations = bool("RemoveAnnotations", true),
                    exclusion = list("Exclusion")
                )
            }
            addIfEnabled(document, "AntiDebug") {
                AntiDebug.Config(
                    classFilter = classFilter(list("Exclusion")),
                    checkJDWP = bool("CheckJDWP", true),
                    checkXDebug = bool("CheckXDebug", true),
                    checkJavaAgent = bool("CheckJavaAgent", true),
                    customKeywords = list("CustomKeywords"),
                    message = string("Message", "Debugger detected"),
                    exclusion = list("Exclusion")
                )
            }
            addIfEnabled(document, "HideDeclaredFields") {
                DeclaredFieldsExtract.Config(classFilter = classFilter(list("Exclusion")))
            }
            addIfEnabled(document, "ParameterObfuscate") {
                ParameterObfuscate.Config(
                    onlyPrivateMethod = bool("OnlyPrivateMethod", true),
                    classFilter = classFilter(list("Exclusion")),
                    exclusion = list("Exclusion")
                )
            }
            addIfEnabled(document, "TrashClass") {
                TrashClass.Config(
                    packageName = string("Package", "net/spartanb312/obf/"),
                    prefix = string("Prefix", "Trash"),
                    count = int("Count", 0)
                )
            }
            addIfEnabled(document, "HWIDAuthentication") {
                HWIDAuthentication.Config(
                    classFilter = classFilter(list("Exclusion")),
                    onlineMode = bool("OnlineMode", true),
                    offlineHWID = list("OfflineHWID", listOf("Put HWID here (For offline mode only)")),
                    onlineURL = string("OnlineURL", "https://pastebin.com/XXXXX"),
                    encryptKey = string("EncryptKey", "1186118611861186"),
                    cachePools = int("CachePools", 5),
                    showHWIDWhenFailed = bool("ShowHWIDWhenFailed", true),
                    encryptConst = bool("EncryptConst", true),
                    exclusion = list("Exclusion")
                )
            }
            addIfEnabled(document, "ReflectionSupport") {
                ReflectionSupport.Config(
                    printLog = bool("PrintLog", true),
                    clazz = bool("Class", true),
                    method = bool("Method", true),
                    field = bool("Field", true)
                )
            }
            addIfEnabled(document, "StringEncrypt") {
                StringArrayedEncrypt.Config(
                    classFilter = classFilter(list("Exclusion")),
                    carray = bool("Arrayed", false),
                    invokeDynamics = bool("ReplaceInvokeDynamics", true),
                    exclusion = list("Exclusion")
                )
            }
            addControlflowIfNeeded(controlflowState, beforeEncrypt = true)
            addIfEnabled(document, "ConstBuilder") {
                ConstBuilder.Config(
                    classFilter = classFilter(list("Exclusion")),
                    numberSwitchBuilder = bool("NumberSwitchBuilder", true),
                    splitLong = bool("SplitLong", true),
                    heavyEncrypt = bool("HeavyEncrypt", false),
                    skipControlFlow = bool("SkipControlFlow", true),
                    replacePercentage = int("ReplacePercentage", 10),
                    maxCases = int("MaxCases", 5),
                    exclusion = list("Exclusion")
                )
            }
            addIfEnabled(document, "ConstPollEncrypt") {
                ConstPoolEncrypt.Config(
                    classFilter = classFilter(list("Exclusion")),
                    integer = bool("Integer", true),
                    long = bool("Long", true),
                    float = bool("Float", true),
                    double = bool("Double", true),
                    string = bool("String", true),
                    heavyEncrypt = bool("HeavyEncrypt", false),
                    dontScramble = bool("DontScramble", true),
                    nativeAnnotation = bool("NativeAnnotation", false),
                    exclusion = list("Exclusion")
                )
            }
            addIfEnabled(document, "NumberEncrypt") {
                val chance = intensityToChance(int("Intensity", 1))
                NumberBasicEncrypt.Config(
                    classFilter = classFilter(list("Exclusion")),
                    integer = true,
                    integerChance = chance,
                    long = true,
                    longChance = chance,
                    float = bool("FloatingPoint", true),
                    floatChance = chance,
                    double = bool("FloatingPoint", true),
                    doubleChance = chance,
                    arrayed = bool("Arrayed", false),
                    maxInstructions = int("MaxInsnSize", NumberBasicEncrypt.Config().maxInstructions),
                    dynamicStrength = true,
                    exclusion = list("Exclusion")
                )
            }
            addIfEnabled(document, "ArithmeticEncrypt") {
                ArithmeticSubstitute.Config(
                    classFilter = classFilter(list("Exclusion")),
                    chance = intensityToChance(int("Intensity", 1)),
                    maxInstructions = int("MaxInsnSize", ArithmeticSubstitute.Config().maxInstructions),
                    dynamicStrength = true,
                    exclusion = list("Exclusion")
                )
            }
            addControlflowIfNeeded(controlflowState, beforeEncrypt = false)
            addIfEnabled(document, "RedirectStringEquals") {
                StringEqualsOptimize.Config(
                    classFilter = classFilter(list("Exclusion")),
                    ignoreCase = bool("IgnoreCase", true)
                )
            }
            addIfEnabled(document, "FieldScramble") {
                FieldAccessProxy.Config(
                    classFilter = classFilter(list("ExcludedClasses")),
                    chance = percentageToChance(int("ReplacePercentage", 10)),
                    intensity = int("Intensity", 1),
                    getStatic = bool("GetStatic", true),
                    putStatic = bool("SetStatic", true),
                    getField = bool("GetValue", true),
                    putField = bool("SetField", true),
                    randomName = bool("RandomName", false),
                    outer = bool("GenerateOuterClass", false),
                    nativeAnnotation = bool("NativeAnnotation", false),
                    exclusion = list("ExcludedFieldName")
                )
            }
            addIfEnabled(document, "MethodScramble") {
                InvokeProxy.Config(
                    classFilter = classFilter(list("ExcludedClasses")),
                    chance = percentageToChance(int("ReplacePercentage", 10)),
                    outer = bool("GenerateOuterClass", false),
                    randomCall = bool("RandomCall", true),
                    nativeAnnotation = bool("NativeAnnotation", false),
                    exclusion = list("ExcludedMethodName")
                )
            }
            addIfEnabled(document, "InvokeDispatcher") {
                InvokeDispatcher.Config(
                    classFilter = classFilter(list("Exclusion")),
                    chance = percentageToChance(int("ReplacePercentage", 30)),
                    maxParams = int("MaxParams", 10),
                    maxHandles = int("MaxHandles", 10),
                    exclusion = list("Exclusion")
                )
            }
            addIfEnabled(document, "InvokeDynamic") {
                InvokeDynamic.Config(
                    classFilter = classFilter(list("Exclusion")),
                    replacePercentage = int("ReplacePercentage", 10),
                    heavyProtection = bool("HeavyProtection", false),
                    metadataClass = string("MetadataClass", "net/spartanb312/grunt/GruntMetadata"),
                    massiveRandomBlank = bool("MassiveRandomBlank", false),
                    reobfuscate = bool("Reobfuscate", true),
                    enhancedFlowReobf = bool("EnhancedFlowReobf", false),
                    bsmNativeAnnotation = bool("BSMNativeAnnotation", false),
                    exclusion = list("Exclusion")
                )
            }
            addIfEnabled(document, "NativeCandidate") {
                NativeCandidate.Config(
                    nativeAnnotation = string("NativeAnnotation", "Lnet/spartanb312/example/Native;"),
                    searchCandidate = bool("SearchCandidate", true),
                    upCallLimit = int("UpCallLimit", 0),
                    exclusion = list("Exclusion"),
                    annotationGroups = list("AnnotationGroups")
                )
            }
            addIfEnabled(document, "SyntheticBridge") {
                FakeSyntheticBridge.Config(classFilter = classFilter(list("Exclusion")))
            }
            addIfEnabled(document, "LocalVariableRename") {
                LocalVarRenamer.Config(
                    classFilter = classFilter(list("Exclusion")),
                    dictionary = dictionary(string("Dictionary", "Alphabet")),
                    prefix = if (bool("ThisReference", false)) "this_" else LocalVarRenamer.Config().prefix,
                    deleteASMInfo = bool("DeleteLocalVars", false) || bool("DeleteParameters", false),
                    exclusion = list("Exclusion")
                )
            }
            addIfEnabled(document, "ClassRename") {
                ClassRenamer.Config(
                    classFilter = classFilter(list("Exclusion")),
                    dictionary = dictionary(string("Dictionary", "Alphabet")),
                    parent = string("Parent", ClassRenamer.Config().parent),
                    prefix = string("Prefix", ""),
                    reversed = bool("Reversed", false),
                    shuffled = bool("Shuffled", false),
                    corruptedName = bool("CorruptedName", false),
                    corruptedExclusion = list("CorruptedNameExclusion")
                )
            }
            addIfEnabled(document, "FieldRename") {
                FieldRenamer.Config(
                    classFilter = classFilter(list("Exclusion")),
                    dictionary = dictionary(string("Dictionary", "Alphabet")),
                    prefix = string("Prefix", ""),
                    reversed = bool("Reversed", false),
                    shuffled = false,
                    heavyOverloads = false,
                    aggressiveShadowNames = bool("RandomKeywordPrefix", false),
                    excludedNames = list("ExcludedName")
                )
            }
            addIfEnabled(document, "MethodRename") {
                MethodRenamer.Config(
                    classFilter = classFilter(list("Exclusion")),
                    dictionary = dictionary(string("Dictionary", "Alphabet")),
                    prefix = string("Prefix", ""),
                    reversed = bool("Reversed", false),
                    enums = bool("Enums", true),
                    interfaces = bool("Interfaces", false),
                    heavyOverloads = bool("HeavyOverloads", false),
                    aggressiveShadowNames = bool("RandomKeywordPrefix", false),
                    excludedNames = list("ExcludedName"),
                    solveBridge = true
                )
            }
            addIfEnabled(document, "MixinFieldRename") {
                MixinFieldRenamer.Config(
                    dictionary = dictionary(string("Dictionary", "Alphabet")),
                    prefix = string("Prefix", ""),
                    exclusion = list("Exclusion"),
                    excludedName = list("ExcludedName")
                )
            }
            addIfEnabled(document, "MixinClassRename") {
                MixinClassRenamer.Config(
                    dictionary = dictionary(string("Dictionary", "Alphabet")),
                    targetMixinPackage = string("TargetMixinPackage", "net/spartanb312/obf/mixins/"),
                    mixinFile = string("MixinFile", "mixins.example.json"),
                    refmapFile = string("RefmapFile", "mixins.example.refmap.json"),
                    exclusion = list("Exclusion")
                )
            }
            addIfEnabled(document, "Crasher") {
                DecompilerCrasher.Config(
                    classFilter = classFilter(list("Exclusion")),
                    blankString = !bool("Random", false)
                )
            }
            addIfEnabled(document, "ShuffleMembers") {
                ShuffleMembers.Config(
                    classFilter = classFilter(list("Exclusion")),
                    methods = bool("Methods", true),
                    fields = bool("Fields", true),
                    annotations = bool("Annotations", true),
                    exceptions = bool("Exceptions", true)
                )
            }
            addIfEnabled(document, "Watermark") {
                Watermark.Config(
                    classFilter = classFilter(list("Exclusion")),
                    names = list("Names", Watermark.Config().names),
                    messages = list("Messages", Watermark.Config().messages),
                    fieldMark = bool("FieldMark", true),
                    methodMark = bool("MethodMark", true),
                    annotationMark = bool("AnnotationMark", false),
                    annotations = list("Annotations", Watermark.Config().annotations),
                    versions = list("Versions", Watermark.Config().versions),
                    interfaceMark = bool("InterfaceMark", false),
                    fatherOfJava = string("FatherOfJava", Watermark.Config().fatherOfJava),
                    customTrashMethod = bool("CustomTrashMethod", false),
                    customMethodName = string("CustomMethodName", Watermark.Config().customMethodName),
                    customMethodCode = string("CustomMethodCode", Watermark.Config().customMethodCode)
                )
            }
            addIfEnabled(document, "PostProcess") {
                PostProcess.Config(
                    classFilter = classFilter(emptyList()),
                    manifest = bool("Manifest", true),
                    pluginMain = bool("Plugin YML", true),
                    bungeeMain = bool("Bungee YML", true),
                    fabricMain = bool("Fabric JSON", true),
                    velocityMain = bool("Velocity JSON", true),
                    manifestReplace = list("ManifestPrefix", listOf("Main-Class:"))
                )
            }
        }

        return defaults.copy(
            input = settings.string("Input", defaults.input),
            output = settings.string("Output", defaults.output ?: "output.jar"),
            libs = settings.list("Libraries", defaults.libs),
            exclusions = settings.list("Exclusions", defaults.exclusions),
            mixinExclusions = settings.list("MixinPackage", defaults.mixinExclusions),
            dumpMappings = settings.bool("DumpMappings", defaults.dumpMappings),
            controllableRandom = settings.bool("ControllableRandom", defaults.controllableRandom),
            inputSeed = settings.string("InputSeed", defaults.inputSeed),
            multithreading = settings.bool("Multithreading", defaults.multithreading),
            printTimeUsage = settings.bool("PrintTimeUsage", defaults.printTimeUsage),
            profiler = settings.bool("Profiler", defaults.profiler),
            forceComputeMax = settings.bool("ForceUseComputeMax", defaults.forceComputeMax),
            missingCheck = settings.bool("LibsMissingCheck", defaults.missingCheck),
            corruptHeaders = corruptOutput,
            corruptCRC32 = corruptOutput,
            removeTimeStamps = settings.bool("RemoveTimeStamps", defaults.removeTimeStamps),
            compressionLevel = settings.int("CompressionLevel", defaults.compressionLevel),
            archiveComment = settings.string("ArchiveComment", defaults.archiveComment),
            fileRemovePrefix = settings.list("FileRemovePrefix", defaults.fileRemovePrefix),
            fileRemoveSuffix = settings.list("FileRemoveSuffix", defaults.fileRemoveSuffix),
            customDictionary = settings.string("CustomDictionaryFile", defaults.customDictionary),
            dictionaryStartIndex = settings.int("DictionaryStartIndex", defaults.dictionaryStartIndex),
            transformerConfigs = transformers
        )
    }

    private inline fun MutableList<TransformerConfig>.addIfEnabled(
        document: JsonObject,
        sectionName: String,
        build: SectionState.() -> TransformerConfig
    ) {
        val section = document.obj(sectionName)
        val state = SectionState(section)
        if (state.enabled) {
            add(state.build())
        }
    }

    private fun MutableList<TransformerConfig>.addControlflowIfNeeded(
        state: SectionState,
        beforeEncrypt: Boolean
    ) {
        if (!state.enabled) return
        if (state.bool("ExecuteBeforeEncrypt", false) != beforeEncrypt) return
        add(
            Controlflow.Config(
                classFilter = classFilter(state.list("Exclusion")),
                intensity = state.int("Intensity", 1),
                executeBeforeEncrypt = state.bool("ExecuteBeforeEncrypt", false),
                switchExtractor = state.bool("SwitchExtractor", true),
                extractRate = state.int("ExtractRate", 30),
                bogusConditionJump = state.bool("BogusConditionJump", true),
                gotoReplaceRate = state.int("GotoReplaceRate", 80),
                mangledCompareJump = state.bool("MangledCompareJump", true),
                ifReplaceRate = state.int("IfReplaceRate", 50),
                ifICompareReplaceRate = state.int("IfICompareReplaceRate", 100),
                switchProtect = state.bool("SwitchProtect", true),
                protectRate = state.int("ProtectRate", 30),
                tableSwitchJump = state.bool("TableSwitchJump", true),
                mutateJumps = state.bool("MutateJumps", true),
                mutateRate = state.int("MutateRate", 10),
                switchReplaceRate = state.int("SwitchReplaceRate", 30),
                maxSwitchCase = state.int("MaxSwitchCase", 5),
                reverseExistedIf = state.bool("ReverseExistedIf", true),
                reverseChance = state.int("ReverseChance", 50),
                trappedSwitchCase = state.bool("TrappedSwitchCase", true),
                trapChance = state.int("TrapChance", 50),
                arithmeticExprBuilder = state.bool("ArithmeticExprBuilder", true),
                builderIntensity = state.int("BuilderIntensity", 1),
                junkBuilderParameter = state.bool("JunkBuilderParameter", true),
                builderNativeAnnotation = state.bool("BuilderNativeAnnotation", false),
                useLocalVar = state.bool("UseLocalVar", true),
                junkCode = state.bool("JunkCode", true),
                maxJunkCode = state.int("MaxJunkCode", 2),
                expandedJunkCode = state.bool("ExpandedJunkCode", true),
                exclusion = state.list("Exclusion")
            )
        )
    }

    private fun classFilter(exclusions: List<String>): ClassFilterConfig {
        return ClassFilterConfig(
            excludeStrategy = exclusions,
            includeStrategy = listOf("**")
        )
    }

    private fun intensityToChance(intensity: Int): Double {
        return (intensity.coerceIn(0, 10) / 10.0).coerceIn(0.0, 1.0)
    }

    private fun percentageToChance(percentage: Int): Double {
        return (percentage.coerceIn(0, 100) / 100.0).coerceIn(0.0, 1.0)
    }

    private fun dictionary(name: String): NameGenerator.DictionaryType {
        return when (name) {
            "Alphabet" -> NameGenerator.DictionaryType.Alphabet
            "Numbers" -> NameGenerator.DictionaryType.Numbers
            "ConfuseIL" -> NameGenerator.DictionaryType.ConfuseIL
            "Confuse0O" -> NameGenerator.DictionaryType.Confuse0O
            "ConfuseS5" -> NameGenerator.DictionaryType.ConfuseS5
            "Arabic" -> NameGenerator.DictionaryType.Arabic
            "CustomIncrementable" -> NameGenerator.DictionaryType.CustomIncrementable
            "Custom", "CustomDictionary" -> NameGenerator.DictionaryType.CustomDictionary
            else -> NameGenerator.DictionaryType.Alphabet
        }
    }

    private class SectionState(private val section: JsonObject?) {
        val enabled: Boolean
            get() = bool("Enabled", false)

        fun bool(key: String, fallback: Boolean): Boolean {
            return section.primitive(key)?.asBoolean ?: fallback
        }

        fun int(key: String, fallback: Int): Int {
            return section.primitive(key)?.asInt ?: fallback
        }

        fun string(key: String, fallback: String): String {
            return section.primitive(key)?.asString ?: fallback
        }

        fun list(key: String, fallback: List<String> = emptyList()): List<String> {
            return section?.get(key)?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?.mapNotNull { element ->
                    if (element.isJsonNull) null else element.asString
                }
                ?: fallback
        }
    }
}

private fun JsonObject?.obj(key: String): JsonObject? {
    return this?.getAsJsonObject(key)
}

private fun JsonObject?.primitive(key: String): JsonElement? {
    val value = this?.get(key) ?: return null
    return value.takeIf { it.isJsonPrimitive }
}
