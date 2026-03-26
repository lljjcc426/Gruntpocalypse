package net.spartanb312.grunteon.obfuscator.pipeline

import net.spartanb312.grunteon.obfuscator.process.Transformer

typealias OrderRule = (List<Transformer<*>>, Int) -> Boolean // transformers, index

fun <T : Transformer<*>> Transformer<*>.after(
    transformerClass: Class<T>,
    message: String
): Transformer<*> = apply {
    val check = afterTransformer(transformerClass)
    orderRules.add(check to message)
}

fun <T : Transformer<*>> Transformer<*>.before(
    transformerClass: Class<T>,
    message: String
): Transformer<*> = apply {
    val check = beforeTransformer(transformerClass)
    orderRules.add(check to message)
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