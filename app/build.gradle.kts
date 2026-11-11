import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.youtubeceviri"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.youtubeceviri"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // API anahtarları local.properties'ten okunur (git'e girmez).
        // Kendi anahtarlarınızı local.properties içine yazın:
        //   GEMINI_API_KEY=...   (https://aistudio.google.com/apikey)
        //   NVIDIA_API_KEY=...   (https://build.nvidia.com)
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProps.getProperty("GEMINI_API_KEY", "")}\"")
        buildConfigField("String", "NVIDIA_API_KEY", "\"${localProps.getProperty("NVIDIA_API_KEY", "")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:vision-common:17.3.0")
    // ML Kit on-device translate kaldırıldı: düşük RAM'li TV'leri kastırıyordu.
    // Çeviriler artık yalnızca Gemini + NVIDIA Riva API'lerinden geliyor.
}