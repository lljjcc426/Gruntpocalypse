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

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(project(":grunt-testcase"))
}

tasks {
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
    maxHeapSize = "4G"
}
