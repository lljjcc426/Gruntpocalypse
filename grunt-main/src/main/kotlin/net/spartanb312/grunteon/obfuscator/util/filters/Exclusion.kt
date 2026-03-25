package net.spartanb312.grunteon.obfuscator.util.filters

// class
typealias NamePredicate = (String) -> Boolean
typealias NamePredicates = List<NamePredicate>

fun buildClassNamePredicate(rule: String): NamePredicate {
    if (rule.endsWith("**")) return { it.startsWith(rule) } // package exclude/include
    else return { it == rule } // name exclude/include
}

fun buildClassNamePredicates(rules: List<String>): NamePredicates {
    return rules.map { buildClassNamePredicate(it) }
}

fun NamePredicate.matchedBy(name: String): Boolean = invoke(name)

fun NamePredicates.matchedAllBy(name: String): Boolean = all { it.invoke(name) }

fun NamePredicates.matchedAnyBy(name: String): Boolean = any { it.invoke(name) }


