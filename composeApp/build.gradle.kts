import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
                console = true
                menuGroup = "KiwiSDR"
                upgradeUuid = "a60cfb90-1e5b-4c07-b35f-287714be9580"
            }
        }
    }
}
