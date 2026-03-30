package net.spartanb312.grunteon.obfuscator.util

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import net.spartanb312.grunteon.obfuscator.process.*
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.ClassHierarchy
import net.spartanb312.grunteon.obfuscator.process.hierarchy2.MethodHierarchy
import net.spartanb312.grunteon.obfuscator.util.collection.FastObjectArrayList
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.InvokeDynamicInsnNode

object IndyChecker {
    context(_: PipelineBuilder)
    fun check(
        hierarchyInfo: ScopeValueKey.Global<MethodHierarchy>,
        infoMappings: ScopeValueKey.Global<out Int2ObjectMap<String>>
    ): ScopeValueKey.Reducible<MergeableObjectList<IndyImplicitInfo>> {
        val results = reducibleScopeValue { MergeableObjectList<IndyImplicitInfo>(FastObjectArrayList()) }
        parForEach { classNode ->
            val mh = hierarchyInfo.global
            val ch = mh.classHierarchy
            context(ch, mh) {
                val results = results.local
                val mappings = infoMappings.global
                classNode.methods.forEach { methodNode ->
                    methodNode.instructions?.forEach { insnNode ->
                        if (insnNode is InvokeDynamicInsnNode) {
                            results.addAll(checkInsn(insnNode, mappings))
                        }
                    }
                }
            }
        }
        return results
    }

    context(ch: ClassHierarchy, mh: MethodHierarchy)
    private fun checkInsn(
        invokeDynamicInsnNode: InvokeDynamicInsnNode,
        infoMappings: Int2ObjectMap<String> // Method, new name, desc
    ): List<IndyImplicitInfo> {
        val results = mutableListOf<IndyImplicitInfo>()
        if (invokeDynamicInsnNode.bsmArgs == null) return results
        var indexOfHandle = -1
        invokeDynamicInsnNode.bsmArgs.forEachIndexed { index, obj ->
            if (obj is Handle) indexOfHandle = index
        }
        if (indexOfHandle == -1) return results
        val handle = invokeDynamicInsnNode.bsmArgs[indexOfHandle] as Handle
        val handleCodename = handle.name + handle.desc
        val handleMethodCode = mh.methodCodeLookup.getInt(handleCodename)
        if (handleMethodCode == -1) return results
        val insnName = invokeDynamicInsnNode.name
        val insnOwner = invokeDynamicInsnNode.desc.substringAfter(")L").removeSuffix(";")
        val indyParams = invokeDynamicInsnNode.desc.substringAfter("(").substringBeforeLast(")")
        val originParams = handle.desc.substringAfter("(").substringBeforeLast(")")
        val remainParams = originParams.removePrefix(indyParams)
        val insnDesc = "(" + remainParams + ")" + handle.desc.substringAfterLast(")")
        val insnTypes = Type.getArgumentTypes(insnDesc)
        val insnOwnerClass = ch.findClassEntry(insnOwner)
        if (!insnOwnerClass.isValid) return results
        val insnOwnerMethods = insnOwnerClass.methods
        run outer@{
            mh.methodCodeToMethods[handleMethodCode].forEach { prev ->
                val shouldRemap = when (handle.tag) {
                    Opcodes.H_INVOKEVIRTUAL -> ch.isSubType(handle.owner, prev.owner.name)
                    Opcodes.H_INVOKESTATIC -> ch.isSubType(handle.owner, prev.owner.name)
                    else -> handle.owner == prev.owner.name
                }
                if (!shouldRemap) return@forEach
                insnOwnerMethods.forEach { preMethod ->
                    val paramsTypes = Type.getArgumentTypes(preMethod.desc)
                    var typesMatch = paramsTypes.size == insnTypes.size
                    if (typesMatch) {
                        for (index in paramsTypes.indices) {
                            val type1 = insnTypes[index]
                            val type2 = paramsTypes[index]
                            if (!(type1.className == type2.className || type2.className == "java.lang.Object")) {
                                typesMatch = false
                            }
                        }
                    }

                    if (preMethod.name == insnName && (preMethod.desc == insnDesc || typesMatch)) {
                        val newName = infoMappings.get(preMethod.index)
                        results.add(
                            IndyImplicitInfo(
                                invokeDynamicInsnNode.name,
                                invokeDynamicInsnNode.desc,
                                newName
                            )
                        )
                    }
                }
                return@outer
            }
        }
        return results
    }

    class IndyImplicitInfo(
        val indyInsnName: String,
        val indyInsnDesc: String,
        val newName: String
    )

}