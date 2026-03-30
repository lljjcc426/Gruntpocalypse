package net.spartanb312.grunteon.obfuscator.util.filters

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.util.extensions.isExcluded
import org.objectweb.asm.tree.ClassNode

class FilterStrategy(
    val includeStrategy: NamePredicates,
    val excludeStrategy: NamePredicates
) {
    context(_: Grunteon)
    fun testClass(classNode: ClassNode): Boolean {
        val include = includeStrategy.matchedAnyBy(classNode.name)
        val exclude = excludeStrategy.matchedAnyBy(classNode.name)
        val hardExclude = classNode.isExcluded
        return include && !exclude && !hardExclude
    }
}