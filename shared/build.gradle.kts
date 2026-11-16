import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

// Il target Android si attiva solo se l'SDK è disponibile (container Docker o Android Studio):
// sul PC di sviluppo desktop la build resta identica e non richiede l'SDK.
val androidEnabled = rootProject.extra["kiwi.androidEnabled"] as Boolean
if (androidEnabled) {
    apply(plugin = "com.android.kotlin.multiplatform.library")
}

kotlin {
    jvm("desktop")
    if (androidEnabled) {
        configureAndroidLibraryTarget(this)
    }

    // jvmCommon: codice JVM condiviso tra desktop e Android (niente java.net.http, javax.*, AWT, JNA).
    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withCompilations {
                    it.platformType == KotlinPlatformType.jvm || it.platformType == KotlinPlatformType.androidJvm
                }
            }
        }
    }

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
        getByName("jvmCommonTest").dependencies {
            implementation(kotlin("test"))
        }
        getByName("desktopTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        if (androidEnabled) {
            getByName("androidMain").dependencies {
                implementation(libs.okhttp)
                implementation(libs.bouncycastle.bcprov)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.androidx.core.ktx)
            }
            getByName("androidHostTest").dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }
    }
}

/** Il plugin Android KMP è applicato in modo condizionale: la configurazione passa per tipo, non per accessor. */
fun configureAndroidLibraryTarget(kmp: org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension) {
    (kmp as ExtensionAware).extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension> {
        namespace = "com.gig81.kiwisdrgrabber.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTestBuilder { }
    }
}
