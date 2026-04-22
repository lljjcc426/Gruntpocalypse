package buildsrc.convention

group = "net.spartanb312"

plugins {
    id("buildsrc.convention.kotlin-jvm")
    `maven-publish`
}

java {
    toolchain {
        // Use a specific Java version to make it easier to work in different environments.
        languageVersion.set(JavaLanguageVersion.of(8))
    }
    withSourcesJar()
}

kotlin {
    jvmToolchain(8)
}
