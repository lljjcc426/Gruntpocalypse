package net.spartanb312.grunteon.obfuscator.util.extensions

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

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