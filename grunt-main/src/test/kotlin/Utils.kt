import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import kotlin.io.path.extension
import kotlin.io.path.readBytes
import kotlin.io.path.toPath
import kotlin.io.path.walk

fun readTestClasses(): List<ClassNode> {
    val path =
        net.spartanb312.grunteon.testcase.Asserts::class.java.protectionDomain.codeSource.location.toURI().toPath()
    return path.walk()
        .filter { it.extension == "class" }
        .map {
            val classNode = ClassNode()
            ClassReader(it.readBytes()).accept(classNode, ClassReader.EXPAND_FRAMES)
            classNode
        }
        .toList()
}