package net.spartanb312.grunteon.obfuscator.util.extensions

import org.objectweb.asm.tree.*
import kotlin.contracts.contract

fun AbstractInsnNode.matchAnyOp(vararg opcodes: Int) = opcodes.any { it == opcode }

fun AbstractInsnNode.matchNoneOp(vararg opcodes: Int) = opcodes.none { it == opcode }

fun AbstractInsnNode.matchOp(opcode: Int) = this.opcode == opcode

fun AbstractInsnNode.nextMatchOp(opcode: Int): Boolean {
    val next = next ?: return false
    return next.opcode == opcode
}

fun ClassNode.findMethod(name: String, desc: String, predicate: (MethodNode) -> Boolean): MethodNode? {
    return methods?.firstOrNull { it.name == name && it.desc == desc && predicate(it) }
}

fun ClassNode.findField(name: String, desc: String, predicate: (FieldNode) -> Boolean): FieldNode? {
    return fields?.firstOrNull { it.name == name && it.desc == desc && predicate(it) }
}

fun AbstractInsnNode.matchInvoke(
    invokeType: Int,
    owner: String? = null,
    name: String? = null,
    desc: String? = null,
): Boolean {
    contract {
        returns(true) implies (this@matchInvoke is MethodInsnNode)
    }
    if (this !is MethodInsnNode) return false
    if (this.opcode != invokeType) return false
    if (owner != null && this.owner != owner) return false
    if (name != null && this.name != name) return false
    if (desc != null && this.desc != desc) return false
    return true
}

fun MethodInsnNode.match(
    owner: String,
    name: String? = null,
    desc: String? = null,
): Boolean {
    if (this.owner != owner) return false
    if (name != null && this.name != name) return false
    if (desc != null && this.desc != desc) return false
    return true
}