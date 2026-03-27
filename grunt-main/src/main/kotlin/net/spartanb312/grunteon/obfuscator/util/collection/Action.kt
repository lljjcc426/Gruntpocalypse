package net.spartanb312.grunteon.obfuscator.util.collection

import org.apache.commons.math3.random.RandomGenerator
import java.util.*

const val SHUFFLE_THRESHOLD = 5

inline fun <reified T> Iterable<T>.shuffled(randomGen: RandomGenerator): List<T> =
    toMutableList().apply { shuffle(randomGen) }

inline fun <reified T> MutableList<T>.shuffle(rnd: RandomGenerator) {
    val size: Int = size
    if (size < SHUFFLE_THRESHOLD || this is RandomAccess) {
        for (i in size downTo 2) Collections.swap(this, i - 1, rnd.nextInt(i))
    } else {
        val arr = this.toTypedArray()
        for (i in size downTo 2) swap(arr, i - 1, rnd.nextInt(i))
        val it: MutableListIterator<T> = this.listIterator()
        for (e in arr) {
            it.next()
            it.set(e)
        }
    }
}

fun <T> swap(arr: Array<T>, i: Int, j: Int) {
    val tmp = arr[i]
    arr[i] = arr[j]
    arr[j] = tmp
}

fun <T> Collection<T>.random(randomGen: RandomGenerator): T {
    if (isEmpty()) throw NoSuchElementException("Collection is empty.")
    return elementAt(randomGen.nextInt(size))
}
