package net.spartanb312.grunteon.obfuscator.process.resource

import com.google.gson.JsonObject
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.util.Logger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.ClassNode
import java.nio.file.Path
import java.util.jar.JarInputStream
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.io.path.inputStream

class JarResources(val jar: Path) {

    val classes = mutableMapOf<String, ClassNode>()
    val resources = mutableMapOf<String, ByteArray>()
    val generatedClasses = mutableMapOf<String, ClassNode>() // also included in classes

    fun readInput(lisLib: Boolean = false) {
        if (!lisLib) Logger.info("Reading $jar")
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

    val classMappings = Object2ObjectOpenHashMap<String, String>()
    val revMappings = Object2ObjectOpenHashMap<String, String>()
    val mappingObjects = Object2ObjectOpenHashMap<String, JsonObject>()

    context(instance: Grunteon)
    fun applyRemap(type: String, mappings: Map<String, String>, remapClassNames: Boolean = false) {
        if (instance.configGroup.dumpMappings) {
            val obj = JsonObject()
            mappings.forEach { (prev, new) ->
                obj.addProperty(prev, new)
                classMappings[prev] = new
                revMappings[new] = prev
            }
            mappingObjects[type] = JsonObject().apply { add(type, obj) }
        }
        val remapper = SimpleRemapper(Opcodes.ASM9, mappings)
        for ((name, node) in classes.toMutableMap()) {
            val copy = ClassNode()
            val adapter = ClassRemapper(copy, remapper)
            node.accept(adapter)
            classes[name] = copy
            generatedClasses[name]?.let { generatedClasses[name] = copy }
        }
        if (remapClassNames) {
            classes.toMap().forEach { (name, node) ->
                mappings[name]?.let { newName ->
                    classes.remove(name)
                    classes[newName] = node
                }
            }
            generatedClasses.toMap().forEach { (name, node) ->
                mappings[name]?.let { newName ->
                    generatedClasses.remove(name)
                    generatedClasses[newName] = node
                }
            }
        }
    }

}