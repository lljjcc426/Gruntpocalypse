package net.spartanb312.grunteon.obfuscator.process.resource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.util.ClearClassNode
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.cryptography.Xoshiro256PPRandom
import net.spartanb312.grunteon.obfuscator.util.cryptography.getSeed
import net.spartanb312.grunteon.obfuscator.util.extensions.isExcluded
import net.spartanb312.grunteon.obfuscator.util.file.corruptCRC32
import net.spartanb312.grunteon.obfuscator.util.file.corruptJarHeader
import org.objectweb.asm.Opcodes
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

object JarDumper {
    context(instance: Grunteon)
    fun dumpJar(outputFile: Path) {
        val config = instance.configGroup

        fun checkFileNameRemove(name: String): Boolean {
            return config.fileRemovePrefix.any { name.startsWith(it) }
                || config.fileRemoveSuffix.any { name.endsWith(it) }
        }

        Logger.info("Dumping jar to $outputFile")
        if (outputFile.exists()) Logger.warn("Existing output file will be overridden!")
        outputFile.parent.createDirectories()
        val outputStream = outputFile.outputStream()
        // Corrupt header
        if (config.corruptHeaders) {
            Logger.info("Corrupting jar header...")
            val random = Xoshiro256PPRandom(
                getSeed(
                    config.input,
                    config.output,
                    "corruptHeader",
                )
            )
            corruptJarHeader(random, outputStream)
        }
        ZipOutputStream(outputStream).use { zipOut ->
            // Compression level
            zipOut.setLevel(config.compressionLevel)
            // Archive comment
            if (config.archiveComment.isNotEmpty()) zipOut.setComment(config.archiveComment)
            // Corrupt CRC32
            if (config.corruptCRC32) {
                Logger.info("Corrupting CRC32...")
                val random = Xoshiro256PPRandom(
                    getSeed(
                        config.input,
                        config.output,
                        "corruptCRC32",
                    )
                )
                zipOut.corruptCRC32(random)
            }

            // Build hierarchy
            Logger.info("Building hierarchies...")
            val hierarchy = ClassHierarchy.build(instance.workRes.allClassCollection, instance.workRes::getClassNode)
            // Writing class
            Logger.info("Writing classes...")
            val mutex = Mutex()

            runBlocking {
                // TODO: handle resource
                for (classNode in instance.workRes.inputClassCollection) {
                    // File remove
                    if (classNode.name == "module-info" || checkFileNameRemove(classNode.name)) continue
                    val missingList = hierarchy.checkMissing(classNode)
                    val classInfo = hierarchy.findClass(classNode.name)!!
                    launch(Dispatchers.IO) {
                        // Dependency check
                        val missingRef = missingList.isNotEmpty()
                        if (missingRef && config.missingCheck) {
                            Logger.error("Class ${classNode.name} missing reference:")
                            for (missing in missingList) {
                                Logger.error(" - $missing")
                            }
                        }
                        val missingAny = (hierarchy.missingDependencies[classInfo] || missingRef) && config.missingCheck
                        val useComputeMax = config.forceComputeMax || missingAny || classNode.isExcluded
                        val missing = missingAny && !config.forceComputeMax && !classNode.isExcluded
                        // Write zip entry
                        val entryName = classNode.name + ".class"
                        val byteArray = try {
                            if (missing) Logger.warn("Using COMPUTE_MAXS due to ${classNode.name} missing dependencies or reference.")
                            ClassDumper(instance, hierarchy, useComputeMax).apply {
                                classNode.accept(ClearClassNode(Opcodes.ASM9, this))
                            }.toByteArray()
                        } catch (exception: Exception) {
                            Logger.error("Failed to dump class ${classNode.name}. Trying ${if (useComputeMax) "COMPUTE_FRAMES" else "COMPUTE_MAXS"}")
                            exception.printStackTrace()
                            try {
                                ClassDumper(instance, hierarchy, !useComputeMax).apply {
                                    classNode.accept(ClearClassNode(Opcodes.ASM9, this))
                                }.toByteArray()
                            } catch (exception: Exception) {
                                Logger.error("Failed to dump class ${classNode.name}!")
                                exception.printStackTrace()
                                ByteArray(0)
                            }
                        }
                        // TODO: optimize
                        mutex.withLock {
                            val zipEntry = ZipEntry(entryName)
                            if (config.removeTimeStamps) zipEntry.time = 0
                            zipOut.putNextEntry(zipEntry)
                            zipOut.write(byteArray)
                            zipOut.closeEntry()
                        }
                    }
                }
            }
            if (config.missingCheck) hierarchy.printMissing()

            Logger.info("Writing resources...")
            instance.workRes.inputResourceSet.root.walk()
                .filter { !it.isDirectory() }
                .filter { it.extension != "class" }
                .filterNot { checkFileNameRemove(it.name) }
                .forEach {
                    val zipEntry = ZipEntry(it.pathString)
                    if (config.removeTimeStamps) zipEntry.time = 0
                    zipOut.putNextEntry(zipEntry)
                    zipOut.write(it.readBytes())
                    zipOut.closeEntry()
                }
            // TODO: dump mappings
        }
    }
}