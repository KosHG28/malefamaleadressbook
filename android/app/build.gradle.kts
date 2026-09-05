import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Play Store release signing: reads app/keystore.properties (git-ignored, never committed) when
// present, so a real upload key can be dropped in locally or supplied by CI secrets without ever
// touching source control -- see android/RELEASE_SIGNING.md for how to generate one. Falls back
// to the debug key otherwise, which keeps the existing GitHub Releases APK flow (android-ci.yml,
// android-release.yml) working exactly as before for anyone who hasn't set one up.
val keystorePropertiesFile = file("keystore.properties")
val hasReleaseSigning = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasReleaseSigning) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

android {
    namespace = "com.koshg.interlude"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.koshg.interlude"
        // Android 16 (API 36) only, by design — no back-compat branches to maintain.
        minSdk = 36
        targetSdk = 36
        versionCode = 38
        versionName = "3.2.1"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("playStore") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Play requires a shrunk, obfuscated build; the default AndroidX/Room/Compose
            // consumer ProGuard rules cover this app's needs (no reflection-based JSON, no
            // custom native/JNI code), so no extra keep rules were needed in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("playStore")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.work.runtime.ktx)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
