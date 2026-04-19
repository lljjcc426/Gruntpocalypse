package net.spartanb312.grunteon.obfuscator.web

import org.benf.cfr.reader.api.CfrDriver
import org.benf.cfr.reader.api.ClassFileSource
import org.benf.cfr.reader.api.OutputSinkFactory
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair

object Decompiler {

    fun decompile(
        className: String,
        classBytes: ByteArray,
        allClasses: Map<String, ByteArray> = emptyMap()
    ): String {
        val result = StringBuilder()

        val sinkFactory = object : OutputSinkFactory {
            override fun getSupportedSinks(
                sinkType: OutputSinkFactory.SinkType,
                available: Collection<OutputSinkFactory.SinkClass>
            ): List<OutputSinkFactory.SinkClass> {
                return listOf(OutputSinkFactory.SinkClass.STRING)
            }

            override fun <T : Any?> getSink(
                sinkType: OutputSinkFactory.SinkType,
                sinkClass: OutputSinkFactory.SinkClass
            ): OutputSinkFactory.Sink<T> {
                @Suppress("UNCHECKED_CAST")
                return when (sinkType) {
                    OutputSinkFactory.SinkType.JAVA -> OutputSinkFactory.Sink<T> { content ->
                        result.append(content.toString())
                    }

                    OutputSinkFactory.SinkType.EXCEPTION -> OutputSinkFactory.Sink<T> { content ->
                        result.append("// Decompilation error: $content\n")
                    }

                    else -> OutputSinkFactory.Sink<T> { }
                }
            }
        }

        val classFileSource = object : ClassFileSource {
            override fun informAnalysisRelativePathDetail(usePath: String?, classFilePath: String?) {
            }

            override fun getPossiblyRenamedPath(path: String): String = path

            override fun addJar(jarPath: String): Collection<String> = emptyList()

            override fun getClassFileContent(path: String): Pair<ByteArray, String> {
                val lookupName = path.removeSuffix(".class")
                val bytes = if (lookupName == className) {
                    classBytes
                } else {
                    allClasses[lookupName]
                        ?: allClasses[lookupName.replace("/", ".")]
                        ?: ClassLoader.getSystemResourceAsStream("$lookupName.class")?.use { it.readBytes() }
                }
                return Pair(bytes ?: ByteArray(0), lookupName)
            }
        }

        return try {
            val driver = CfrDriver.Builder()
                .withClassFileSource(classFileSource)
                .withOutputSink(sinkFactory)
                .withOptions(
                    mapOf(
                        "showversion" to "false",
                        "hideutf" to "false",
                        "decodelambdas" to "true",
                        "removeboilerplate" to "true",
                        "removeinnerclasssynthetics" to "true",
                        "recovertypeclash" to "true",
                        "recovertypehints" to "true",
                        "forcereturningifs" to "true",
                        "silent" to "true"
                    )
                )
                .build()

            driver.analyse(listOf("$className.class"))
            result.toString()
        } catch (exception: Exception) {
            buildString {
                appendLine("// Failed to decompile: ${exception.message}")
                appendLine("// Class: ${className.replace("/", ".")}")
            }
        }
    }
}
