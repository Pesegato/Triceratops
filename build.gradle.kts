import java.util.Properties

plugins {
    kotlin("jvm") version "2.2.0"
    application
    id("com.palantir.git-version") version "4.0.0"
    id("com.google.cloud.tools.jib") version "3.4.3"
}

application {
    mainClass = "MainKt"
}

group = "com.pesegato"
val gitVersion: groovy.lang.Closure<String> by extra
version = gitVersion()
val mordantVersion = "3.0.2"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.itextpdf:itext7-core:8.0.4")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")
    implementation("com.squareup.moshi:moshi:1.15.2")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation(kotlin("reflect"))
// Adds all JVM interface modules
    implementation("com.github.ajalt.mordant:mordant:${mordantVersion}")
// optional extensions for running animations with coroutines
    implementation("com.github.ajalt.mordant:mordant-coroutines:${mordantVersion}")
// optional widget for rendering Markdown
    implementation("com.github.ajalt.mordant:mordant-markdown:${mordantVersion}")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<ProcessResources>() {
    doLast {
        //val propertiesFile = file("$buildDir/resources/main/version.properties")
        val propertiesFile = layout.buildDirectory.file("resources/main/version.properties").get().asFile
        propertiesFile.parentFile.mkdirs()
        val properties = Properties()
        properties.setProperty("version", rootProject.version.toString())
        propertiesFile.writer().use { properties.store(it, null) }
    }
}

kotlin {
    jvmToolchain(21)
}

jib {
    from {
        image = "eclipse-temurin:21-jre-jammy"
    }
    to {
        image = "triceratops-app"
    }
    container {
        mainClass = "MainKt"
    }
}

tasks.register<Exec>("pruneDockerImages") {
    group = "Docker"
    description = "Removes stale (dangling) Docker images created by Jib."
    commandLine("docker", "image", "prune", "--force", "--filter", "dangling=true")
}

tasks.named("jibDockerBuild") {
    finalizedBy("pruneDockerImages")
}
