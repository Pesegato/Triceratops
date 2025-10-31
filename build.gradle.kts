import com.palantir.gradle.gitversion.VersionDetails
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.kotlin.plugin.compose) apply false
    alias(libs.plugins.jib) apply false
    id("com.palantir.git-version") version "4.0.0"
}

val versionDetails: groovy.lang.Closure<VersionDetails> by extra
val fullVersion = versionDetails().version

val cleanVersion = fullVersion.split("-").first().let {
    val parts = it.split(".")
    val major = parts.getOrElse(0) { "0" }.let { if (it == "0") "1" else it }
    val minor = parts.getOrElse(1) { "0" }
    val patch = parts.getOrElse(2) { "0" }
    "$major.$minor.$patch"
}

extra["fullVersion"] = fullVersion

subprojects {
    version = cleanVersion

    repositories {
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.google.com/")
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }
    }
}
