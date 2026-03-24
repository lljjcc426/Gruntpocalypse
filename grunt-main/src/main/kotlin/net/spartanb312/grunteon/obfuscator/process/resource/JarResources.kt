package net.spartanb312.grunteon.obfuscator.process.resource

import net.spartanb312.grunteon.obfuscator.util.Logger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.jar.JarFile
import kotlin.sequences.forEach

class JarResources(val jar: File) {

    val classes = mutableMapOf<String, ClassNode>()
    val resources = mutableMapOf<String, ByteArray>()
    val generatedClasses = mutableMapOf<String, ClassNode>()

    fun readInput() {
        Logger.info("Reading ${jar.path}")
        JarFile(jar).apply {
            entries().asSequence()
                .filter { !it.isDirectory }
                .forEach {
                    if (it.name.endsWith(".class")) {
                        kotlin.runCatching {
                            ClassReader(getInputStream(it)).apply {
                                val classNode = ClassNode()
                                accept(classNode, ClassReader.EXPAND_FRAMES)
                                classes[classNode.name] = classNode
                            }
                        }
                    } else resources[it.name] = getInputStream(it).readBytes()
                }
        }
    }

}