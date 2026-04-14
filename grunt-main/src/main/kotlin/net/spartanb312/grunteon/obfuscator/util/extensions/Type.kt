package net.spartanb312.grunteon.obfuscator.util.extensions

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

fun String.getReturnType(): Int = when (Type.getReturnType(this).sort) {
    Type.BOOLEAN -> Opcodes.IRETURN
    Type.CHAR -> Opcodes.IRETURN
    Type.BYTE -> Opcodes.IRETURN
    Type.SHORT -> Opcodes.IRETURN
    Type.INT -> Opcodes.IRETURN
    Type.LONG -> Opcodes.LRETURN
    Type.FLOAT -> Opcodes.FRETURN
    Type.DOUBLE -> Opcodes.DRETURN
    Type.VOID -> Opcodes.RETURN
    else -> Opcodes.ARETURN
}

fun Type.getLoadType(): Int = when (sort) {
    Type.BOOLEAN -> Opcodes.ILOAD
    Type.CHAR -> Opcodes.ILOAD
    Type.BYTE -> Opcodes.ILOAD
    Type.SHORT -> Opcodes.ILOAD
    Type.INT -> Opcodes.ILOAD
    Type.LONG -> Opcodes.LLOAD
    Type.FLOAT -> Opcodes.FLOAD
    Type.DOUBLE -> Opcodes.DLOAD
    else -> Opcodes.ALOAD
}