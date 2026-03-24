package net.spartanb312.grunteon.obfuscator.util

import net.spartanb312.everett.bootstrap.LaunchClassLoader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object ResourceHelper {

    val paths = mutableListOf("")

    fun getResourceStream(path: String): InputStream? {
        val path1 = if (path.startsWith("/")) path.removePrefix("/") else path
        val inRoot = LaunchClassLoader::class.java.getResource("/$path1")
        if (inRoot != null) return inRoot.openStream()
        val inJar = LaunchClassLoader.INSTANCE.findResource(path1)
        if (inJar != null) return inJar.openStream()
        for (p in paths) {
            val name = p + path1
            val file = File(name)
            if (file.exists()) {
                return FileInputStream(file)
            }
        }
        return null
    }

    fun addPath(path: String): Boolean {
        val correctedPath = if (path.endsWith("/")) path else "$path/"
        return if (!paths.contains(correctedPath)) {
            paths.add(correctedPath)
            true
        } else false
    }

    fun removePath(path: String): Boolean {
        val correctedPath = if (path.endsWith("/")) path else "$path/"
        return if (paths.contains(correctedPath)) {
            paths.remove(correctedPath)
            true
        } else false
    }

}