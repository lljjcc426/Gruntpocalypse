package net.spartanb312.grunteon.obfuscator.process.resource

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.grunteon.obfuscator.util.Logger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.*

class WorkResources private constructor(
    val inputResourceSet: ResourceSet.Single,
    val libraryResourceSets: Map<String, ResourceSet.Single>,
    val allResourceSets: ResourceSet,
    /**
     * Input classes in library input set, maps name to class node.
     * Also included in allClasses.
     */
    val libraryClassMap: MutableMap<String, ClassNode>,
    /**
     * Input classes in input/output set, maps name to class node.
     * Also included in allClasses.
     */
    val inputClassMap: MutableMap<String, ClassNode>,
    /**
     * Generated classes in input/output set, maps name to class node in input.
     * Also included in allClasses.
     */
    val generatedClassMap: MutableMap<String, ClassNode> // also included in classes
) {
    val inputClassCollection: Collection<ClassNode> get() = inputClassMap.values
    val generatedClassCollection: Collection<ClassNode> get() = generatedClassMap.values

    // TODO: optimize this
    inline val allClassCollection
        get() = ObjectArrayList<ClassNode>(inputClassCollection.size + generatedClassCollection.size).apply {
            addAll(inputClassCollection)
            addAll(generatedClassCollection)
        }


    fun readInRuntime(name: String): ClassNode? {
        return try {
            val classNode = ClassNode()
            ClassReader(name).apply {
                accept(classNode, ClassReader.EXPAND_FRAMES)
                libraryClassMap[classNode.name] = classNode
            }
            classNode
        } catch (_: Exception) {
            null
        }
    }

    fun getClassNode(name: String): ClassNode? {
        return inputClassMap[name] ?: libraryClassMap[name] ?: readInRuntime(name)
    }

    companion object {
        private fun toZipRootPath(zipPath: Path): Path {
            val jarURI = URI.create("jar:" + zipPath.toUri())
            // TODO: lifecycle of zipFileSystem
            val zipFileSystem = FileSystems.newFileSystem(jarURI, mapOf<String, String>())
            val zipRoot = zipFileSystem.getPath("/")
            return zipRoot
        }

        private fun resolvePath(path: Path): Path {
            if (path.isRegularFile()) {
                val ext = path.extension.lowercase()
                if (ext == "jar" || ext == "zip") {
                    return toZipRootPath(path)
                }
            }
            return path
        }

        fun read(input: Path, libs: List<Path> = emptyList()): WorkResources {
            Logger.info("Reading...")
            Logger.info("Input: ${input.absolutePathString()}")
            Logger.info("Libraries:")
            libs.forEach {
                Logger.info(" - ${it.absolutePathString()}")
            }
            val inputResourceSet = ResourceSet.Single(resolvePath(input))
            val libraryResourceSets = libs.associate { it.pathString to ResourceSet.Single(resolvePath(it)) }
            val allResourceSetList = listOf(inputResourceSet) + libraryResourceSets.values
            val allResourceSets = ResourceSet.Composite(allResourceSetList)

            val inputClassMap = Object2ObjectOpenHashMap<String, ClassNode>()
            val libraryClassMap = Object2ObjectOpenHashMap<String, ClassNode>()
            allResourceSetList.parallelStream()
                .flatMap { resourceSet ->
                    val isInput = resourceSet == inputResourceSet
                    resourceSet.root.walk()
                        .filter { it.extension == "class" }
                        .map { resourceSet[it].first() }
                        .map { isInput to it }
                        .toList()
                        .parallelStream()
                        .map {
                            runCatching {
                                it.first to ClassNode().apply {
                                    ClassReader(it.second.content)
                                        .accept(this, ClassReader.EXPAND_FRAMES)
                                }
                            }.getOrNull()
                        }
                        .filter { it != null }
                        .toList()
                        .stream()
                }
                .sequential()
                .forEach {
                    it!!
                    if (it.first) {
                        inputClassMap[it.second.name] = it.second
                    } else {
                        libraryClassMap[it.second.name] = it.second
                    }
                }

            return WorkResources(
                inputResourceSet = inputResourceSet,
                libraryResourceSets = libraryResourceSets,
                allResourceSets = allResourceSets,
                libraryClassMap = libraryClassMap,
                inputClassMap = inputClassMap,
                generatedClassMap = Object2ObjectOpenHashMap()
            )
        }
    }
}