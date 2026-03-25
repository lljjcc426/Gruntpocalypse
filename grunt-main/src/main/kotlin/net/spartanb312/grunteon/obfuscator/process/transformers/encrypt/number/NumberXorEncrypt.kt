package net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.resource.JarResources
import net.spartanb312.grunteon.obfuscator.process.resource.WorkResources
import org.objectweb.asm.tree.ClassNode

class NumberXorEncrypt : Transformer<NumberXorEncrypt.Config>(
    name = enText("process.encrypt.number.number_xor", "Number Xor Encrypt"),
    category = Category.Encryption,
    parallel = true
) {

    override val defConfig: TransformerConfig get() = Config()
    override val confType: Class<Config> get() = Config::class.java

    class Config : TransformerConfig() {

    }

    context(instance: Grunteon, res: WorkResources, jar: JarResources)
    override fun transformClass(classNode: ClassNode, config: Config) {

    }

}