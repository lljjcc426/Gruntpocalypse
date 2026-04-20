package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import it.unimi.dsi.fastutil.objects.Object2ObjectMap
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.Remapper
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.bufferedWriter

class NameMapping : Remapper(Opcodes.ASM9) {

    private val classMappings = Object2ObjectOpenHashMap<String, ClassEntry>()
    private val reverseClassMappings = ConcurrentHashMap<String, String>()
    private val indyMapping = ConcurrentHashMap<String, String>()
    private val serviceFileMappings = ConcurrentHashMap<String, String>()
    private val serviceImplementationMappings = ConcurrentHashMap<String, String>()
    private val manifestMappings = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

    fun getMapping(old: String): String? {
        return classMappings.getOrDefault(old, null)?.new
    }

    fun dump(path: Path, context: DumpContext? = null) {
        path.bufferedWriter().use {
            val jsonObj = JsonObject().apply {
                add("meta", JsonObject().apply {
                    addProperty("schema", "grunteon/mappings@1")
                    addProperty("generatedAt", Instant.now().toString())
                    context?.let {
                        addProperty("input", it.input)
                        addProperty("output", it.output)
                    }
                })
                add("pipeline", JsonObject().apply {
                    addProperty("kind", "transformer-pipeline")
                    addProperty("multithreading", context?.multithreading ?: false)
                    addProperty("profiler", context?.profiler ?: false)
                    add("steps", com.google.gson.JsonArray().apply {
                        context?.steps?.forEach(::add)
                    })
                })
                add("summary", JsonObject().apply {
                    addProperty("classCount", classMappings.size)
                    addProperty("methodCount", classMappings.values.sumOf { it.methodMapping.size })
                    addProperty("fieldCount", classMappings.values.sumOf { it.fieldMapping.size })
                    addProperty("indyCount", indyMapping.size)
                })
                add("classes", JsonObject().apply {
                    classMappings.entries.sortedBy { it.key }.forEach { (prev, entry) ->
                        add(prev, JsonObject().apply {
                            addProperty("new", entry.new)
                            add("methods", JsonObject().apply {
                                entry.methodMapping.entries.sortedBy { it.key }.forEach { (k, v) ->
                                    addProperty(k, v)
                                }
                            })
                            add("fields", JsonObject().apply {
                                entry.fieldMapping.entries.sortedBy { it.key }.forEach { (k, v) ->
                                    addProperty(k, v)
                                }
                            })
                        })
                    }
                })
                add("invokedynamic", JsonObject().apply {
                    indyMapping.entries.sortedBy { it.key }.forEach { (k, v) ->
                        addProperty(k, v)
                    }
                })
                add("resources", JsonObject().apply {
                    add("services", JsonObject().apply {
                        add("files", JsonObject().apply {
                            serviceFileMappings.entries.sortedBy { it.key }.forEach { (k, v) ->
                                addProperty(k, v)
                            }
                        })
                        add("implementations", JsonObject().apply {
                            serviceImplementationMappings.entries.sortedBy { it.key }.forEach { (k, v) ->
                                addProperty(k, v)
                            }
                        })
                    })
                    add("manifest", JsonObject().apply {
                        manifestMappings.entries.sortedBy { it.key }.forEach { (attribute, values) ->
                            add(attribute, JsonObject().apply {
                                values.entries.sortedBy { it.key }.forEach { (k, v) ->
                                    addProperty(k, v)
                                }
                            })
                        }
                    })
                    add("classResources", JsonObject().apply {
                        classMappings.entries.sortedBy { it.key }.forEach { (prev, entry) ->
                            if (prev != entry.new) {
                                addProperty("$prev.class", "${entry.new}.class")
                            }
                        }
                    })
                })
            }
            GsonBuilder().setPrettyPrinting().create().toJson(jsonObj, it)
        }
    }

    data class DumpContext(
        val input: String,
        val output: String,
        val profiler: Boolean,
        val multithreading: Boolean,
        val steps: List<String>
    )

