plugins {
    alias(libs.plugins.kotlin.jvm)
    kotlin("plugin.serialization") version libs.versions.kotlin
}

group = "com.pesegato"
version = "1.0.0"

dependencies {
    // Dipendenza dal modulo condiviso (RSACrypt, DeviceManager, ecc.)
    implementation(project(":common"))

    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.contentnegotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Logging
    implementation(libs.logback)

    testImplementation(kotlin("test"))
}