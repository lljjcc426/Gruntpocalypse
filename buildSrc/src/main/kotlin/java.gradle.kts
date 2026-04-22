package buildsrc.convention

import org.gradle.api.tasks.testing.Test

group = "net.spartanb312"

plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

val library: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}
val projectLib: Configuration by configurations.creating {
    configurations.api.get().extendsFrom(this)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.add("--enable-preview")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
}
