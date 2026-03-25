package net.spartanb312.grunteon.obfuscator.config.manager

import net.spartanb312.grunteon.obfuscator.config.Configurable
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.Transformer
import net.spartanb312.grunteon.obfuscator.process.TransformerConfig
import java.util.zip.Deflater

class ConfigGroup : Configurable() {

    /**
     * General configs
     */
    val input by setting(
        name = enText("config.input", "Input"),
        value = "input.jar",
        desc = enText("config.input.desc", "The input jar that will be obfuscated")
    )
    val output by setting(
        name = enText("config.output", "Output"),
        value = "output.jar",
        desc = enText("config.output.desc", "The output obfuscated jar")
    )
    val libs by setting(
        name = enText("config.libs", "Libraries"),
        value = listOf(),
        desc = enText("config.libs.desc", "Dependencies of the input jar")
    )
    val exclusions by setting(
        name = enText("config.exclusions", "Exclusions"),
        value = listOf(
            "net/example/package/**",
            "net/example/Class"
        ),
        desc = enText("config.exclusions.desc", "Global hard exclusions")
    )
    val mixinExclusions by setting(
        name = enText("config.mixin_exclusions", "Mixin exclusions"),
        value = listOf(
            "net/spartanb312/client/mixins/**",
            "net/spartanb312/common/MixinExampleClass"
        ),
        desc = enText("config.mixin_exclusions.desc", "Minecraft mixin exclusions. For mods or plugins")
    )
    val dumpMappings by setting(
        name = enText("config.dump_mappings", "Dump mappings"),
        value = true,
        desc = enText("config.dump_mappings.desc", "Dump class/method/field mappings")
    )
    val profiler by setting(
        name = enText("config.profiler", "Profiler"),
        value = true,
        desc = enText("config.profiler.desc", "Print time usage for each obfuscation stages")
    )
    val forceComputeMax by setting(
        name = enText("config.force.compute_max", "Force ComputeMaxs mode"),
        value = false,
        desc = enText("config.force.compute_max.desc", "Enabled: ComputeMaxs, Disabled: ComputeFrames")
    )
    val missingCheck by setting(
        name = enText("config.missing_check", "Dependency missing check"),
        value = true,
        desc = enText("config.missing_check.desc", "Dependency missing check")
    )

    /**
     * Features
     */
    val corruptHeaders by setting(
        name = enText("config.corrupt_headers", "Corrupt headers"),
        value = false,
        desc = enText("config.corrupt_headers.desc", "Corrupt file headers")
    )
    val corruptCRC32 by setting(
        name = enText("config.corrupt_CRC32", "Corrupt CRC32"),
        value = false,
        desc = enText("config.corrupt_CRC32.desc", "Corrupt zip CRC32")
    )
    val removeTimeStamps by setting(
        name = enText("config.remove_time_stamps", "Remove time stamps"),
        value = false,
        desc = enText("config.remove_time_stamps.desc", "Remove time stamps")
    )
    val compressionLevel by setting(
        name = enText("config.compression_level", "Compression level"),
        value = Deflater.BEST_COMPRESSION,
        desc = enText("config.compression_level.desc", "Output jar compression level")
    )
    val archiveComment by setting(
        name = enText("config.archive_comment", "Archive comment"),
        value = "",
        desc = enText("config.archive_comment.desc", "Output jar file archive comment")
    )
    val fileRemovePrefix by setting(
        name = enText("config.file_remove_prefix", "File remove prefix"),
        value = listOf(),
        desc = enText("config.file_remove_prefix.desc", "File with specified prefix will be removed")
    )
    val fileRemoveSuffix by setting(
        name = enText("config.file_remove_suffix", "File remove suffix"),
        value = listOf(),
        desc = enText("config.file_remove_suffix.desc", "File with specified suffix will be removed")
    )


    /**
     * Transformer configs
     */
    val configs = mutableListOf<TransformerConfig>()

    fun getConfig(transformer: Transformer<*>, index: Int): TransformerConfig {
        val conf = configs.getOrNull(index) ?: transformer.defConfig
        if (!transformer.qualifyConfig(conf, true)) return transformer.defConfig
        return conf
    }

}