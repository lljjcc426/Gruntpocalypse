package net.spartanb312.grunteon.obfuscator.util

import net.spartanb312.grunteon.obfuscator.process.Mergeable

class MergeableCounter : Mergeable<MergeableCounter> {
    private var count: Int = 0
    fun add(num: Int = 1) {
        count += num
    }

    fun get() = count

    override fun merge(other: MergeableCounter) {
        this.count += other.count
    }
}