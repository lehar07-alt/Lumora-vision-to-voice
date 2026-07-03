plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.lumora"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.lumora"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // ── PUT YOUR GEMINI API KEY HERE ──────────────────────────────────────
        // Get a free key at: https://aistudio.google.com/app/apikey
        buildConfigField("String", "GEMINI_API_KEY", "\"AIzaSyBjXS4TcwK4gF4Hp3yDOC0X40wVTNWkRC0\"")
        // ─────────────────────────────────────────────────────────────────────
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Android core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // CameraX — for accessing the camera
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // Gemini AI SDK — for scene description
    implementation("com.google.ai.client.generativeai:generativeai:0.7.0")

    // Kotlin coroutines — for background tasks
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
}