    fun putIndyMapping(name: String, descriptor: String, newName: String) {
        indyMapping["$name$descriptor"] = newName
    }

    fun putServiceFileMapping(oldPath: String, newPath: String) {
        if (oldPath != newPath) serviceFileMappings[oldPath] = newPath
    }

    fun putServiceImplementationMapping(oldName: String, newName: String) {
        if (oldName != newName) serviceImplementationMappings[oldName] = newName
    }

    fun putManifestMapping(attribute: String, oldValue: String, newValue: String) {
        if (oldValue == newValue) return
        manifestMappings.computeIfAbsent(attribute) { ConcurrentHashMap() }[oldValue] = newValue
    }

    fun putClassMapping(prev: String, new: String) {
        classMappings.computeIfAbsent(prev) { ClassEntry(prev) }.new = new
        reverseClassMappings[new] = prev
    }

    fun getOriginalClassName(current: String): String? {
        return reverseClassMappings[current]
    }

    fun putMethodMapping(owner: String, name: String, descriptor: String, newName: String) {
        classMappings.computeIfAbsent(owner) { ClassEntry(owner) }.methodMapping["$name$descriptor"] = newName
    }

    fun putFieldMapping(owner: String, name: String, descriptor: String, newName: String) {
        classMappings.computeIfAbsent(owner) { ClassEntry(owner) }.fieldMapping["$name$descriptor"] = newName
    }

    fun mapReflectiveMethodName(owner: String, oldName: String): String? {
        return mapReflectiveMethodName(sequenceOf(owner), oldName)
    }

    fun mapReflectiveMethodName(owners: Sequence<String>, oldName: String): String? {
        val resolved = linkedSetOf<String>()
        owners.forEach { owner ->
            val entry = classMappings[owner] ?: return@forEach
            entry.methodMapping.entries
                .asSequence()
                .filter { it.key.startsWith(oldName) }
                .mapTo(resolved) { it.value }
        }
        return resolved.singleOrNull()
    }

    fun mapReflectiveFieldName(owner: String, oldName: String): String? {
        return mapReflectiveFieldName(sequenceOf(owner), oldName)
    }

    fun mapReflectiveFieldName(owners: Sequence<String>, oldName: String): String? {
        val resolved = linkedSetOf<String>()
        owners.forEach { owner ->
            val entry = classMappings[owner] ?: return@forEach
            entry.fieldMapping.entries
                .asSequence()
                .filter { it.key.startsWith(oldName) }
                .mapTo(resolved) { it.value }
        }
        return resolved.singleOrNull()
    }

    class ClassEntry(val prev: String) {
        var new = prev
        val methodMapping: Object2ObjectMap<String, String> = Object2ObjectMaps.synchronize(Object2ObjectOpenHashMap())
        val fieldMapping: Object2ObjectMap<String, String> = Object2ObjectMaps.synchronize(Object2ObjectOpenHashMap())
    }

    override fun mapMethodName(owner: String?, name: String?, descriptor: String?): String? {
        if (owner == null || name == null || descriptor == null) return name
        return classMappings[owner]?.methodMapping?.get("$name$descriptor") ?: name
    }

    @Deprecated("Deprecated in Java")
    override fun mapInvokeDynamicMethodName(name: String?, descriptor: String?): String? {
        if (name == null || descriptor == null) return name
        return indyMapping["$name$descriptor"] ?: name
    }

    override fun mapBasicInvokeDynamicMethodName(
        name: String?,
        descriptor: String?,
        bootstrapMethodHandle: Handle?,
        vararg bootstrapMethodArguments: Any?
    ): String? {
        if (name == null || descriptor == null) return name
        return indyMapping["$name$descriptor"] ?: name
    }

    override fun mapFieldName(owner: String, name: String, descriptor: String): String {
        return classMappings[owner]?.fieldMapping?.get("$name$descriptor") ?: name
    }

    override fun map(key: String?): String? {
        if (key == null) return null
        return classMappings[key]?.new ?: key
    }

}
