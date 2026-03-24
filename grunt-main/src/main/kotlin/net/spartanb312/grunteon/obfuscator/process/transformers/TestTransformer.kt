package net.spartanb312.grunteon.obfuscator.process.transformers

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.resource.JarResources
import net.spartanb312.grunteon.obfuscator.process.resource.WorkResources
import org.objectweb.asm.tree.ClassNode

class TestTransformer : Transformer<TestTransformer.Config>(
    enText("process.misc.test_transformer", "TestTransformer"),
    Category.Other
) {

    override val defConfig: TransformerConfig get() = Config()
    override val confType: Class<Config> get() = Config::class.java

    class Config : TransformerConfig() {
        val boolean by setting(
            enText("process.misc.test_transformer.config.boolean", "Boolean"),
            true,
            enText("process.misc.test_transformer.config.boolean", "Boolean desc"),
        )
        val integer by setting(
            enText("process.misc.test_transformer.config.integer", "Integer"),
            1,
            0..100,
            1,
            enText("process.misc.test_transformer.config.integer", "Integer desc"),
        )
        val float by setting(
            enText("process.misc.test_transformer.config.float", "Float"),
            1f,
            0f..100f,
            1f,
            enText("process.misc.test_transformer.config.float", "Float desc"),
        )
        val list by setting(
            enText("process.misc.test_transformer.config.list", "List"),
            listOf("a", "b", "c"),
            enText("process.misc.test_transformer.config.list", "List desc"),
        )
        val string by setting(
            enText("process.misc.test_transformer.config.string", "String"),
            "default",
            enText("process.misc.test_transformer.config.string", "String desc"),
        )
    }

    context(instance: Grunteon, res: WorkResources, jar: JarResources)
    override fun transformClass(
        classNode: ClassNode,
        config: Config,
    ) {
        config.boolean
        config.integer
        config.float
        config.list
        config.string
    }

}