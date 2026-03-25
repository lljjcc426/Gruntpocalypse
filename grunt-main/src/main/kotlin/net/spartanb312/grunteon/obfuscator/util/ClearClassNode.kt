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
        methods.forEach { mn ->
            transform(name, mn)
        }
        super.visitEnd()
        if (cv != null) {
            accept(cv)
        }
    }

    fun transform(owner: String, mn: MethodNode) {
        mn.maxStack = -1
        val ana = Analyzer(BasicInterpreter())
        runCatching {
            ana.analyze(owner, mn)
            val frames = ana.frames
            val insnNodes = mn.instructions.toArray()
            for (i in frames.indices) {
                if (frames[i] == null && insnNodes[i] !is LabelNode) {
                    mn.instructions.remove(insnNodes[i])
                }
            }
        }.onFailure {
            //it.printStackTrace()
        }
    }
}