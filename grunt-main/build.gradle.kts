plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinxSerialization)
    //alias(libs.plugins.compose.compiler)
    //alias(libs.plugins.compose.hotReload)
}

import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.tasks.Jar
import org.gradle.api.tasks.SourceSetContainer
import java.io.File

repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io/")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://maven.noblesix.net/")
}

val coroutineVersion: String = libs.versions.coroutine.get()

dependencies {
    projectLib(project(":grunt-bootstrap"))
    projectLib(project(":genesis"))

    library("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutineVersion")

    // libraries
    library(libs.kotlinReflect)
    library(libs.kotlinxSerializationCore)
    library(libs.kotlinxSerializationJson)
    library(libs.bundles.asm)
    library(libs.bundles.utils)
    library(libs.bundles.apache.common)
    library(libs.bundles.ktor.server)
    library(libs.cfr)
    library("org.javassist:javassist:3.30.2-GA")

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(project(":grunt-testcase"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val testSourceSet = project.extensions.getByType<SourceSetContainer>().getByName("test")
val mirroredTestRuntimeRoot = providers
    .gradleProperty("grunteon.testRuntimeRoot")
    .orElse(providers.environmentVariable("GRUNTEON_TEST_RUNTIME_ROOT"))
    .orElse(
        run {
            val windowsDevCache = File("D:/dev-cache/grunteon-test-runtime")
            val defaultRoot = if (windowsDevCache.parentFile.exists() || windowsDevCache.parentFile.mkdirs()) {
                windowsDevCache
            } else {
                File(System.getProperty("java.io.tmpdir"), "grunteon-test-runtime")
            }
            defaultRoot.absolutePath
        }
    )
val mirroredRuntimeEntries = testSourceSet.runtimeClasspath.files.toList().mapIndexed { index, entry ->
    val mirrored = if (entry.toPath().startsWith(rootProject.rootDir.toPath())) {
        File(mirroredTestRuntimeRoot.get(), "${project.name}/${index.toString().padStart(3, '0')}-${entry.name}")
    } else {
        entry
    }
    entry to mirrored
}
val mirroredTestClassDirs = testSourceSet.output.classesDirs.files.map { dir ->
    mirroredRuntimeEntries.firstOrNull { it.first == dir }?.second ?: dir
}

tasks {
    register("prepareAsciiTestRuntime") {
        dependsOn("testClasses")
        notCompatibleWithConfigurationCache("Mirrors project test runtime artifacts into an ASCII-only path for Gradle test workers.")
        outputs.dir(File(mirroredTestRuntimeRoot.get(), project.name))
        doLast {
            val root = File(mirroredTestRuntimeRoot.get(), project.name)
            if (root.exists()) {
                root.deleteRecursively()
            }
            root.mkdirs()
            mirroredRuntimeEntries.forEach { (source, target) ->
                if (source == target || !source.exists()) return@forEach
                if (source.isDirectory) {
                    copy {
                        from(source)
                        into(target)
                    }
                } else {
                    target.parentFile.mkdirs()
                    source.copyTo(target, overwrite = true)
                }
            }
        }
    }

    jar {
        manifest {
            attributes(
                "Main-Class" to "net.spartanb312.grunteon.obfuscator.MainKt"
            )
        }
    }

    register<Jar>("distJar") {
        group = "build"
        description = "Build a standalone Grunteon executable jar with runtime dependencies."
        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        archiveClassifier.set("all")
        dependsOn(configurations.runtimeClasspath)
        from(sourceSets.getByName("main").output)
        from({
            configurations.runtimeClasspath.get()
                .filter { it.name.endsWith(".jar") }
                .map { zipTree(it) }
        })
        exclude(
            "META-INF/versions/**",
            "META-INF/*.RSA",
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/INDEX.LIST",
            "module-info.class"
        )
        manifest {
            attributes(
                "Main-Class" to "net.spartanb312.grunteon.obfuscator.MainKt"
            )
        }
    }

    register<JavaExec>("runWeb") {
        group = "application"
        description = "Run Grunteon Web UI"
        val sourceSets = project.extensions.getByType<SourceSetContainer>()
        val webPort = providers.gradleProperty("webPort").orElse("8080")
        classpath = sourceSets.getByName("main").runtimeClasspath
        mainClass.set("net.spartanb312.grunteon.obfuscator.MainKt")
        jvmArgs("--enable-preview")
        args("--web")
        args(webPort.map { "--port=$it" })
        dependsOn("classes")
    }
}

tasks.withType<Test> {
    dependsOn("prepareAsciiTestRuntime")
    notCompatibleWithConfigurationCache("Uses an ASCII-only mirrored runtime to avoid Gradle test worker classpath issues in non-ASCII workspaces.")
    maxHeapSize = "4G"
    testClassesDirs = files(mirroredTestClassDirs)
    classpath = files(mirroredRuntimeEntries.map { it.second })
    isScanForTestClasses = false
    include("**/*Test.class")
    doFirst {
        logger.lifecycle("Using mirrored test runtime root: ${mirroredTestRuntimeRoot.get()}")
    }
}
