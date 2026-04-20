plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Kotlin Coroutines support for Spring
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Restate SDK
    implementation("dev.restate:sdk-api-kotlin-gen:2.5.0") {
        exclude(group = "org.slf4j", module = "slf4j-nop")
    }
    ksp("dev.restate:sdk-api-kotlin-gen:2.5.0")

    implementation("dev.restate:sdk-kotlin-http:2.5.0") {
        exclude(group = "org.slf4j", module = "slf4j-nop")
    }

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")

    // Exclude slf4j-nop from all configurations
    configurations.all {
        exclude(group = "org.slf4j", module = "slf4j-nop")
    }

    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

application {
    mainClass.set("org.example.LoanApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(23)
}
