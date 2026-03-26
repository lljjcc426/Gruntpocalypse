package net.spartanb312.grunteon.obfuscator.process

import com.google.gson.JsonObject
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.spartanb312.grunteon.obfuscator.Grunteon
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.ClassNode

class MappingApplier(private val instance: Grunteon) {
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
        instance.workRes.inputClassMap.toList().forEach { (name, node) ->
            val copy = ClassNode()
            val adapter = ClassRemapper(copy, remapper)
            node.accept(adapter)
            instance.workRes.inputClassMap[name] = copy
            instance.workRes.generatedClassMap.replace(name, copy)
        }
        if (remapClassNames) {
            instance.workRes.inputClassMap.toList().forEach { (name, node) ->
                mappings[name]?.let { newName ->
                    instance.workRes.inputClassMap.remove(name)
                    instance.workRes.inputClassMap[newName] = node
                }
            }
            instance.workRes.generatedClassMap.toList().forEach { (name, node) ->
                mappings[name]?.let { newName ->
                    instance.workRes.generatedClassMap.remove(name)
                    instance.workRes.generatedClassMap[newName] = node
                }
            }
        }
    }
}