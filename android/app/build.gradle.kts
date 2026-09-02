plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.koshg.calendar"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.koshg.calendar"
        // Android 16 (API 36) only, by design — no back-compat branches to maintain.
        minSdk = 36
        targetSdk = 36
        versionCode = 22
        versionName = "3.0.19"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so CI can produce an installable APK without a
            // dedicated release keystore/secrets. Fine for direct-install distribution
            // via GitHub Releases; swap in a real signing config before a Play Store upload.
            signingConfig = signingConfigs.getByName("debug")
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
