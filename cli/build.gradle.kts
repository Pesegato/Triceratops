plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.application)
    alias(libs.plugins.jib)
}

dependencies {
    implementation(project(":common"))

    implementation(libs.itext.core)
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(kotlin("reflect"))
    implementation(libs.mordant)
    implementation(libs.mordant.coroutines)
    implementation(libs.mordant.markdown)
}

application {
    mainClass = "MainKt"
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
    finalizedBy(tasks.named("pruneDockerImages"))
}
