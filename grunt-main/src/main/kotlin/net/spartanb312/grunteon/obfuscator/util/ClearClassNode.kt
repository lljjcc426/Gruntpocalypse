package net.spartanb312.grunteon.obfuscator.util

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter

class ClearClassNode(api: Int, cv: ClassVisitor) : ClassNode(api) {

    init {
        this.cv = cv
    }

    override fun visitEnd() {
        methods.forEach { transform(name, it) }
        super.visitEnd()
        if (cv != null) accept(cv)
    }

    fun transform(owner: String, mn: MethodNode) {
        mn.maxStack = -1
        val ana = Analyzer(BasicInterpreter())
        try {
            ana.analyze(owner, mn)
            val frames = ana.frames
            val instructions = mn.instructions.toArray()
            for (i in frames.indices) {
                if (frames[i] == null && instructions[i] !is LabelNode) {
                    mn.instructions.remove(instructions[i])
                }
            }
        } catch (_: Exception) {

        }
    }

}