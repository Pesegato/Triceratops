plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.plugin.compose)
}

dependencies {
    implementation(project(":common"))
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(kotlin("reflect"))
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation("com.github.mobile-dev-inc:dadb:1.2.10")
    implementation(compose.material3)
}

compose.desktop {
    application {
        mainClass = "ComposeMainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "Triceratops"
            packageVersion = project.version.toString()
        }
    }
}
