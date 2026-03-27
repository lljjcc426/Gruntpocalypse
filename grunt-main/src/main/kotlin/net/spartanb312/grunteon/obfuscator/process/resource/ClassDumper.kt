package net.spartanb312.grunteon.obfuscator.process.resource

import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.util.Logger
import net.spartanb312.grunteon.obfuscator.util.extensions.isInterface
import org.objectweb.asm.ClassWriter

class ClassDumper(
    private val instance: Grunteon,
    private val hierarchy: ClassHierarchy,
    useComputeMax: Boolean = false
) : ClassWriter(if (useComputeMax) COMPUTE_MAXS else COMPUTE_FRAMES) {

    override fun getCommonSuperClass(type1: String, type2: String): String {
        return when {
            type1 == "java/lang/Object" -> type1
            type2 == "java/lang/Object" -> type2
            hierarchy.isSubType(type1, type2) -> type2
            hierarchy.isSubType(type2, type1) -> type1
            else -> {
                val clazz1 = instance.workRes.getClassNode(type1)
                val clazz2 = instance.workRes.getClassNode(type2)
                if (clazz1?.isInterface == true && clazz2?.isInterface == true) return "java/lang/Object"
                try {
                    super.getCommonSuperClass(type1, type2)
                } catch (_: Exception) {
                    try {
                        Class.forName(type1)
                    } catch (_: Exception) {
                        if (clazz1 == null) {
                            Logger.error("Missing dependency $type1")
                            throw Exception("Can't find common super class due to missing $type1")
                        }
                    }
                    try {
                        Class.forName(type2)
                    } catch (_: Exception) {
                        if (clazz2 == null) {
                            Logger.error("Missing dependency $type2")
                            throw Exception("Can't find common super class due to missing $type2")
                        }
                    }
                    "java/lang/Object"
                }
            }
        }
    }

}