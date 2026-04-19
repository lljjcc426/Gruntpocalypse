package net.spartanb312.grunteon.obfuscator.process.transformers.miscellaneous

import kotlinx.serialization.Serializable
import net.spartanb312.genesis.kotlin.clazz
import net.spartanb312.genesis.kotlin.extensions.*
import net.spartanb312.genesis.kotlin.extensions.insn.ALOAD
import net.spartanb312.genesis.kotlin.extensions.insn.INVOKESPECIAL
import net.spartanb312.genesis.kotlin.extensions.insn.RETURN
import net.spartanb312.genesis.kotlin.field
import net.spartanb312.genesis.kotlin.method
import net.spartanb312.genesis.kotlin.modify
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.PipelineBuilder
import net.spartanb312.grunteon.obfuscator.process.SettingDesc
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.post
import net.spartanb312.grunteon.obfuscator.process.pre
import net.spartanb312.grunteon.obfuscator.util.GENERATED_CLASS
import net.spartanb312.grunteon.obfuscator.util.GENERATED_FIELD
import net.spartanb312.grunteon.obfuscator.util.GENERATED_METHOD
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.extensions.appendAnnotation
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode

class TrashClass : Transformer<TrashClass.Config>(
    name = enText("process.miscellaneous.trash_class", "TrashClass"),
    category = Category.Miscellaneous,
    description = enText(
        "process.miscellaneous.trash_class.desc",
        "Generate standalone trash classes into the output jar"
    )
) {
    @Serializable
    data class Config(
        @SettingDesc(enText = "Package prefix used for generated trash classes")
        val packageName: String = "net/spartanb312/obf/",
        @SettingDesc(enText = "Shared class name prefix")
        val prefix: String = "Trash",
        @SettingDesc(enText = "Number of trash classes to generate")
        val count: Int = 0
    ) : TransformerConfig

    context(instance: Grunteon, pipelineBuilder: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        var generatedCount = 0
        pre {
            if (config.count <= 0) return@pre
            val random = Xoshiro256PPRandom(instance.obfConfig.baseSeed().toByteArray())
            repeat(config.count) {
                val generated = generateClass(config, random, it)
                instance.workRes.addGeneratedClass(generated)
                generatedCount++
            }
        }
        post {
            Logger.info(" - TrashClass:")
            Logger.info("    Generated $generatedCount trash classes")
        }
    }

    private fun generateClass(
        config: Config,
        random: org.apache.commons.rng.UniformRandomProvider,
        index: Int
    ): ClassNode {
        val superClass = TRASH_SUPERS[random.nextInt(TRASH_SUPERS.size)]
        val normalizedPackage = config.packageName.replace('.', '/').trim().trim('/')
        val className = buildString {
            if (normalizedPackage.isNotEmpty()) {
                append(normalizedPackage)
                append('/')
            }
            append(config.prefix)
            append(index)
            append('_')
            append(randomString(random, 5))
        }
        return clazz(
            PUBLIC + SUPER,
            className,
            superClass
        ).appendAnnotation(GENERATED_CLASS).modify {
            +field(
                PUBLIC + STATIC,
                "c",
                "I",
                null,
                8964
            ).appendAnnotation(GENERATED_FIELD)
            +method(
                PUBLIC,
                "<init>",
                "()V"
            ) {
                INSTRUCTIONS {
                    ALOAD(0)
                    INVOKESPECIAL(superClass, "<init>", "()V")
                    RETURN
                }
                MAXS(1, 1)
            }.appendAnnotation(GENERATED_METHOD)
        }
    }

    private fun randomString(
        random: org.apache.commons.rng.UniformRandomProvider,
        length: Int
    ): String {
        val chars = CharArray(length)
        repeat(length) { index ->
            chars[index] = ALPHABET[random.nextInt(ALPHABET.length)]
        }
        return String(chars)
    }

    companion object {
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private val TRASH_SUPERS = arrayOf(
            "java/util/concurrent/ConcurrentHashMap",
            "java/util/concurrent/ConcurrentLinkedDeque",
            "java/util/concurrent/ConcurrentSkipListMap"
        )
    }
}
