import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.SourceSetContainer
import java.io.File
import java.util.UUID

plugins {
    id("buildsrc.convention.java")
    id("org.springframework.boot") version "3.3.5"
}

repositories {
    mavenCentral()
}

val springBootVersion = "3.3.5"
val mirroredRuntimeSessionId = providers
    .gradleProperty("grunteon.testRuntimeSessionId")
    .orElse(UUID.randomUUID().toString().replace("-", ""))

dependencies {
    implementation(project(":grunt-main"))

    implementation("org.springframework.boot:spring-boot-starter-webflux:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-validation:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-actuator:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-security:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive:$springBootVersion")
    implementation("org.springframework.kafka:spring-kafka:3.2.4")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.6.0")
    implementation("io.temporal:temporal-sdk:1.25.2")
    implementation("io.minio:minio:8.5.12")
    implementation("io.opentelemetry:opentelemetry-api:1.45.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.45.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.13.6")
    implementation("com.google.code.gson:gson:2.13.2")

    runtimeOnly("org.postgresql:postgresql:42.7.4")
    runtimeOnly("org.postgresql:r2dbc-postgresql:1.0.7.RELEASE")
    runtimeOnly("io.r2dbc:r2dbc-h2:1.0.0.RELEASE")

    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("io.projectreactor:reactor-test:3.6.11")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
}

val testSourceSet = project.extensions.getByType<SourceSetContainer>().getByName("test")
val mirroredTestRuntimeRoot = providers
    .gradleProperty("grunteon.testRuntimeRoot")
    .orElse(providers.environmentVariable("GRUNTEON_TEST_RUNTIME_ROOT"))
    .orElse(
        run {
            val preferredRoot = File(gradle.gradleUserHomeDir, "grunteon-test-runtime")
            val defaultRoot = if (preferredRoot.parentFile.exists() || preferredRoot.parentFile.mkdirs()) {
                preferredRoot
            } else {
                File(System.getProperty("java.io.tmpdir"), "grunteon-test-runtime")
            }
            defaultRoot.absolutePath
        }
    )
val mirroredRuntimeEntries = testSourceSet.runtimeClasspath.files.toList().mapIndexed { index, entry ->
    val mirrored = if (entry.toPath().startsWith(rootProject.rootDir.toPath())) {
        File(
            mirroredTestRuntimeRoot.get(),
            "${project.name}/${mirroredRuntimeSessionId.get()}/${index.toString().padStart(3, '0')}-${entry.name}"
        )
    } else {
        entry
    }
    entry to mirrored
}
val mirroredTestClassDirs = testSourceSet.output.classesDirs.files.map { dir ->
    mirroredRuntimeEntries.firstOrNull { it.first == dir }?.second ?: dir
}

tasks.register("prepareAsciiTestRuntime") {
    dependsOn("testClasses", ":grunt-main:jar", ":genesis:jar", ":grunt-bootstrap:jar")
    notCompatibleWithConfigurationCache("Mirrors project test runtime artifacts into an ASCII-only path for Gradle test workers.")
    outputs.dir(File(mirroredTestRuntimeRoot.get(), "${project.name}/${mirroredRuntimeSessionId.get()}"))
    doLast {
        val projectRoot = File(mirroredTestRuntimeRoot.get(), project.name)
        val root = File(projectRoot, mirroredRuntimeSessionId.get())
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
                if (target.exists() && !target.delete()) {
                    throw GradleException("Failed to replace mirrored test runtime file: ${target.absolutePath}")
                }
                source.copyTo(target, overwrite = true)
            }
        }
        projectRoot.listFiles()
            ?.filter { it.name != mirroredRuntimeSessionId.get() }
            ?.forEach { it.deleteRecursively() }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
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
