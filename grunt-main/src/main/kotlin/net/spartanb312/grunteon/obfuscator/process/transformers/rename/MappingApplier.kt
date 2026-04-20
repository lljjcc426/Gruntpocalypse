package net.spartanb312.grunteon.obfuscator.process.transformers.rename

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.lang.enText
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.hierarchy.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.util.Logger
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

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
        val classHierarchy = globalScopeValue {
            ClassHierarchy.build(instance.workRes.inputClassCollection, instance.workRes::getClassNode)
        }
        val newClasses = reducibleScopeValue {
            MergeableObjectList(ObjectArrayList<ClassNode>())
        }
        parForEachClasses {
            val copy = ClassNode()
            val adapter = ClassRemapper(copy, instance.nameMapping)
            it.accept(adapter)
            remapReflectionLiterals(instance, classHierarchy.global, copy)
            newClasses.local.add(copy)
        }
        seq {
            val instance = contextOf<Grunteon>()
            instance.workRes.inputClassMap.clear()
            newClasses.global.forEach { instance.workRes.inputClassMap[it.name] = it }
        }
    }

    private fun remapReflectionLiterals(instance: Grunteon, classHierarchy: ClassHierarchy, classNode: ClassNode) {
        classNode.methods.forEach { methodNode ->
            methodNode.instructions?.forEach { insnNode ->
                if (insnNode !is MethodInsnNode) return@forEach
                when {
                    insnNode.owner == "java/lang/Class" && insnNode.name == "forName" -> {
                        val pre = insnNode.previous as? LdcInsnNode ?: return@forEach
                        val literal = pre.cst as? String ?: return@forEach
                        val mapped = instance.nameMapping.getMapping(literal.replace('.', '/'))?.replace('/', '.')
                        if (mapped != null && mapped != literal) pre.cst = mapped
                    }

                    insnNode.owner == "java/lang/ClassLoader" && (insnNode.name == "loadClass" || insnNode.name == "findClass") -> {
                        val pre = insnNode.previous as? LdcInsnNode ?: return@forEach
                        val literal = pre.cst as? String ?: return@forEach
                        val mapped = instance.nameMapping.getMapping(literal.replace('.', '/'))?.replace('/', '.')
                        if (mapped != null && mapped != literal) pre.cst = mapped
                    }

                    insnNode.owner == "java/lang/ClassLoader" &&
                            (insnNode.name == "getSystemResource" ||
                                    insnNode.name == "getSystemResourceAsStream" ||
                                    insnNode.name == "getSystemResources") -> {
                        val pre = insnNode.previous as? LdcInsnNode ?: return@forEach
                        remapClassResourceLiteral(instance, pre, findLookupOwnerLiteral(insnNode))
                    }

                    insnNode.owner == "java/lang/Class" && (insnNode.name == "getMethod" || insnNode.name == "getDeclaredMethod") -> {
                        val methodLiteral = findReflectionStringArgument(insnNode) ?: return@forEach
                        val ownerLiteral = findReflectionOwnerLiteral(insnNode) ?: return@forEach
                        val mapped = findMappedReflectiveMethodName(
                            instance,
                            classHierarchy,
                            ownerLiteral.replace('.', '/'),
                            methodLiteral,
                            includeAncestors = insnNode.name == "getMethod"
                        ) ?: return@forEach
                        replaceReflectionStringArgument(insnNode, methodLiteral, mapped)
                    }

                    insnNode.owner == "java/lang/Class" && (insnNode.name == "getField" || insnNode.name == "getDeclaredField") -> {
                        val fieldLiteral = findReflectionStringArgument(insnNode) ?: return@forEach
                        val ownerLiteral = findReflectionOwnerLiteral(insnNode) ?: return@forEach
                        val mapped = findMappedReflectiveFieldName(
                            instance,
                            classHierarchy,
                            ownerLiteral.replace('.', '/'),
                            fieldLiteral,
                            includeAncestors = insnNode.name == "getField"
                        ) ?: return@forEach
                        replaceReflectionStringArgument(insnNode, fieldLiteral, mapped)
                    }

                    insnNode.owner == "java/lang/invoke/MethodHandles\$Lookup" &&
                            (insnNode.name == "findVirtual" || insnNode.name == "findStatic" || insnNode.name == "findSpecial") -> {
                        val methodLiteral = findReflectionStringArgument(insnNode) ?: return@forEach
                        val ownerLiteral = findLookupOwnerLiteral(insnNode) ?: return@forEach
                        val mapped = findMappedReflectiveMethodName(
                            instance,
                            classHierarchy,
                            ownerLiteral,
                            methodLiteral,
                            includeAncestors = insnNode.name == "findVirtual"
                        ) ?: return@forEach
                        replaceReflectionStringArgument(insnNode, methodLiteral, mapped)
                    }

                    insnNode.owner == "java/lang/invoke/MethodHandles\$Lookup" &&
                            (insnNode.name == "findGetter" || insnNode.name == "findSetter" ||
                                    insnNode.name == "findStaticGetter" || insnNode.name == "findStaticSetter") -> {
                        val fieldLiteral = findReflectionStringArgument(insnNode) ?: return@forEach
                        val ownerLiteral = findLookupOwnerLiteral(insnNode) ?: return@forEach
                        val mapped = findMappedReflectiveFieldName(
                            instance,
                            classHierarchy,
                            ownerLiteral,
                            fieldLiteral,
                            includeAncestors = insnNode.name == "findGetter" || insnNode.name == "findSetter"
                        ) ?: return@forEach
                        replaceReflectionStringArgument(insnNode, fieldLiteral, mapped)
                    }

                    insnNode.name == "findClass" && insnNode.desc == "(Ljava/lang/String;)Ljava/lang/Class;" -> {
                        val pre = insnNode.previous as? LdcInsnNode ?: return@forEach
                        val literal = pre.cst as? String ?: return@forEach
                        val mapped = instance.nameMapping.getMapping(literal.replace('.', '/'))?.replace('/', '.')
                        if (mapped != null && mapped != literal) pre.cst = mapped
                    }

                    insnNode.owner == "java/lang/invoke/MethodType" &&
                            insnNode.name == "fromMethodDescriptorString" &&
                            insnNode.desc == "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;" -> {
                        val literal = findMethodDescriptorStringArgument(insnNode) ?: return@forEach
                        val mapped = instance.nameMapping.mapMethodDesc(literal)
                        if (mapped != literal) {
                            replaceReflectionStringArgument(insnNode, literal, mapped)
                        }
                    }

                    insnNode.owner == "org/objectweb/asm/Type" &&
                            insnNode.desc == "(Ljava/lang/String;)Lorg/objectweb/asm/Type;" &&
                            (insnNode.name == "getType" || insnNode.name == "getMethodType") -> {
                        val pre = insnNode.previous as? LdcInsnNode ?: return@forEach
                        val literal = pre.cst as? String ?: return@forEach
                        val mapped = if (literal.startsWith("(")) {
                            instance.nameMapping.mapMethodDesc(literal)
                        } else {
                            instance.nameMapping.mapDesc(literal)
                        }
                        if (mapped != literal) pre.cst = mapped
                    }

                    insnNode.owner == "org/objectweb/asm/Type" &&
                            insnNode.name == "getObjectType" &&
                            insnNode.desc == "(Ljava/lang/String;)Lorg/objectweb/asm/Type;" -> {
                        val pre = insnNode.previous as? LdcInsnNode ?: return@forEach
                        val literal = pre.cst as? String ?: return@forEach
                        val mapped = instance.nameMapping.getMapping(literal.replace('.', '/')) ?: return@forEach
                        if (mapped != literal) pre.cst = mapped
                    }

                    insnNode.owner == "java/lang/constant/ClassDesc" &&
                            insnNode.name == "ofDescriptor" &&
                            insnNode.desc == "(Ljava/lang/String;)Ljava/lang/constant/ClassDesc;" -> {
                        val pre = insnNode.previous as? LdcInsnNode ?: return@forEach
                        val literal = pre.cst as? String ?: return@forEach
                        val mapped = instance.nameMapping.mapDesc(literal)
                        if (mapped != literal) pre.cst = mapped
                    }

                    insnNode.owner == "java/lang/constant/MethodTypeDesc" &&
                            insnNode.name == "ofDescriptor" &&
                            insnNode.desc == "(Ljava/lang/String;)Ljava/lang/constant/MethodTypeDesc;" -> {
                        val pre = insnNode.previous as? LdcInsnNode ?: return@forEach
                        val literal = pre.cst as? String ?: return@forEach
                        val mapped = instance.nameMapping.mapMethodDesc(literal)
                        if (mapped != literal) pre.cst = mapped
                    }

                    insnNode.owner == "org/objectweb/asm/Handle" &&
                            insnNode.name == "<init>" &&
                            (insnNode.desc == "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V"
                                    || insnNode.desc == "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V") -> {
                        remapAsmHandleArguments(instance, classHierarchy, insnNode)
                    }

                    insnNode.name == "getResource" && insnNode.desc == "(Ljava/lang/String;)Ljava/net/URL;" -> {
                        val pre = insnNode.previous as? LdcInsnNode ?: return@forEach
                        remapClassResourceLiteral(instance, pre, findLookupOwnerLiteral(insnNode))
                    }

                    insnNode.name == "getResourceAsStream" && insnNode.desc == "(Ljava/lang/String;)Ljava/io/InputStream;" -> {
                        val pre = insnNode.previous as? LdcInsnNode ?: return@forEach
                        remapClassResourceLiteral(instance, pre, findLookupOwnerLiteral(insnNode))
                    }

                    insnNode.name == "getResources" && insnNode.desc == "(Ljava/lang/String;)Ljava/util/Enumeration;" -> {
                        val pre = insnNode.previous as? LdcInsnNode ?: return@forEach
                        remapClassResourceLiteral(instance, pre, findLookupOwnerLiteral(insnNode))
                    }
                }
            }
        }
    }

    private fun findMappedReflectiveMethodName(
        instance: Grunteon,
        classHierarchy: ClassHierarchy,
        ownerLiteral: String,
        oldName: String,
        includeAncestors: Boolean
    ): String? {
        val owners = reflectiveOwnerCandidates(instance, classHierarchy, ownerLiteral, includeAncestors)
        return instance.nameMapping.mapReflectiveMethodName(owners.asSequence(), oldName)
    }

    private fun findMappedReflectiveFieldName(
        instance: Grunteon,
        classHierarchy: ClassHierarchy,
        ownerLiteral: String,
        oldName: String,
        includeAncestors: Boolean
    ): String? {
        val owners = reflectiveOwnerCandidates(instance, classHierarchy, ownerLiteral, includeAncestors)
        return instance.nameMapping.mapReflectiveFieldName(owners.asSequence(), oldName)
    }

    private fun reflectiveOwnerCandidates(
        instance: Grunteon,
        classHierarchy: ClassHierarchy,
        ownerLiteral: String,
        includeAncestors: Boolean
    ): List<String> {
        val normalized = ownerLiteral.replace('.', '/')
        val originalOwner = instance.nameMapping.getOriginalClassName(normalized) ?: normalized
        if (!includeAncestors) return listOf(originalOwner)
        val index = classHierarchy.findClass(originalOwner)
        if (index == -1) return listOf(originalOwner)
        val owners = ArrayList<String>(1 + classHierarchy.ancestors[index].size)
        owners += originalOwner
        classHierarchy.ancestors[index].forEach { ancestor ->
            if (ancestor in 0 until classHierarchy.realClassCount) {
                owners += classHierarchy.classNames[ancestor]
            }
        }
        return owners
    }

    private fun remapClassResourceLiteral(instance: Grunteon, ldc: LdcInsnNode, ownerLiteral: String?) {
        val literal = ldc.cst as? String ?: return
        if (!literal.endsWith(".class", true)) return
        val hasLeadingSlash = literal.startsWith("/")
        val rawPart = literal.removePrefix("/").dropLast(".class".length).replace('.', '/')
        val classPart = if ('/' !in rawPart && ownerLiteral != null) {
            val ownerPackage = ownerLiteral.substringBeforeLast('/', "")
            if (ownerPackage.isEmpty()) rawPart else "$ownerPackage/$rawPart"
        } else rawPart
        val mapped = instance.nameMapping.getMapping(classPart) ?: return
        ldc.cst = (if (hasLeadingSlash) "/" else "") + mapped + ".class"
    }

    private fun remapAsmHandleArguments(instance: Grunteon, classHierarchy: ClassHierarchy, insnNode: MethodInsnNode) {
        val args = findAsmHandleArguments(insnNode) ?: return
        val mappedOwner = instance.nameMapping.getMapping(args.owner.replace('.', '/'))
        if (mappedOwner != null && mappedOwner != args.owner) {
            args.ownerLdc.cst = mappedOwner
        }
        val mappedDesc = if (args.desc.startsWith("(")) {
            instance.nameMapping.mapMethodDesc(args.desc)
        } else {
            instance.nameMapping.mapDesc(args.desc)
        }
        if (mappedDesc != args.desc) {
            args.descLdc.cst = mappedDesc
        }

        val ownerLiteral = args.ownerLdc.cst as? String ?: args.owner
        val includeAncestors = isMethodHandleTag(args.tag) || isFieldHandleTag(args.tag)
        val ownerCandidates = reflectiveOwnerCandidates(instance, classHierarchy, ownerLiteral, includeAncestors)
        val mappedName = when {
            isMethodHandleTag(args.tag) -> instance.nameMapping.mapReflectiveMethodName(ownerCandidates.asSequence(), args.name)
            isFieldHandleTag(args.tag) -> instance.nameMapping.mapReflectiveFieldName(ownerCandidates.asSequence(), args.name)
            else -> null
        }
        if (mappedName != null && mappedName != args.name) {
            args.nameLdc.cst = mappedName
        }
    }

    private fun findReflectionStringArgument(insnNode: MethodInsnNode): String? {
        var cursor = insnNode.previous
        var budget = 12
        while (cursor != null && budget-- > 0) {
            when (cursor) {
                is LdcInsnNode -> {
                    val value = cursor.cst
                    if (value is String) return value
                }

                else -> {
                    if (cursor.opcode == Opcodes.INVOKEVIRTUAL ||
                        cursor.opcode == Opcodes.INVOKESTATIC ||
                        cursor.opcode == Opcodes.INVOKEINTERFACE ||
                        cursor.opcode == Opcodes.INVOKESPECIAL
                    ) return null
                }
            }
            cursor = cursor.previous
        }
        return null
    }

    private fun findMethodDescriptorStringArgument(insnNode: MethodInsnNode): String? {
        var cursor = insnNode.previous
        var budget = 16
        while (cursor != null && budget-- > 0) {
            when (cursor) {
                is LdcInsnNode -> {
                    val value = cursor.cst
                    if (value is String) return value
                }

                is MethodInsnNode -> {
                    if (cursor.owner == "java/lang/Class" &&
                        cursor.name == "getClassLoader" &&
                        cursor.desc == "()Ljava/lang/ClassLoader;"
                    ) {
                        cursor = cursor.previous
                        continue
                    }
                    return null
                }

                else -> Unit
            }
            cursor = cursor.previous
        }
        return null
    }

    private fun replaceReflectionStringArgument(insnNode: MethodInsnNode, expected: String, mapped: String) {
        var cursor = insnNode.previous
        var budget = 12
        while (cursor != null && budget-- > 0) {
            if (cursor is LdcInsnNode && cursor.cst == expected) {
                cursor.cst = mapped
                return
            }
            cursor = cursor.previous
        }
    }

    private fun findReflectionOwnerLiteral(insnNode: MethodInsnNode): String? {
        var cursor = insnNode.previous
        var budget = 20
        while (cursor != null && budget-- > 0) {
            when (cursor) {
                is LdcInsnNode -> {
                    val cst = cursor.cst
                    if (cst is Type && cst.sort == Type.OBJECT) {
                        return cst.internalName
                    }
                    if (cst is String && (cst.contains('/') || cst.contains('.'))) {
                        return cst
                    }
                }

                is MethodInsnNode -> {
                    val name = cursor.name
                    if (cursor.owner == "java/lang/Class" && name == "forName") {
                        val pre = cursor.previous as? LdcInsnNode
                        val cst = pre?.cst
                        return cst as? String
                    }
                    if (name == "findClass" && cursor.desc == "(Ljava/lang/String;)Ljava/lang/Class;") {
                        val pre = cursor.previous as? LdcInsnNode
                        val cst = pre?.cst
                        return cst as? String
                    }
                }
            }
            cursor = cursor.previous
        }
        return null
    }

    private fun findLookupOwnerLiteral(insnNode: MethodInsnNode): String? {
        var cursor = insnNode.previous
        var budget = 20
        while (cursor != null && budget-- > 0) {
            when (cursor) {
                is LdcInsnNode -> {
                    val cst = cursor.cst
                    if (cst is Type && cst.sort == Type.OBJECT) {
                        return cst.internalName
                    }
                    if (cst is String && (cst.contains('/') || cst.contains('.'))) {
                        return cst.replace('.', '/')
                    }
                }

                is MethodInsnNode -> {
                    if (cursor.owner == "java/lang/Class" && cursor.name == "forName") {
                        val pre = cursor.previous as? LdcInsnNode
                        val cst = pre?.cst
                        return (cst as? String)?.replace('.', '/')
                    }
                    if (cursor.owner == "java/lang/ClassLoader" && (cursor.name == "loadClass" || cursor.name == "findClass")) {
                        val pre = cursor.previous as? LdcInsnNode
                        val cst = pre?.cst
                        return (cst as? String)?.replace('.', '/')
                    }
                }
            }
            cursor = cursor.previous
        }
        return null
    }

    private fun findAsmHandleArguments(insnNode: MethodInsnNode): AsmHandleArguments? {
        val stringLdc = ArrayList<LdcInsnNode>(3)
        val intArgs = ArrayList<Int>(2)
        val requiredIntCount = if (insnNode.desc.endsWith("Z)V")) 2 else 1
        var cursor = insnNode.previous
        var budget = 24
        while (cursor != null && budget-- > 0) {
            when (cursor) {
                is LdcInsnNode -> if (cursor.cst is String) {
                    stringLdc += cursor
                    if (stringLdc.size == 3 && intArgs.size >= requiredIntCount) break
                }
                is org.objectweb.asm.tree.IntInsnNode -> {
                    intArgs += cursor.operand
                }
                is org.objectweb.asm.tree.InsnNode -> if (cursor.opcode in Opcodes.ICONST_0..Opcodes.ICONST_5) {
                    intArgs += cursor.opcode - Opcodes.ICONST_0
                }
                is MethodInsnNode -> return null
            }
            cursor = cursor.previous
        }
        if (stringLdc.size < 3 || intArgs.size < requiredIntCount) return null
        return AsmHandleArguments(
            tag = intArgs.last(),
            ownerLdc = stringLdc[2],
            nameLdc = stringLdc[1],
            descLdc = stringLdc[0]
        )
    }

    private fun isMethodHandleTag(tag: Int): Boolean {
        return tag == Opcodes.H_INVOKEVIRTUAL
                || tag == Opcodes.H_INVOKESTATIC
                || tag == Opcodes.H_INVOKESPECIAL
                || tag == Opcodes.H_NEWINVOKESPECIAL
                || tag == Opcodes.H_INVOKEINTERFACE
    }

    private fun isFieldHandleTag(tag: Int): Boolean {
        return tag == Opcodes.H_GETFIELD
                || tag == Opcodes.H_GETSTATIC
                || tag == Opcodes.H_PUTFIELD
                || tag == Opcodes.H_PUTSTATIC
    }

    private data class AsmHandleArguments(
        val tag: Int,
        val ownerLdc: LdcInsnNode,
        val nameLdc: LdcInsnNode,
        val descLdc: LdcInsnNode
    ) {
        val owner: String get() = ownerLdc.cst as String
        val name: String get() = nameLdc.cst as String
        val desc: String get() = descLdc.cst as String
    }
}
