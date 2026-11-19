plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
}

android {
    namespace = "com.example.bhpos"
    compileSdk = 35

    val vCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 10
    val vName = System.getenv("VERSION_NAME") ?: "1.1.3"

    defaultConfig {
        applicationId = "com.example.bhpos"
        minSdk = 24
        targetSdk = 35
        versionCode = vCode
        versionName = vName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing credentials come from local Gradle properties
    // (~/.gradle/gradle.properties, never committed) or environment variables
    // (CI secrets). If absent, release builds are left unsigned.
    fun signingProp(name: String): String? =
        (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }
            ?: System.getenv(name)?.takeIf { it.isNotBlank() }

    val releaseStorePath = signingProp("GODOWNWALA_STORE_FILE")

    signingConfigs {
        create("releaseConfig") {
            if (releaseStorePath != null) {
                storeFile = file(releaseStorePath)
                storePassword = signingProp("GODOWNWALA_STORE_PASSWORD")
                keyAlias = signingProp("GODOWNWALA_KEY_ALIAS")
                keyPassword = signingProp("GODOWNWALA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Uses the standard Android debug keystore.
        }
        release {
            isMinifyEnabled = false
            if (releaseStorePath != null) {
                signingConfig = signingConfigs.getByName("releaseConfig")
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
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
