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
import net.spartanb312.grunteon.obfuscator.util.extensions.isExcluded
import net.spartanb312.grunteon.obfuscator.util.file.corruptCRC32
import net.spartanb312.grunteon.obfuscator.util.file.corruptJarHeader
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

class JarDumper(
    val instance: Grunteon,
    val outputFile: File,
    val forceComputeMax: Boolean = false,
    val missingCheck: Boolean = true,
    // features
    val corruptHeader: Boolean = false,
    val corruptCRC32: Boolean = false,
    val removeTimestamps: Boolean = false,
    val compressionLevel: Int = Deflater.BEST_COMPRESSION,
    val archiveComment: String = "",
    // file remove
    val fileRemovePrefix: List<String> = emptyList(),
    val fileRemoveSuffix: List<String> = emptyList(),
) {

    context(instance: Grunteon)
    fun dumpJar() {
        Logger.info("Dumping jar to ${outputFile.path}")
        if (outputFile.exists()) Logger.warn("Existing output file will be overridden!")
        outputFile.parentFile.mkdirs()
        val outputStream = outputFile.outputStream()
        // Corrupt header
        if (corruptHeader) {
            Logger.info("Corrupting jar header...")
            corruptJarHeader(outputStream)
        }
        ZipOutputStream(outputStream).apply {
            // Compression level
            setLevel(compressionLevel)
            // Archive comment
            if (archiveComment.isNotEmpty()) setComment(archiveComment)
            // Corrupt CRC32
            if (corruptCRC32) {
                Logger.info("Corrupting CRC32...")
                corruptCRC32()
            }
            // Build hierarchy
            Logger.info("Building hierarchies...")
            val hierarchy = ClassHierarchy.build(instance.workRes.allClassCollection, instance.workRes::getClassNode)
            // Writing class
            Logger.info("Writing classes...")
            val mutex = Mutex()

            //hierarchy.findClass("java/lang/Float") ?: throw Exception()

            //hierarchy.classNameLookUp.forEach { (string, i) -> if (string.startsWith("java"))println("$string -> $i") }

            runBlocking {
                // TODO: handle resource
                for (classNode in instance.workRes.inputClassCollection) {
                    // File remove
                    if (classNode.name == "module-info" || classNode.name.shouldRemove) continue
                    val missingList = hierarchy.checkMissing(classNode)
                    val classInfo = hierarchy.findClass(classNode.name)!!
                    launch(Dispatchers.IO) {
                        // Dependency check
                        val missingRef = missingList.isNotEmpty()
                        if (missingRef && missingCheck) {
                            Logger.error("Class ${classNode.name} missing reference:")
                            for (missing in missingList) {
                                Logger.error(" - $missing")
                            }
                        }
                        val missingAny = (hierarchy.missingDependencies[classInfo] || missingRef) && missingCheck
                        val useComputeMax = forceComputeMax || missingAny || classNode.isExcluded
                        val missing = missingAny && !forceComputeMax && !classNode.isExcluded
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
                            if (removeTimestamps) zipEntry.time = 0
                            putNextEntry(zipEntry)
                            write(byteArray)
                            closeEntry()
                        }
                    }
                }
            }
            if (missingCheck) hierarchy.printMissing()

            Logger.info("Writing resources...")
            instance.workRes.inputResourceSet.root.walk()
                .filter { !it.isDirectory() }
                .filter { it.extension != "class" }
                .filterNot { it.name.shouldRemove }
                .forEach {
                    val zipEntry = ZipEntry(it.pathString)
                    if (removeTimestamps) zipEntry.time = 0
                    putNextEntry(zipEntry)
                    write(it.readBytes())
                    closeEntry()
                }
            close()

            // TODO: dump mappings
        }
    }

    inline val String.shouldRemove get() = fileRemovePrefix.any { startsWith(it) } || fileRemoveSuffix.any { endsWith(it) }

}