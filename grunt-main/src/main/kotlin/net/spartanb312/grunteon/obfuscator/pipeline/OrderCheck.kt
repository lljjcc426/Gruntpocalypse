package net.spartanb312.grunteon.obfuscator.pipeline

import net.spartanb312.grunteon.obfuscator.process.Category
import net.spartanb312.grunteon.obfuscator.process.Transformer

typealias OrderRule = (List<Transformer<*>>, Int) -> Boolean // transformers, index

fun <T : Transformer<*>> Transformer<*>.after(
    transformerClass: Class<T>,
    message: String
): Transformer<*> = apply {
    val check = afterTransformer(transformerClass)
    orderRules.add(check to message)
}

fun Transformer<*>.after(
    category: Category,
    message: String
): Transformer<*> = apply {
    val check = afterCategory(category)
    orderRules.add(check to message)
}

fun <T : Transformer<*>> Transformer<*>.before(
    transformerClass: Class<T>,
    message: String
): Transformer<*> = apply {
    val check = beforeTransformer(transformerClass)
    orderRules.add(check to message)
}

fun Transformer<*>.before(
    category: Category,
    message: String
): Transformer<*> = apply {
    val check = beforeCategory(category)
    orderRules.add(check to message)
}

fun <T : Transformer<*>> Transformer<*>.between(
    fromTransformer: Class<T>,
    toTransformer: Class<T>,
    message: String
): Transformer<*> = apply {
    val check = betweenTransformer(fromTransformer, toTransformer)
    orderRules.add(check to message)
}

fun afterCategory(category: Category): OrderRule {
    val rule: OrderRule = outer@{ transformers: List<Transformer<*>>, currentIndex: Int ->
        transformers.forEachIndexed { index, transformer ->
            if (transformer.category == category) {
                if (index > currentIndex) return@outer false
            }
        }
        return@outer true
    }
    return rule
}

fun beforeCategory(category: Category): OrderRule {
    val rule: OrderRule = outer@{ transformers: List<Transformer<*>>, currentIndex: Int ->
        transformers.forEachIndexed { index, transformer ->
            if (transformer.category == category) {
                if (index < currentIndex) return@outer false
            }
        }
        return@outer true
    }
    return rule
}

fun <T : Transformer<*>> betweenTransformer(
    fromTransformer: Class<T>,
    toTransformer: Class<T>,
): OrderRule {
    val before = beforeTransformer(toTransformer)
    val after = afterTransformer(fromTransformer)
    val rule: OrderRule = outer@{ transformers: List<Transformer<*>>, currentIndex: Int ->
        return@outer before.invoke(transformers, currentIndex) && after.invoke(transformers, currentIndex)
    }
    return rule
}

fun <T : Transformer<*>> afterTransformer(transformerClass: Class<T>): OrderRule {
    val rule: OrderRule = outer@{ transformers: List<Transformer<*>>, currentIndex: Int ->
        transformers.forEachIndexed { index, transformer ->
            if (transformer::class.java.isAssignableFrom(transformerClass)) {
                if (index > currentIndex) return@outer false
            }
        }
        return@outer true
    }
    return rule
}

fun <T : Transformer<*>> beforeTransformer(transformerClass: Class<T>): OrderRule {
    val rule: OrderRule = outer@{ transformers: List<Transformer<*>>, currentIndex: Int ->
        transformers.forEachIndexed { index, transformer ->
            if (transformer::class.java.isAssignableFrom(transformerClass)) {
                if (index < currentIndex) return@outer false
            }
        }
        return@outer true
    }
    return rule
}