plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.application)
    alias(libs.plugins.jib)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":web"))
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
        image = "docker://triceratops-base:latest"
    }
    to {
        //image = "triceratops-app"
        image = "ghcr.io/pesegato/triceratops-app"
        tags = setOf("latest", "${project.version}")
    }

    container {
        // Sovrascriviamo l'entrypoint di default
        entrypoint = listOf("/bin/bash", "/entrypoint.sh")
        args = listOf()
        // Usa mapOf() per l'Environment (si aspetta una Map)
        environment = mapOf(
            "RUNNING_IN_DOCKER" to "true",
            "TPM2_PKCS11_TCTI" to "device:/dev/tpmrm0",
            "TPM2TOOLS_TCTI" to "device:/dev/tpmrm0",
            "TPM2_PKCS11_STORE" to "/var/lib/tpm2-pkcs11"
        )

        user = "root"

        volumes = listOf("/var/lib/tpm2-pkcs11")

        extraDirectories {
            paths {
                path {
                    setFrom("src/main/jib")
                }
            }
            permissions = mapOf(
                "/clean_tpm.sh" to "755",
                "/provision_tpm.sh" to "755",
                "/entrypoint.sh" to "755"
            )
        }
    }
}

tasks.register<Exec>("buildBaseImage") {
    group = "Docker"
    description = "Builds the base Docker image with TPM tools."
    // Eseguiamo la build dalla root del progetto per trovare il Dockerfile.base
    workingDir(project.rootDir)
    commandLine("docker", "build", "-f", "Dockerfile", "-t", "triceratops-base:latest", ".")
}

tasks.register<Exec>("pruneDockerImages") {
    group = "Docker"
    description = "Removes stale (dangling) Docker images created by Jib."
    commandLine("docker", "image", "prune", "--force", "--filter", "dangling=true")
}

tasks.named("jibDockerBuild") {
    dependsOn("buildBaseImage")
    finalizedBy(tasks.named("pruneDockerImages"))
}

tasks.named("jib") {
    dependsOn("buildBaseImage")
}
