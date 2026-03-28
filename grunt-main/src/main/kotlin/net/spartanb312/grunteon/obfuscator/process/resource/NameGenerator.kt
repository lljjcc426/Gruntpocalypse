package net.spartanb312.grunteon.obfuscator.process.resource

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.interfaces.DisplayEnum
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class NameGenerator(private val dictionary: Dictionary) {
    private val index = AtomicInteger(0/*dictionaryStartIndex*/)
    private val methodOverloads = hashMapOf<String, MutableList<String>>() // Name Descs

    var overloadsCount = 0; private set
    var actualNameCount = 0; private set

    fun nextName(): String {
        var index = index.getAndIncrement()
        return if (index == 0) dictionary.elements[0]
        else {
            val charArray = mutableListOf<String>()
            while (true) {
                charArray.add(dictionary.elements[index % dictionary.elements.size])
                index /= dictionary.elements.size
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

    companion object {
        context(instance: Grunteon)
        private fun alphabet() = Dictionary(
            "Alphabet",
            (('a'..'z') + ('A'..'Z')).map { it.toString() }
        )

        context(instance: Grunteon)
        private fun numbers() = Dictionary(
            "Numbers",
            ('0'..'9').map { it.toString() }
        )

        context(instance: Grunteon)
        private fun confuseIL() = Dictionary(
            "ConfuseIL",
            listOf("I", "i", "l", "1")
        )

        context(instance: Grunteon)
        private fun confuse0O() = Dictionary(
            "Confuse0O",
            listOf("O", "o", "0")
        )

        context(instance: Grunteon)
        private fun confuseS5() = Dictionary(
            "ConfuseS5",
            listOf("S", "s", "5", "$")
        )

        context(instance: Grunteon)
        private fun arabic() = Dictionary(
            "Arabic",
            ('\u0600'..'\u06ff').asSequence().map { it.toString() }.toList()
        )

        context(instance: Grunteon)
        private fun customIncr() = Dictionary(
            "CustomIncrementable",
            instance.configGroup.customIncrementalDictionary
        )

        context(instance: Grunteon)
        private fun custom() = Dictionary(
            "Custom",
            run {
                val file = File(instance.configGroup.customDictionary)
                if (!file.exists()) {
                    // Dictionary file does not exist, use default dictionary
                    Logger.error("Could not find custom dictionary ${file.name}")
                    Logger.error("Using default fallback dictionary!")
                    return@run alphabet().elements
                }
                Files.readAllLines(file.toPath())
            }
        )

        context(instance: Grunteon)
        fun getDictionary(dictionary: DictionaryType): Dictionary =
            when (dictionary) {
                DictionaryType.Alphabet -> alphabet()
                DictionaryType.Numbers -> numbers()
                DictionaryType.ConfuseIL -> confuseIL()
                DictionaryType.Confuse0O -> confuse0O()
                DictionaryType.ConfuseS5 -> confuseS5()
                DictionaryType.Arabic -> arabic()
                DictionaryType.CustomIncrementable -> customIncr()
                DictionaryType.CustomDictionary -> custom()
            }
    }

    enum class DictionaryType(override val displayName: CharSequence) : DisplayEnum {
        Alphabet("Alphabet"),
        Numbers("Numbers"),
        ConfuseIL("ConfuseIL"),
        Confuse0O("Confuse0O"),
        ConfuseS5("ConfuseS5"),
        Arabic("Arabic"),
        CustomIncrementable("CustomIncrementable"),
        CustomDictionary("CustomDictionary")
    }

    class Dictionary(val name: String, val elements: List<String>)
}
