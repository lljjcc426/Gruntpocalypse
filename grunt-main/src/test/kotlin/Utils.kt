import net.spartanb312.grunteon.obfuscator.Grunteon
import net.spartanb312.grunteon.obfuscator.config.manager.ConfigGroup
import net.spartanb312.grunteon.obfuscator.pipeline.ProcessPipeline
import net.spartanb312.grunteon.obfuscator.process.resource.WorkResources
import kotlin.io.path.extension
import kotlin.io.path.toPath

fun readTestClasses(klass: Class<*>, pipeline: ProcessPipeline = ProcessPipeline()): Grunteon {
    val path = klass.protectionDomain.codeSource.location.toURI().toPath()
    check(path.extension == "jar")
    val instance = Grunteon(ConfigGroup(), pipeline)
    instance.workRes = WorkResources.read(path, emptyList())
    return instance
}