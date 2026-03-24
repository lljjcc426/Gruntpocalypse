package net.spartanb312.grunteon.obfuscator.process.resource

class WorkResources(
    val inputJar: JarResources
) {
    val libraries = mutableListOf<JarResources>()
}