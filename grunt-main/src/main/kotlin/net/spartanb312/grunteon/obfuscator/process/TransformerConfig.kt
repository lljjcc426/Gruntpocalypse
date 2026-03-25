package net.spartanb312.grunteon.obfuscator.process

import net.spartanb312.grunteon.obfuscator.config.Configurable
import net.spartanb312.grunteon.obfuscator.lang.enText

abstract class TransformerConfig : Configurable() {

    val excludeStrategy by setting(
        enText("process.common.exclude_strategy", "Exclude strategy"),
        listOf(
            "net/dummy/**", // Exclude package
            "net/dummy/Class", // Exclude class
            "net/dummy/Event**" // Exclude prefix
        ),
        enText("process.common.exclude_strategy.desc", "Specify class exclusions"),
    )

    val includeStrategy by setting(
        enText("process.common.include_strategy", "Include strategy"),
        listOf(
            "**" // Include all
        ),
        enText("process.common.include_strategy.desc", "Specify class includes"),
    )

}