package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import net.spartanb312.grunteon.obfuscator.process.resource.JarResources
import net.spartanb312.grunteon.obfuscator.process.resource.NameGenerator
import net.spartanb312.grunteon.obfuscator.process.resource.WorkResources
import net.spartanb312.grunteon.obfuscator.util.interfaces.DisplayEnum

class ClassRenamer : Transformer<ClassRenamer.Config>(
    name = enText("process.rename.class_renamer", "ClassRenamer"),
    category = Category.Renaming,
    parallel = false
) {

    override val defConfig: TransformerConfig get() = Config()
    override val confType: Class<Config> get() = Config::class.java

    class Config : TransformerConfig() {
        val dictionary by setting(
            name = enText("process.rename.class_renamer.config.dictionary", "Dictionary"),
            value = NameGenerator.Dictionary.Alphabet,
            desc = enText("process.rename.class_renamer.config.dictionary.desc", "Dictionary for renamer")
        )
        val parent by setting(
            name = enText("process.rename.class_renamer.config.package", "Package"),
            value = "net/spartanb312/obf/",
            desc = enText("process.rename.class_renamer.config.package.desc", "Parent package for target name")
        )
    }

    context(instance: Grunteon, res: WorkResources, jar: JarResources)
    override fun transform(config: Config) {

    }

}