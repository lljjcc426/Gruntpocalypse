package net.spartanb312.grunteon.obfuscator.web

enum class SessionAccessProfile {
    SECURE,
    RESEARCH;

    val allowProjectPreview: Boolean
        get() = this == RESEARCH

    val allowSourcePreview: Boolean
        get() = this == RESEARCH

    val allowDetailedLogs: Boolean
        get() = this == RESEARCH

    companion object {
        @JvmStatic
        fun parseOrNull(raw: String?): SessionAccessProfile? {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
        }
    }
}
