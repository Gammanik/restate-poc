plugins {
    application
    kotlin("jvm") version "2.1.0"
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.spring") version "2.1.0"
}

dependencies {
    implementation(project(":common"))

    // Temporal SDK
    implementation("io.temporal:temporal-sdk:1.26.2")
    testImplementation("io.temporal:temporal-testing:1.26.2")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // HTTP Client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

application {
    mainClass.set("com.mal.lospoc.temporal.TemporalApplicationKt")
    applicationDefaultJvmArgs = listOf(
        "-Xms2g",
        "-Xmx2g",
        "-XX:+AlwaysPreTouch",
        "-XX:+UseG1GC"
    )
}
