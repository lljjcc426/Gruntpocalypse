plugins {
    id("buildsrc.convention.kotlin-jvm")
    //alias(libs.plugins.compose)
    //alias(libs.plugins.compose.compiler)
    //alias(libs.plugins.compose.hotReload)
}

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
    projectLib("net.spartanb312:genesis-kotlin:1.0.0")

    library("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutineVersion")

    // libraries
    library(libs.bundles.asm)
    library(libs.bundles.utils)

    testImplementation(kotlin("test"))
    testImplementation(project(":grunt-testcase"))
}

tasks {
    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(configurations["projectLib"].map { if (it.isDirectory) it else zipTree(it) })
        from(configurations["library"].map { if (it.isDirectory) it else zipTree(it) })
        exclude("META-INF/versions/**", "module-info.class", "**/**.RSA")
        manifest {
            attributes(
                "Main-Class" to "net.spartanb312.everett.bootstrap.Main"
            )
        }
        dependsOn(":grunt-bootstrap:jar")
    }
}

tasks.withType<Test> {
    maxHeapSize = "4G"
}