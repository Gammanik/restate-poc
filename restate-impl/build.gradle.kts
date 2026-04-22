plugins {
    application
    kotlin("jvm") version "2.1.0"
}

dependencies {
    implementation(project(":common"))

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

application {
    mainClass.set("com.mal.lospoc.restate.RestateAppKt")
    applicationDefaultJvmArgs = listOf(
        "-Xms2g",
        "-Xmx2g",
        "-XX:+AlwaysPreTouch",
        "-XX:+UseG1GC"
    )
}
