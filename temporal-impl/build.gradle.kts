plugins {
    application
}

dependencies {
    implementation(project(":common"))

    implementation("io.temporal:temporal-sdk:1.26.2")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("io.temporal:temporal-testing:1.26.2")
}

application {
    mainClass.set("com.mal.lospoc.temporal.TemporalApp")
}
