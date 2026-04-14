package net.spartanb312.grunteon.obfuscator.util.numerical

import net.spartanb312.genesis.kotlin.extensions.insn.DUP2
import net.spartanb312.genesis.kotlin.extensions.insn.DUP_X1
import net.spartanb312.genesis.kotlin.extensions.insn.DUP_X2
import net.spartanb312.genesis.kotlin.extensions.insn.IADD
import net.spartanb312.genesis.kotlin.extensions.insn.IAND
import net.spartanb312.genesis.kotlin.extensions.insn.ICONST_1
import net.spartanb312.genesis.kotlin.extensions.insn.ICONST_M1
import net.spartanb312.genesis.kotlin.extensions.insn.IOR
import net.spartanb312.genesis.kotlin.extensions.insn.ISUB
import net.spartanb312.genesis.kotlin.extensions.insn.IXOR
import net.spartanb312.genesis.kotlin.extensions.insn.POP
import net.spartanb312.genesis.kotlin.extensions.insn.SWAP
import net.spartanb312.genesis.kotlin.instructions
import org.objectweb.asm.tree.InsnList
import kotlin.random.Random

fun replaceINEG(type: Int = Random.nextInt(2)): InsnList = instructions {
    when (type) {
        0 -> {
            ICONST_M1
            IXOR
            ICONST_1
            IADD
        }

        else -> {
            ICONST_1
            ISUB
            ICONST_M1
            IXOR
        }
    }
}

fun replaceIAND(): InsnList = instructions {
    SWAP
    DUP_X1
    ICONST_M1
    IXOR
    IOR
    SWAP
    ICONST_M1
    IXOR
    ISUB
}

fun replaceIOR(): InsnList = instructions {
    DUP_X1
    ICONST_M1
    IXOR
    IAND
    IADD
}

fun replaceIXOR(type: Int = Random.nextInt(3)): InsnList = instructions {
    when (type) {
        0 -> {
            DUP2
            IOR
            DUP_X2
            POP
            IAND
            ISUB
        }

        1 -> {
            DUP2
            ICONST_M1
            IXOR
            IAND
            DUP_X2
            POP
            SWAP
            ICONST_M1
            IXOR
            IAND
            IOR
        }

        2 -> {
            DUP2
            IOR
            DUP_X2
            POP
            ICONST_M1
            IXOR
            SWAP
            ICONST_M1
            IXOR
            IOR
            IAND
        }

        else -> {
            DUP2
            IOR
            DUP_X2
            POP
            IAND
            ICONST_M1
            IXOR
            IAND
        }
    }
}