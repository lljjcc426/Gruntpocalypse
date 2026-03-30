package net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.PipelineBuilder
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig

class Watermark : Transformer<Watermark.Config>(
    name = enText("process.miscellaneous.watermark", "Watermark"),
    category = Category.Miscellaneous,
    description = enText(
        "process.miscellaneous.watermark.desc",
        "Add watermarks"
    )
) {

    override val defConfig: TransformerConfig get() = Config()
    override val confType: Class<Config> get() = Config::class.java

    class Config : TransformerConfig() {
        val names = listOf("Endoqa Inc.", "Grunteon", "")
    }

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {

    }

}