plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val appVersion = providers.gradleProperty("appVersion").get()

/** 1.2.3 → 10203: stesso versionCode per APK GitHub e AAB Play Store della stessa versione. */
fun versionCodeOf(version: String): Int {
    val (major, minor, patch) = version.split('.').map { it.takeWhile(Char::isDigit).toInt() }
    return major * 10_000 + minor * 100 + patch
}

// Firma di release: keystore custodito fuori dal repository, indicato con proprietà Gradle
// (~/.gradle/gradle.properties) o variabili d'ambiente. Senza, la release resta non firmata.
fun signingValue(property: String, environment: String): String? =
    providers.gradleProperty(property).orNull ?: providers.environmentVariable(environment).orNull

val releaseStoreFile = signingValue("kiwi.signing.storeFile", "KIWI_SIGNING_STORE_FILE")

android {
    namespace = "com.gig81.kiwisdrgrabber"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.gig81.kiwisdrgrabber"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("kiwi.signing.storePassword", "KIWI_SIGNING_STORE_PASSWORD")
                keyAlias = signingValue("kiwi.signing.keyAlias", "KIWI_SIGNING_KEY_ALIAS")
                keyPassword = signingValue("kiwi.signing.keyPassword", "KIWI_SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Analizza anche androidMain di :shared (API non disponibili su Android 10, ecc.).
        checkDependencies = true
    }

    packaging {
        resources.excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES")
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
}
