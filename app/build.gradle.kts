plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1"
    id("kotlin-parcelize")
    kotlin("plugin.serialization") version "2.1.0"
}

android {
    namespace = "com.example.remusic"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.remusic"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ambil dari gradle/local.properties
        buildConfigField("String", "SUPABASE_URL", "\"${project.findProperty("SUPABASE_URL") ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${project.findProperty("SUPABASE_ANON_KEY") ?: ""}\"")
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"${project.findProperty("GOOGLE_SERVER_CLIENT_ID") ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            // Biasanya proguardFiles tidak diperlukan untuk debug
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
        buildConfig = true
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
    implementation(libs.androidx.foundation.layout.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Tempat naruh library pihak ke 3
    // Navigasi antar screen di Jetpack Compose
    implementation(libs.androidx.navigation.compose)
    // Library inti pemutar audio/video (ExoPlayer)
    implementation(libs.androidx.media3.exoplayer)
    // Kontrol media seperti play/pause lewat MediaSession (notif, headset, lockscreen)
    implementation(libs.androidx.media3.session)
    // UI komponen player khusus Jetpack Compose (progress bar, tombol, dsb)
    implementation(libs.androidx.media3.ui.compose)
    // Untuk streaming audio/video dari internet (via HTTP/HTTPS)
    implementation("androidx.media3:media3-datasource-okhttp:1.9.0")
    // Menyimpan data lokal seperti setting, tema, atau last played (pengganti SharedPreferences)
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    // Mengelola state global (seperti player state) dengan ViewModel di Jetpack Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    // Tambahan ikon bawaan Material Design (play, pause, next, dll)
    implementation("androidx.compose.material:material-icons-extended")
    //ambil gambar dari http
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:34.1.0"))
    // (Opsional) Firebase Analytics
     implementation("com.google.firebase:firebase-analytics")
    // Dependensi untuk Firebase Authentication
    implementation("com.google.firebase:firebase-auth")
    // Dependensi untuk Firebase Firestore Database
    implementation("com.google.firebase:firebase-firestore")

    // --- GOOGLE CREDENTIAL MANAGER (Wajib untuk Google Sign In) ---
    implementation("androidx.credentials:credentials:1.5.0") // Gunakan versi stabil/terbaru jika ada
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Tambahkan Supabase BOM (Bill of Materials)
    implementation(platform("io.github.jan-tennert.supabase:bom:3.2.6"))
    // Tambahkan modul-modul Supabase yang dibutuhkan
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    //    implementation("io.github.jan-tennert.supabase:postgrest-kt")

    implementation("com.google.code.gson:gson:2.13.2")

    implementation("com.github.yalantis:ucrop:2.2.10")
    implementation("io.ktor:ktor-client-okhttp:3.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation("androidx.palette:palette:1.0.0")
    implementation("androidx.mediarouter:mediarouter:1.8.1")
}