package net.spartanb312.grunteon.obfuscator.process.transformers.encrypt.number

import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig

class NumberXorEncrypt : Transformer<NumberXorEncrypt.Config>(
    enText("process.encrypt.number.number_xor", "Number Xor Encrypt"),
    Category.Encryption
) {

    override val defConfig: TransformerConfig get() = Config()
    override val confType: Class<Config> get() = Config::class.java

    class Config : TransformerConfig() {

    }

}