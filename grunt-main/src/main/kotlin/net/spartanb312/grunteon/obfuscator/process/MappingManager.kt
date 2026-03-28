package net.spartanb312.grunteon.obfuscator.process

import com.google.gson.JsonObject
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.grunteon.obfuscator.Grunteon
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.ClassNode

class MappingManager {
    val mappings = Array(MappingType.entries.size) { Object2ObjectOpenHashMap<String, String>() }
    val classMappings = Object2ObjectOpenHashMap<String, String>()
    val revMappings = Object2ObjectOpenHashMap<String, String>()
    val mappingObjects = Array(MappingType.entries.size) { JsonObject() }

    context(instance: Grunteon)
    fun addMapping(type: MappingType, prev: String, new: String) {
        if (instance.configGroup.dumpMappings) {
            mappings[type.ordinal][prev] = new
            mappingObjects[type.ordinal].addProperty(prev, new)
            classMappings[prev] = new
            revMappings[new] = prev
        }
    }

    context(pb: PipelineBuilder)
    fun applyRemap(type: MappingType) {
        val mapping = mappings[type.ordinal]
        val remapper = SimpleRemapper(Opcodes.ASM9, mapping)
        val newClasses = reducibleScopeValue {
            MergeableObjectList(ObjectArrayList<ClassNode>())
        }
        parForEach {
            val copy = ClassNode()
            val adapter = ClassRemapper(copy, remapper)
            it.accept(adapter)
            newClasses.local.add(copy)
        }
        seq {
            val instance = contextOf<Grunteon>()
            instance.workRes.inputClassMap.clear()
            newClasses.global.forEach { instance.workRes.inputClassMap[it.name] = it }
        }
    }

    enum class MappingType {
        Classes, Methods, Fields
    }
}