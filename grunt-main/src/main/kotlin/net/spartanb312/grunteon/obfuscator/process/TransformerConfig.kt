package net.spartanb312.grunteon.obfuscator.process

import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import net.spartanb312.grunteon.obfuscator.lang.I18NDescriptorPath
import net.spartanb312.grunteon.obfuscator.process.transformers.PostProcess
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.ArithmeticSubstitute
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number.NumberBasicEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.string.StringArrayedEncrypt
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.DeclaredFieldsExtract
import net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous.ParameterObfuscate
import net.spartanb312.grunteon.obfuscator.process.transformers.optimize.*
import net.spartanb312.grunteon.obfuscator.process.transformers.other.DecompilerCrasher
import net.spartanb312.grunteon.obfuscator.process.transformers.other.FakeSyntheticBridge
import net.spartanb312.grunteon.obfuscator.process.transformers.other.ShuffleMembers
import net.spartanb312.grunteon.obfuscator.process.transformers.other.Watermark
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.FieldAccessProxy
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.InvokeDispatcher
import net.spartanb312.grunteon.obfuscator.process.transformers.redirect.InvokeProxy
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.ClassRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.FieldRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.LocalVarRenamer
import net.spartanb312.grunteon.obfuscator.process.transformers.rename.MethodRenamer
import net.spartanb312.grunteon.obfuscator.util.filters.FilterStrategy
import net.spartanb312.grunteon.obfuscator.util.filters.buildClassNamePredicates

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class SettingDesc(val enText: String)

// Optional, will fallback to property name if not present
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class SettingName(val enText: String)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class IntRangeVal(val min: Int, val max: Int, val step: Int = 1)

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DecimalRangeVal(val min: Double, val max: Double, val step: Double)

interface TransformerConfig {
    companion object {
        val projectModule = SerializersModule {
            polymorphic(TransformerConfig::class) {
                subclass(DeadCodeRemove.Config::class)
                subclass(EnumOptimize.Config::class)
                subclass(KotlinClassShrink.Config::class)
                subclass(ClassShrink.Config::class)
                subclass(SourceDebugInfoHide.Config::class)
                subclass(StringEqualsOptimize.Config::class)
                subclass(ArithmeticSubstitute.Config::class)
                subclass(NumberBasicEncrypt.Config::class)
                subclass(StringArrayedEncrypt.Config::class)
                subclass(DeclaredFieldsExtract.Config::class)
                subclass(ParameterObfuscate.Config::class)
                subclass(InvokeDispatcher.Config::class)
                subclass(InvokeProxy.Config::class)
                subclass(FieldAccessProxy.Config::class)
                subclass(LocalVarRenamer.Config::class)
                subclass(ClassRenamer.Config::class)
                subclass(FieldRenamer.Config::class)
                subclass(MethodRenamer.Config::class)
                subclass(FakeSyntheticBridge.Config::class)
                subclass(DecompilerCrasher.Config::class)
                subclass(ShuffleMembers.Config::class)
                subclass(Watermark.Config::class)
                subclass(PostProcess.Config::class)
            }
        }
    }
}

@Serializable
@I18NDescriptorPath("process.common")
data class ClassFilterConfig(
    @SettingDesc(enText = "Specify class exclusions")
    val excludeStrategy: List<String> = listOf(
        "net/dummy/**", // Exclude package
        "net/dummy/Class", // Exclude class
        "net/dummy/Event**" // Exclude prefix
    ),
    @SettingDesc(enText = "Specify class includes")
    val includeStrategy: List<String> = listOf(
        "**" // Include all
    )
) : TransformerConfig {
    fun buildFilterStrategy(): FilterStrategy {
        return FilterStrategy(
            buildClassNamePredicates(includeStrategy),
            buildClassNamePredicates(excludeStrategy)
        )
    }
}