plugins {
    application
    kotlin("jvm") version "2.1.0"
    kotlin("kapt") version "2.1.0"
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.spring") version "2.1.0"
}

dependencies {
    implementation(project(":common"))

    // Restate SDK - using Java API for annotation processing
    implementation("dev.restate:sdk-api:2.1.0")
    implementation("dev.restate:sdk-http-vertx:2.1.0")
    implementation("dev.restate:client:2.1.0")
    annotationProcessor("dev.restate:sdk-api-gen:2.1.0")
    kapt("dev.restate:sdk-api-gen:2.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.9.0")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // HTTP Client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

application {
    mainClass.set("com.mal.lospoc.restate.RestateApplicationKt")
    applicationDefaultJvmArgs = listOf(
        "-Xms2g",
        "-Xmx2g",
        "-XX:+AlwaysPreTouch",
        "-XX:+UseG1GC"
    )
}

tasks.register<JavaExec>("runEndpoint") {
    group = "application"
    description = "Run Restate endpoint on port 9080"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.mal.lospoc.restate.RestateEndpointKt")
    jvmArgs = listOf("-Xms1g", "-Xmx1g")
}
