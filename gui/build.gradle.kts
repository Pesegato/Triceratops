plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.plugin.compose)
}

dependencies {
    implementation(project(":common"))
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    //implementation("com.github.mobile-dev-inc:dadb:1.2.10")
    implementation(compose.material3)
    //forse non serve
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

compose.desktop {
    application {
        mainClass = "ComposeMainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "Triceratops"
            //packageVersion = project.version.toString()
// Forza una versione numerica valida per gli standard OS (Debian/MSI/Dmg)
            packageVersion = "1.1.0"

            linux { debPackageVersion = "1.1.0" }
            windows { msiPackageVersion = "1.1.0" }
            macOS { dmgPackageVersion = "1.1.0" }
        }
    }
}
