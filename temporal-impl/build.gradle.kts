plugins {
    application
    kotlin("jvm") version "2.1.0"
}

dependencies {
    implementation(project(":common"))

    implementation("io.temporal:temporal-sdk:1.26.2")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    testImplementation("io.temporal:temporal-testing:1.26.2")
}

application {
    mainClass.set("com.mal.lospoc.temporal.TemporalAppKt")
    applicationDefaultJvmArgs = listOf(
        "-Xms2g",
        "-Xmx2g",
        "-XX:+AlwaysPreTouch",
        "-XX:+UseG1GC"
    )
}
