import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop")

    sourceSets.getByName("desktopMain").dependencies {
        implementation(project(":shared"))
        implementation(compose.desktop.currentOs)
        implementation(libs.compose.material3)
        implementation(libs.compose.material.icons.extended)
        implementation(libs.kotlinx.coroutines.swing)
    }
}

compose.desktop {
    application {
        mainClass = "com.grabber.ui.MainKt"
        jvmArgs += listOf("-Dapp.version=${providers.gradleProperty("appVersion").get()}")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "KiwiSDRRecorder"
            packageVersion = providers.gradleProperty("appVersion").get()

            modules(
                "java.base",
                "java.desktop",
                "java.sql",
                "java.naming",
                "java.management",
                "java.instrument",
                "java.net.http",
                "jdk.unsupported"
            )

            windows {
                console = false
                menuGroup = "KiwiSDR"
                upgradeUuid = "a60cfb90-1e5b-4c07-b35f-287714be9580"
            }
        }

        val releaseVersion = providers.gradleProperty("appVersion").get()
        val releasePackageName = "KiwiSDRRecorder"
        val releaseAppImage = layout.buildDirectory.dir("compose/binaries/main/app/$releasePackageName")
        val windowsInstallerResources = layout.projectDirectory.dir("packaging/windows")

        fun registerReleasePackage(format: String) {
            val extension = format.lowercase()
            val outputFile = layout.buildDirectory.file(
                "compose/binaries/main/$extension/$releasePackageName-$releaseVersion.$extension"
            )
            tasks.register<org.gradle.api.tasks.Exec>("package${format}Release") {
                group = "distribution"
                description = "Creates the Windows $format release installer with post-install choices."
                dependsOn(tasks.named("createDistributable"))
                if (System.getenv("WIX_PATH") == null) {
                    dependsOn(rootProject.tasks.named("unzipWix"))
                }

                inputs.dir(releaseAppImage)
                inputs.dir(windowsInstallerResources)
                inputs.property("releaseVersion", releaseVersion)
                outputs.file(outputFile)

                doFirst {
                    val destination = outputFile.get().asFile
                    destination.parentFile.mkdirs()
                    if (destination.exists() && !destination.delete()) {
                        throw GradleException("Unable to replace release installer ${destination.absolutePath}")
                    }
                }

                val jpackage = File(System.getProperty("java.home"), "bin/jpackage.exe")
                val wixDirectory = System.getenv("WIX_PATH")
                    ?: rootProject.layout.buildDirectory.dir("wix311").get().asFile.absolutePath
                commandLine(
                    jpackage.absolutePath,
                    "--type", extension,
                    "--app-image", releaseAppImage.get().asFile.absolutePath,
                    "--name", releasePackageName,
                    "--app-version", releaseVersion,
                    "--win-upgrade-uuid", "a60cfb90-1e5b-4c07-b35f-287714be9580",
                    "--win-menu",
                    "--win-menu-group", "KiwiSDR",
                    "--resource-dir", windowsInstallerResources.asFile.absolutePath,
                    "--dest", outputFile.get().asFile.parentFile.absolutePath
                )
                environment("PATH", wixDirectory + File.pathSeparator + (System.getenv("PATH") ?: ""))
            }
        }

        registerReleasePackage("Msi")
        registerReleasePackage("Exe")
    }
}
