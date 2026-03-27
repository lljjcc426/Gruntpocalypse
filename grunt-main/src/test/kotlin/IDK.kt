import java.net.URI
import java.nio.file.FileSystems
import kotlin.io.path.Path
import kotlin.io.path.walk

fun main() {
    val zipPath = Path("I:\\code\\obf\\Grunteon\\run\\nvasm-dump-1.0.1-release.jar")
    val fullUri = URI.create("jar:" + zipPath.toUri())
    println(fullUri)
    val zipFileSystem = FileSystems.newFileSystem(fullUri, mapOf<String, String>())
    val zipRoot = zipFileSystem.getPath("/")
    println(zipRoot)
    zipRoot.walk().forEach {
        println(it)
    }
}