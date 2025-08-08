plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.remusic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.remusic"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Tempat naruh library pihak ke 3
    // Navigasi antar screen di Jetpack Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")
    // Library inti pemutar audio/video (ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    // Kontrol media seperti play/pause lewat MediaSession (notif, headset, lockscreen)
    implementation("androidx.media3:media3-session:1.8.0")
    // UI komponen player khusus Jetpack Compose (progress bar, tombol, dsb)
    implementation("androidx.media3:media3-ui-compose:1.8.0")
    // Untuk streaming audio/video dari internet (via HTTP/HTTPS)
    implementation("androidx.media3:media3-datasource-okhttp:1.8.0")
    // Menyimpan data lokal seperti setting, tema, atau last played (pengganti SharedPreferences)
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    // Mengelola state global (seperti player state) dengan ViewModel di Jetpack Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    // Tambahan ikon bawaan Material Design (play, pause, next, dll)
    implementation("androidx.compose.material:material-icons-extended")
}