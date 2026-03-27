package net.spartanb312.grunteon.obfuscator.util

import net.spartanb312.grunteon.obfuscator.process.Mergeable
import java.util.concurrent.atomic.AtomicInteger

class Counter {
    private val count = AtomicInteger(0)
    fun add(num: Int = 1) = count.getAndAdd(num)
    fun get() = count.get()
}

fun count(block: Counter.() -> Unit): Counter = Counter().apply(block)

class FastCounter : Mergeable<FastCounter> {
    private var count: Int = 0
    fun add(num: Int = 1) {
        count += num
    }

    fun get() = count

    override fun merge(other: FastCounter) {
        this.count += other.count
    }
}