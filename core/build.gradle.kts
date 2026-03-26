plugins {
    alias(libs.plugins.kotlin.jvm)
    id("java-library")
    `maven-publish`
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}

group = "com.pesegato"
version = "1.0-SNAPSHOT"

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.pesegato"
            artifactId = "triceratops-core"
            version = "1.0-SNAPSHOT"
        }
    }
}