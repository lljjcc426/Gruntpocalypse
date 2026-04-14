package net.spartanb312.grunteon.obfuscator.process.transformers.other

import net.spartanb312.genesis.kotlin.extensions.PRIVATE
import net.spartanb312.genesis.kotlin.extensions.STATIC
import net.spartanb312.genesis.kotlin.extensions.insn.ARETURN
import net.spartanb312.genesis.kotlin.extensions.insn.LDC
import net.spartanb312.genesis.kotlin.field
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.PipelineBuilder
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.parForEachClassesFiltered
import net.spartanb312.grunteon.obfuscator.process.post
import net.spartanb312.grunteon.obfuscator.process.pre
import net.spartanb312.grunteon.obfuscator.process.reducibleScopeValue
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.MergeableCounter
import net.spartanb312.grunteon.obfuscator.util.extensions.isInterface

class Watermark : Transformer<Watermark.Config>(
    name = enText("process.other.watermark", "Watermark"),
    category = Category.Other,
    description = enText(
        "process.other.watermark.desc",
        "Add watermarks"
    )
) {

    override val defConfig: TransformerConfig get() = Config()
    override val confType: Class<Config> get() = Config::class.java

    class Config : TransformerConfig() {
        val names by setting(
            name = enText("process.other.watermark.names", "Names"),
            value = listOf("Grunt", "Gruntpocalypse", "Grunteon"),
            desc = enText("process.other.watermark.names.desc", "Watermark member names")
        )
        val messages by setting(
            name = enText("process.other.watermark.messages", "Messages"),
            value = listOf(
                "PROTECTED BY GRUNTEON",
                "PROTECTED BY EVERETT",
                "PROTECTED BY YuShengJun"
            ),
            desc = enText("process.other.watermark.messages.desc", "Watermark messages"),
        )
        val fieldMark by setting(
            name = enText("process.other.watermark.field", "Field mark"),
            value = true,
            desc = enText("process.other.watermark.field.desc", "Add field watermark")
        )
        val methodMark by setting(
            name = enText("process.other.watermark.method", "Method mark"),
            value = true,
            desc = enText("process.other.watermark.method.desc", "Add method watermark")
        )
    }

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        pre {
            //Logger.info(" > Watermark: Adding watermarks...")
        }
        val counter = reducibleScopeValue { MergeableCounter() }
        parForEachClassesFiltered(buildFilterStrategy(config)) { classNode ->
            if (classNode.isInterface) return@parForEachClassesFiltered
            val counter = counter.local
            if (config.fieldMark) {
                classNode.fields = classNode.fields ?: arrayListOf()
                val marker = config.messages.random()
                when ((0..2).random()) {
                    0 -> classNode.fields.add(
                        field(
                            PRIVATE + STATIC,
                            config.names.random(),
                            "Ljava/lang/String;",
                            null,
                            marker
                        )
                    )

                    1 -> classNode.fields.add(
                        field(
                            PRIVATE + STATIC,
                            "_$marker _",
                            "I",
                            null,
                            listOf(114514, 1919810, 69420, 911, 8964).random()
                        )
                    )

                    2 -> classNode.fields.add(
                        field(
                            PRIVATE + STATIC,
                            config.names.random(),
                            "Ljava/lang/String;",
                            null,
                            marker
                        )
                    )
                }
                counter.add()
            }
            if (config.methodMark) {
                classNode.methods = classNode.methods ?: arrayListOf()
                val marker = config.messages.random()
                when ((0..2).random()) {
                    0 -> classNode.methods.add(
                        method(
                            PRIVATE + STATIC,
                            config.names.random(),
                            "()Ljava/lang/String;"
                        ) {
                            INSTRUCTIONS {
                                LDC(marker)
                                ARETURN
                            }
                        }
                    )

                    1 -> classNode.methods.add(
                        method(
                            PRIVATE + STATIC,
                            config.names.random(),
                            "()Ljava/lang/String;"
                        ) {
                            INSTRUCTIONS {
                                LDC(marker)
                                ARETURN
                            }
                        }
                    )

                    2 -> classNode.methods.add(
                        method(
                            PRIVATE + STATIC,
                            config.names.random(),
                            "()Ljava/lang/String;"
                        ) {
                            INSTRUCTIONS {
                                LDC(marker)
                                ARETURN
                            }
                        }
                    )
                }
                counter.add()
            }
        }
        post {
            Logger.info(" - Watermark:")
            Logger.info("    Added ${counter.global.get()} watermarks")
        }
    }

}