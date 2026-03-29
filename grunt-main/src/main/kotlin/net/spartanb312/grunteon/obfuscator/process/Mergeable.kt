package net.spartanb312.grunteon.obfuscator.process

import it.unimi.dsi.fastutil.objects.Object2ObjectMap
import it.unimi.dsi.fastutil.objects.ObjectList

interface Mergeable<T : Mergeable<T>> {
    /**
     * Merge other's data into this
     */
    fun merge(other: T)
}

fun <T : Mergeable<T>> Mergeable<T>.merge(other: Mergeable<*>) {
    require(other.javaClass == this.javaClass) {
        "Cannot merge different types! ${this.javaClass} vs ${other.javaClass}"
    }
    @Suppress("UNCHECKED_CAST")
    val typedOther = other as T
    this.merge(typedOther)
}

class MergeableObject2ObjectMap<K, V>(private val delegate: Object2ObjectMap<K, V>) :
    Object2ObjectMap<K, V> by delegate, Mergeable<MergeableObject2ObjectMap<K, V>> {
    override fun clear() {
        delegate.clear()
    }

    override fun merge(other: MergeableObject2ObjectMap<K, V>) {
        delegate.putAll(other.delegate)
    }
}

@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
class MergeableObjectList<E>(private val delegate: ObjectList<E>) : ObjectList<E> by delegate,
    Mergeable<MergeableObjectList<E>> {
    override fun merge(other: MergeableObjectList<E>) {
        delegate.addAll(other.delegate)
    }
}