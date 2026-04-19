package net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous

import kotlinx.serialization.Serializable
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import org.objectweb.asm.tree.ClassNode

class ClonedClass : Transformer<ClonedClass.Config>(
    name = enText("process.miscellaneous.cloned_class", "ClonedClass"),
    category = Category.Miscellaneous,
    description = enText(
        "process.miscellaneous.cloned_class.desc",
        "Clone random existing classes as redundant output classes"
    )
) {
    @Serializable
    data class Config(
        @SettingDesc(enText = "Number of cloned classes to generate")
        val count: Int = 0,
        @SettingDesc(enText = "Suffix appended to cloned class names")
        val suffix: String = "-cloned",
        @SettingDesc(enText = "Remove class-level annotations from cloned classes")
        val removeAnnotations: Boolean = true,
        @SettingDesc(enText = "Specify class exclusions.")
        val exclusion: List<String> = listOf(
            "net/dummy/**",
            "net/dummy/Class"
        )
    ) : TransformerConfig

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        var generatedCount = 0
        pre {
            if (config.count <= 0) return@pre

            val classFilter = ClassFilterConfig(excludeStrategy = config.exclusion)
            val strategy = classFilter.buildFilterStrategy()
            val workingRange = instance.workRes.inputClassCollection
                .asSequence()
                .filter { strategy.testClass(it) }
                .toList()
            if (workingRange.isEmpty()) return@pre

            val cloneNameMap = hashMapOf<String, Int>()
            val random = Xoshiro256PPRandom(instance.obfConfig.baseSeed().toByteArray())

            repeat(config.count) {
                val origin = workingRange[random.nextInt(workingRange.size)]
                val cloneId = cloneNameMap.getOrPut(origin.name) { 0 }
                cloneNameMap[origin.name] = cloneId + 1

                val clone = ClassNode()
                origin.accept(clone)
                clone.name = origin.name + config.suffix + "$$" + cloneId
                if (config.removeAnnotations) {
                    clone.visibleAnnotations?.clear()
                    clone.invisibleAnnotations?.clear()
                    clone.visibleTypeAnnotations?.clear()
                    clone.invisibleTypeAnnotations?.clear()
                }
                instance.workRes.addGeneratedClass(clone)
                generatedCount++
            }
        }
        post {
            Logger.info(" - ClonedClass:")
            Logger.info("    Generated $generatedCount cloned classes")
        }
    }
}
