package net.spartanb312.grunteon.obfuscator.process.resource

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import net.spartanb312.grunteon.obfuscator.util.Logger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.net.URI
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.FileSystem
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import kotlin.collections.AbstractCollection
import kotlin.io.path.*

class WorkResources private constructor(
    val inputResourceSet: ResourceSet.Single,
    val libraryResourceSets: Map<String, ResourceSet.Single>,
    val allResourceSets: ResourceSet,
    /**
     * Input classes in library input set, maps name to class node.
     * Also included in allClasses.
     */
    val libraryClassMap: ConcurrentHashMap<String, ClassNode>,
    /**
     * Input classes in input/output set, maps name to class node.
     * Also included in allClasses.
     */
    val inputClassMap: MutableMap<String, ClassNode>
) {
    val inputClassCollection: Collection<ClassNode> get() = inputClassMap.values
    val librariesClassCollection: Collection<ClassNode> get() = libraryClassMap.values
    val allClassCollection: Collection<ClassNode> = CombinedClassCollection(inputClassMap, libraryClassMap)

    fun addGeneratedClass(classNode: ClassNode) {
        inputClassMap[classNode.name] = classNode
    }

    fun getInputResource(name: String): ResourceSet.ResourceEntry? {
        return inputResourceSet[name].firstOrNull()
    }

    fun getClassNode(name: String): ClassNode? {
        return inputClassMap[name] ?: libraryClassMap.computeIfAbsent(name) {
            try {
                ClassNode().apply {
                    ClassReader(name)
                        .accept(this, ClassReader.EXPAND_FRAMES)
                }
            } catch (_: Exception) {
                DUMMY_CLASSNODE
            }
        }.takeIf { it !== DUMMY_CLASSNODE }
    }

    companion object {
        private val DUMMY_CLASSNODE = ClassNode()
        private val ZIP_FILE_SYSTEMS = ConcurrentHashMap<String, FileSystem>()

        private fun toZipRootPath(zipPath: Path): Path {
            val jarURI = URI.create("jar:" + zipPath.toUri())
            val zipFileSystem = ZIP_FILE_SYSTEMS.computeIfAbsent(jarURI.toString()) {
                try {
                    FileSystems.getFileSystem(jarURI)
                } catch (_: FileSystemNotFoundException) {
                    FileSystems.newFileSystem(jarURI, mapOf<String, String>())
                }
            }
            return zipFileSystem.getPath("/")
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

        @OptIn(ExperimentalCoroutinesApi::class)
        fun read(input: Path, libs: List<Path> = emptyList()): WorkResources {
            Logger.info("Reading...")
            Logger.info("Input: ${input.absolutePathString()}")
            require(input.exists()) { "Input file does not exist: ${input.absolutePathString()}" }
            Logger.debug("Libraries:")
            libs.forEach {
                Logger.debug(" - ${it.absolutePathString()}")
            }
            val inputResourceSet = ResourceSet.Single(resolvePath(input))
            val libraryResourceSets = libs.associate { it.pathString to ResourceSet.Single(resolvePath(it)) }
            val allResourceSetList = listOf(inputResourceSet) + libraryResourceSets.values
            val allResourceSets = ResourceSet.Composite(allResourceSetList)

            val inputClassMap = Object2ObjectOpenHashMap<String, ClassNode>()
            // TODO: resolves all classes reference during read
            val libraryClassMap = ConcurrentHashMap<String, ClassNode>()
            runBlocking {
                val inputClassNodes = Channel<ClassNode>(Channel.BUFFERED)
                val libraryNodes = Channel<ClassNode>(Channel.BUFFERED)

                launch(Dispatchers.Default) {
                    coroutineScope {
                        allResourceSetList.forEach { resourceSet ->
                            readSourceSetClassNodes(resourceSet, inputResourceSet, inputClassNodes, libraryNodes)
                        }
                    }
                    inputClassNodes.close()
                    libraryNodes.close()
                }

                launch {
                    inputClassNodes.consumeEach {
                        inputClassMap[it.name] = it
                    }
                }
                launch {
                    libraryNodes.consumeEach {
                        libraryClassMap[it.name] = it
                    }
                }
            }
            Logger.info("Read ${inputClassMap.size} classes from input and ${libraryClassMap.size} classes from libraries")

            return WorkResources(
                inputResourceSet = inputResourceSet,
                libraryResourceSets = libraryResourceSets,
                allResourceSets = allResourceSets,
                libraryClassMap = libraryClassMap,
                inputClassMap = inputClassMap
            )
        }

        private class CombinedClassCollection(
            private val input: MutableMap<String, ClassNode>,
            private val libraries: Map<String, ClassNode>
        ) : AbstractCollection<ClassNode>() {
            override val size: Int
                get() = input.size + libraries.size

            override fun iterator(): Iterator<ClassNode> {
                val inputIterator = input.values.iterator()
                val libraryIterator = libraries.values.iterator()
                return object : Iterator<ClassNode> {
                    override fun hasNext(): Boolean = inputIterator.hasNext() || libraryIterator.hasNext()

                    override fun next(): ClassNode {
                        return if (inputIterator.hasNext()) inputIterator.next() else libraryIterator.next()
                    }
                }
            }
        }

        private fun CoroutineScope.readSourceSetClassNodes(
            resourceSet: ResourceSet.Single,
            inputResourceSet: ResourceSet.Single,
            inputClassNodes: Channel<ClassNode>,
            libraryNodes: Channel<ClassNode>
        ) {
            launch {
                var uriStr = resourceSet.root.toUri().toString()
                if (uriStr.startsWith("jar:")) {
                    uriStr = uriStr.substring(4, uriStr.length - 2)
                    val entries =
                        resourceSet.root.walk()
                            .filter { !it.isDirectory() }
                            .filter { it.extension == "class" }
                            .filter { !it.absolutePathString().startsWith("/META-INF/") }
                            .toList()
                    ZipFile(URI.create(uriStr).toPath().toFile()).use { zip ->
                        coroutineScope {
                            val isInput = resourceSet === inputResourceSet
                            entries.forEach { entry ->
                                launch {
                                    runCatching {
                                        ClassNode().apply {
                                            val entryPath = entry.pathString.removePrefix("/")
                                            val data =
                                                zip.getInputStream(zip.getEntry(entryPath))
                                                    .use { it.readBytes() }
                                            ClassReader(data)
                                                .accept(this, ClassReader.EXPAND_FRAMES)
                                        }
                                    }.onSuccess {
                                        if (isInput) {
                                            inputClassNodes.send(it)
                                        } else {
                                            libraryNodes.send(it)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val entries = withContext(Dispatchers.IO) {
                        resourceSet.root.walk()
                            .filter { it.extension == "class" }
                            .map { resourceSet[it].first() }
                            .toList()
                    }
                    val isInput = resourceSet === inputResourceSet
                    entries.forEach { entry ->
                        launch {
                            runCatching {
                                ClassNode().apply {
                                    ClassReader(entry.content)
                                        .accept(this, ClassReader.EXPAND_FRAMES)
                                }
                            }.onSuccess {
                                if (isInput) {
                                    inputClassNodes.send(it)
                                } else {
                                    libraryNodes.send(it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
