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
    name = enText("process.misc.test_transformer", "TestTransformer"),
    category = Category.Other,
    parallel = true
) {

    override val defConfig: TransformerConfig get() = Config()
    override val confType: Class<Config> get() = Config::class.java

    class Config : TransformerConfig() {
        val boolean by setting(
            name = enText("process.misc.test_transformer.config.boolean", "Boolean"),
            value = true,
            desc = enText("process.misc.test_transformer.config.boolean", "Boolean desc"),
        )
        val integer by setting(
            name = enText("process.misc.test_transformer.config.integer", "Integer"),
            value = 1,
            range = 0..100,
            desc = enText("process.misc.test_transformer.config.integer", "Integer desc"),
        )
        val float by setting(
            name = enText("process.misc.test_transformer.config.float", "Float"),
            value = 1f,
            range = 0f..100f,
            desc = enText("process.misc.test_transformer.config.float", "Float desc"),
        )
        val list by setting(
            name = enText("process.misc.test_transformer.config.list", "List"),
            value = listOf("a", "b", "c"),
            desc = enText("process.misc.test_transformer.config.list", "List desc"),
        )
        val string by setting(
            name = enText("process.misc.test_transformer.config.string", "String"),
            value = "default",
            desc = enText("process.misc.test_transformer.config.string", "String desc"),
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
        // println(classNode.name)
    }

}