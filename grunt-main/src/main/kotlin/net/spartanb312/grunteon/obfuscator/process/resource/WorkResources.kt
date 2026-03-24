package net.spartanb312.grunteon.obfuscator.process.resource

import net.spartanb312.grunteon.obfuscator.util.Logger
import java.io.File

class WorkResources(
    val inputJar: JarResources
) {

    val libraries = mutableListOf<JarResources>()

    fun readLibs(libs: List<String>) {
        Logger.info("Reading Libraries...")
        libs.map { File(it) }.forEach { file ->
            if (file.isDirectory) {
                readDirectory(file)
            } else {
                val jar = JarResources(file)
                jar.readInput()
                libraries.add(jar)
            }
        }
    }

    private fun readDirectory(directory: File) {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                readDirectory(file)
            } else {
                val jar = JarResources(file)
                jar.readInput()
                libraries.add(jar)
            }
        }
    }

}