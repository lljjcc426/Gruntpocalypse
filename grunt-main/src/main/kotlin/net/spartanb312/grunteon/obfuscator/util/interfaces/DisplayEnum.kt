package net.spartanb312.grunteon.obfuscator.util.interfaces

interface DisplayEnum {
    val displayName: CharSequence
    val displayString: String
        get() = displayName.toString()

    val lowerCase: String
        get() = displayName.toString().lowercase()
}