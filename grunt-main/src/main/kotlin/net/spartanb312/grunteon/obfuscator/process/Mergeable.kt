package net.spartanb312.grunteon.obfuscator.process

interface Mergeable {
    /**
     * Merge other's data into this
     */
    fun merge(other: Mergeable): Mergeable
}