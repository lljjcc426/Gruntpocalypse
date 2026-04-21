plugins {
    id("buildsrc.convention.kotlin-jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    library(kotlin("stdlib"))
    library("org.ow2.asm:asm:9.7")
    library("org.ow2.asm:asm-tree:9.7")
    library("org.ow2.asm:asm-commons:9.7")
}
