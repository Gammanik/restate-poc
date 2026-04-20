plugins {
    application
}

dependencies {
    implementation(project(":common"))

    implementation("dev.restate:sdk-api:2.0.0")
    implementation("dev.restate:sdk-http-vertx:2.0.0")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("dev.restate:sdk-testing:2.0.0")
}

application {
    mainClass.set("com.mal.lospoc.restate.RestateApp")
}
