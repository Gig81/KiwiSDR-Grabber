rootProject.name = "KiwiSDRRecorder"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":shared")
include(":composeApp")


// Versione Android: attiva solo se l'SDK è disponibile (ANDROID_HOME, ANDROID_SDK_ROOT o
// sdk.dir in local.properties), oppure forzata con -Pkiwi.android=true|false.
// Sul PC desktop senza SDK la build resta quella di sempre; la build Android gira nel
// container Docker (scripts/android/build.ps1).
val localSdkDir = file("local.properties").takeIf { it.isFile }?.let { propertiesFile ->
    java.util.Properties().apply { propertiesFile.inputStream().use(::load) }.getProperty("sdk.dir")
}
val androidSdkDir = localSdkDir ?: providers.environmentVariable("ANDROID_HOME").orNull
    ?: providers.environmentVariable("ANDROID_SDK_ROOT").orNull
val androidEnabled = providers.gradleProperty("kiwi.android").orNull?.toBoolean()
    ?: (androidSdkDir != null && file(androidSdkDir).isDirectory)

gradle.rootProject { extra["kiwi.androidEnabled"] = androidEnabled }
if (androidEnabled) {
    include(":androidApp")
}
