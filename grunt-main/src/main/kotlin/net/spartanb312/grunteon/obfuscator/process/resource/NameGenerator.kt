package net.spartanb312.grunteon.obfuscator.process.resource

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.interfaces.DisplayEnum
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

sealed class NameGenerator(val name: String) {

    abstract val elements: List<String>
    private val size get() = elements.size
    private val index = AtomicInteger(0/*dictionaryStartIndex*/)
    private val methodOverloads = hashMapOf<String, MutableList<String>>() // Name Descs

    var overloadsCount = 0; private set
    var actualNameCount = 0; private set

    fun nextName(): String {
        var index = index.getAndIncrement()
        return if (index == 0) elements[0]
        else {
            val charArray = mutableListOf<String>()
            while (true) {
                charArray.add(elements[index % size])
                index /= size
                if (index == 0) break
                index -= 1
            }
            charArray.reversed().joinToString(separator = "")
        }
    }

    @Synchronized
    fun nextName(overload: Boolean, desc: String): String {
        if (!overload) return nextName()
        else {
            //nameCache[desc]?.let { return it }
            for (pair in methodOverloads) {
                if (!pair.value.contains(desc)) {
                    pair.value.add(desc)
                    overloadsCount++
                    return pair.key
                }
            }
            // Generate a new one
            val newName = nextName()
            methodOverloads[newName] = mutableListOf(desc)
            actualNameCount++
            return newName
        }
    }

    class Alphabet : NameGenerator("Alphabet") {
        override val elements = (('a'..'z') + ('A'..'Z')).map { it.toString() }
    }

    class Numbers : NameGenerator("Numbers") {
        override val elements = ('0'..'9').map { it.toString() }
    }

    class ConfuseIL : NameGenerator("ConfuseIL") {
        override val elements = listOf("I", "i", "l", "1")
    }

    class Confuse0O : NameGenerator("Confuse0O") {
        override val elements = listOf("O", "o", "0")
    }

    class ConfuseS5 : NameGenerator("ConfuseS5") {
        override val elements = listOf("S", "s", "5", "$")
    }

    class Arabic : NameGenerator("Arabic") {
        override val elements = ('\u0600'..'\u06ff').asSequence().map { it.toString() }.toList()
    }

    class CustomIncr(instance: Grunteon) : NameGenerator("CustomIncr") {
        override val elements = instance.configGroup.customIncrementalDictionary
    }

    class Custom(instance: Grunteon) : NameGenerator("Custom") {
        override val elements: List<String> = run {
            val file = File(instance.configGroup.customDictionary)
            if (!file.exists()) {
                // Dictionary file does not exist, use default dictionary
                Logger.error("Could not find custom dictionary ${file.name}")
                Logger.error("Using default fallback dictionary!")
                return@run Alphabet().elements
            }
            Files.readAllLines(file.toPath())
        }
    }

    companion object {
        context(instance: Grunteon)
        fun getDictionary(dictionary: Dictionary): NameGenerator =
            when (dictionary) {
                Dictionary.Alphabet -> Alphabet()
                Dictionary.Numbers -> Numbers()
                Dictionary.ConfuseIL -> ConfuseIL()
                Dictionary.Confuse0O -> Confuse0O()
                Dictionary.ConfuseS5 -> ConfuseS5()
                Dictionary.Arabic -> Arabic()
                Dictionary.CustomIncrementable -> CustomIncr(instance)
                Dictionary.CustomDictionary -> Custom(instance)
            }
    }

    enum class Dictionary(override val displayName: CharSequence) : DisplayEnum {
        Alphabet("Alphabet"),
        Numbers("Numbers"),
        ConfuseIL("ConfuseIL"),
        Confuse0O("Confuse0O"),
        ConfuseS5("ConfuseS5"),
        Arabic("Arabic"),
        CustomIncrementable("CustomIncrementable"),
        CustomDictionary("CustomDictionary")
    }

}
