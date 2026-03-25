package net.spartanb312.grunteon.obfuscator.util.extensions

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.util.filters.matchedAnyBy
import org.objectweb.asm.tree.ClassNode

context(_: Grunteon)
inline val String.isExcluded get() = isMixinClass || isGlobalExcluded

context(_: Grunteon)
inline val ClassNode.isExcluded get() = isMixinClass || isGlobalExcluded

context(_: Grunteon)
inline val String.isMixinClass get() = contextOf<Grunteon>().mixinExPredicate.matchedAnyBy(this)

context(_: Grunteon)
inline val ClassNode.isMixinClass get() = contextOf<Grunteon>().mixinExPredicate.matchedAnyBy(this.name)

context(_: Grunteon)
inline val String.isGlobalExcluded get() = contextOf<Grunteon>().globalExPredicate.matchedAnyBy(this)

context(_: Grunteon)
inline val ClassNode.isGlobalExcluded get() = contextOf<Grunteon>().globalExPredicate.matchedAnyBy(this.name)
