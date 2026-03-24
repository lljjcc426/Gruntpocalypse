package net.spartanb312.grunteon.obfuscator.util.thread

import kotlinx.coroutines.CoroutineScope

object MainScope : CoroutineScope by newCoroutineScope(
    Runtime.getRuntime().availableProcessors(),
    "Coroutines"
)