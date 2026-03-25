package net.spartanb312.grunteon.obfuscator.process.resource

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.spartanb312.grunteon.obfuscator.util.Logger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import kotlin.io.path.*

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
        runBlocking {
            val jars = libs.asSequence()
                .map { Path(it) }
                .flatMap { it.walk() }
                .filterNot { it.isDirectory() }
                .filter { it.extension == "jar" }
                .map { JarResources(it) }
                .toList()

            coroutineScope {
                jars.forEach {
                    launch(Dispatchers.Default) { it.readInput(true) }
                }
            }

            jars.forEach {
                libJars[it.jar.name] = it
                libraries.putAll(it.classes) // save lib classes
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