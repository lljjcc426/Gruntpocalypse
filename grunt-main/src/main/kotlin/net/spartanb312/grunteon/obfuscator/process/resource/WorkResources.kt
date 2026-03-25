package net.spartanb312.grunteon.obfuscator.process.resource

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.spartanb312.grunteon.obfuscator.util.Logger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.File

class WorkResources(
    val inputJar: JarResources
) {

    val libJars = Object2ObjectOpenHashMap<String, JarResources>()
    val libraries = Object2ObjectOpenHashMap<String, ClassNode>()

    inline val classes get() = inputJar.classes
    inline val allClasses
        get() = mutableListOf<ClassNode>().apply {
            addAll(inputJar.classes.values)
            addAll(libraries.values)
        }

    /**
     * IO jobs
     */
    fun readLibs(libs: List<String>) {
        Logger.info("Reading Libraries...")
        libs.map { File(it) }.forEach { file ->
            if (file.isDirectory) {
                readDirectory(file)
            } else {
                val jar = JarResources(file)
                jar.readInput(true)
                libJars[file.name] = jar
                libraries.putAll(jar.classes) // save lib classes
            }
        }
    }

    private fun readDirectory(directory: File) {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                readDirectory(file)
            } else {
                val jar = JarResources(file)
                jar.readInput(true)
                libJars[file.name] = jar
                libraries.putAll(jar.classes) // save lib classes
            }
        }
    }

    /**
     * Class node
     */
    fun getClassNode(name: String): ClassNode? {
        return classes[name] ?: libraries[name] ?: readInRuntime(name)
    }

    fun readInRuntime(name: String): ClassNode? {
        return try {
            val classNode = ClassNode()
            ClassReader(name).apply {
                accept(classNode, ClassReader.EXPAND_FRAMES)
                libraries[classNode.name] = classNode
            }
            classNode
        } catch (_: Exception) {
            null
        }
    }

}