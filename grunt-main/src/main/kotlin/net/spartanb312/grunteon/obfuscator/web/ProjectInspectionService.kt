package net.spartanb312.grunteon.obfuscator.web

class ProjectInspectionService {

    fun projectMeta(session: ObfuscationSession, scope: ObfuscationSession.ProjectScope): ProjectMeta {
        val classes = session.getProjectClasses(scope)
        return ProjectMeta(
            scope = scope.name.lowercase(),
            available = classes != null,
            classCount = classes?.size ?: 0
        )
    }

    fun projectTree(session: ObfuscationSession, scope: ObfuscationSession.ProjectScope): ProjectTree {
        val classes = session.getProjectClasses(scope)
            ?: throw IllegalStateException("No ${scope.name.lowercase()} class structure available")
        return ProjectTree(
            scope = scope.name.lowercase(),
            classes = classes
        )
    }

    fun projectSource(
        session: ObfuscationSession,
        scope: ObfuscationSession.ProjectScope,
        className: String
    ): ProjectSource {
        val code = session.decompileClass(scope, className)
        return ProjectSource(
            scope = scope.name.lowercase(),
            className = className,
            language = "java",
            code = code
        )
    }
}

data class ProjectMeta(
    val scope: String,
    val available: Boolean,
    val classCount: Int
)

data class ProjectTree(
    val scope: String,
    val classes: List<String>
) {
    val classCount: Int
        get() = classes.size
}

data class ProjectSource(
    val scope: String,
    val className: String,
    val language: String,
    val code: String
)
