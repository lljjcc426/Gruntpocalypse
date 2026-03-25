package net.spartanb312.grunteon.obfuscator.process.resource

import net.spartanb312.grunteon.obfuscator.util.Logger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.nio.file.Path
import java.util.jar.JarInputStream
import kotlin.io.path.inputStream

class JarResources(val jar: Path) {

    val classes = mutableMapOf<String, ClassNode>()
    val resources = mutableMapOf<String, ByteArray>()
    val generatedClasses = mutableMapOf<String, ClassNode>() // also included in classes

    fun readInput(lisLib: Boolean = false) {
        if (lisLib) Logger.info(" - $jar") else Logger.info("Reading $jar")
        JarInputStream(jar.inputStream()).use {
            var entry = it.nextJarEntry
            while (entry != null) {
                if (entry.isDirectory) {
                    entry = it.nextJarEntry
                    continue
                }

                if (entry.name.endsWith(".class")) {
                    kotlin.runCatching {
                        ClassReader(it).apply {
                            val classNode = ClassNode()
                            accept(classNode, ClassReader.EXPAND_FRAMES)
                            if (classNode.name == "module-info") return@apply
                            classes[classNode.name] = classNode
                        }
                    }
                } else if (!lisLib) {
                    resources[entry.name] = it.readBytes()
                }

                entry = it.nextJarEntry
            }
        }
    }
}