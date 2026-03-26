package net.spartanb312.grunteon.obfuscator.util.extensions

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import org.objectweb.asm.tree.ClassNode

context(_: Grunteon)
inline val String.isExcluded get() = isMixinClass || isGlobalExcluded

context(_: Grunteon)
inline val ClassNode.isExcluded get() = isMixinClass || isGlobalExcluded

context(instance: Grunteon)
inline val String.isMixinClass get() = instance.mixinExPredicate.matchedAnyBy(this)

context(instance: Grunteon)
inline val ClassNode.isMixinClass get() = instance.mixinExPredicate.matchedAnyBy(this.name)

context(instance: Grunteon)
inline val String.isGlobalExcluded get() = instance.globalExPredicate.matchedAnyBy(this)

context(instance: Grunteon)
inline val ClassNode.isGlobalExcluded get() = instance.globalExPredicate.matchedAnyBy(this.name)
