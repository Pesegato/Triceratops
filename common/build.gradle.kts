import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
}

val generateVersionProperties = tasks.register("generateVersionProperties") {
    val outputFile = layout.buildDirectory.file("generated/resources/version.properties").get().asFile
    outputs.file(outputFile)
    doLast {
        outputFile.parentFile.mkdirs()
        Properties().apply {
            setProperty("version", rootProject.extra["fullVersion"] as String)
            outputFile.writer().use { store(it, null) }
        }
    }
}

sourceSets.main.get().resources.srcDir(generateVersionProperties.map { it.outputs.files.singleFile.parentFile })
