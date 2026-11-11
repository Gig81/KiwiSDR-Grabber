plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.sqlite.jdbc)
            implementation(libs.vosk)
            // JUnit 3.8.2 arriva come dipendenza transitiva (compile) di soundlibs:jlayer: non serve a runtime.
            val mp3spi = libs.mp3spi.get()
            implementation("${mp3spi.module}:${mp3spi.versionConstraint.requiredVersion}") {
                exclude(group = "junit")
            }
            implementation(libs.jna.platform)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.jump3r)
        }
        getByName("desktopTest").dependencies {
            implementation(kotlin("test"))
        }
    }
}
