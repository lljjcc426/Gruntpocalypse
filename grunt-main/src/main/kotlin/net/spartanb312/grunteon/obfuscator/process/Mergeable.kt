package net.spartanb312.grunteon.obfuscator.process

interface Mergeable<T : Mergeable<T>> {
    /**
     * Merge other's data into this
     */
    fun merge(other: T)
}

fun <T : Mergeable<T>> Mergeable<T>.merge(other: Mergeable<*>) {
    @Suppress("UNCHECKED_CAST")
    other as T
    require(other.javaClass == this.javaClass) { "Cannot merge different types! ${this.javaClass} vs ${other.javaClass}" }
    this.merge(other)
}