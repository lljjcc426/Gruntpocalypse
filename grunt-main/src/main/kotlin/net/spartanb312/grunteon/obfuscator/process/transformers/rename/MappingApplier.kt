package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.util.Logger
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode

class MappingApplier : Transformer<MappingApplier.Config>(
    name = enText("process.rename.mapping_applier", "MappingApplier"),
    category = Category.Renaming,
    description = enText(
        "process.rename.mapping_applier.desc",
        "Applying mappings"
    )
), MappingSource {
    // Dummy
    class Config : TransformerConfig

    context(instance: Grunteon, _: PipelineBuilder)
    override fun buildStageImpl(config: Config) {
        barrier()
        pre {
            Logger.info(" > MappingApplier: Applying mappings...")
        }
        val newClasses = reducibleScopeValue {
            MergeableObjectList(ObjectArrayList<ClassNode>())
        }
        parForEachClasses {
            val copy = ClassNode()
            val adapter = ClassRemapper(copy, instance.nameMapping)
            it.accept(adapter)
            remapReflectionLiterals(instance, copy)
            newClasses.local.add(copy)
        }
        seq {
            val instance = contextOf<Grunteon>()
            instance.workRes.inputClassMap.clear()
            newClasses.global.forEach { instance.workRes.inputClassMap[it.name] = it }
        }
    }

    private fun remapReflectionLiterals(instance: Grunteon, classNode: ClassNode) {
        classNode.methods.forEach { methodNode ->
            methodNode.instructions?.forEach { insnNode ->
                if (insnNode !is MethodInsnNode) return@forEach
                val pre = insnNode.previous
                if (pre !is LdcInsnNode || pre.cst !is String) return@forEach
                val literal = pre.cst as String
                when {
                    insnNode.owner == "java/lang/Class" && insnNode.name == "forName" -> {
                        val mapped = instance.nameMapping.getMapping(literal.replace('.', '/'))?.replace('/', '.')
                        if (mapped != null && mapped != literal) pre.cst = mapped
                    }

                    insnNode.name == "findClass" && insnNode.desc == "(Ljava/lang/String;)Ljava/lang/Class;" -> {
                        val mapped = instance.nameMapping.getMapping(literal.replace('.', '/'))?.replace('/', '.')
                        if (mapped != null && mapped != literal) pre.cst = mapped
                    }

                    insnNode.name == "getResource" && insnNode.desc == "(Ljava/lang/String;)Ljava/net/URL;" -> {
                        remapClassResourceLiteral(instance, pre)
                    }

                    insnNode.name == "getResourceAsStream" && insnNode.desc == "(Ljava/lang/String;)Ljava/io/InputStream;" -> {
                        remapClassResourceLiteral(instance, pre)
                    }
                }
            }
        }
    }

    private fun remapClassResourceLiteral(instance: Grunteon, ldc: LdcInsnNode) {
        val literal = ldc.cst as? String ?: return
        if (!literal.endsWith(".class", true)) return
        val hasLeadingSlash = literal.startsWith("/")
        val classPart = literal.removePrefix("/").dropLast(".class".length).replace('.', '/')
        val mapped = instance.nameMapping.getMapping(classPart) ?: return
        ldc.cst = (if (hasLeadingSlash) "/" else "") + mapped + ".class"
    }
}
