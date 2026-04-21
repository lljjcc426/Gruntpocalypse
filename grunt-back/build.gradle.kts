import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

plugins {
    id("buildsrc.convention.java")
    id("org.springframework.boot") version "3.3.5"
}

repositories {
    mavenCentral()
}

val springBootVersion = "3.3.5"

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

tasks.named<Jar>("jar") {
    enabled = false
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
