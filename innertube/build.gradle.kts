plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization") version "2.1.0"
}

android {
    namespace = "com.metrolist.innertube"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("io.ktor:ktor-client-core:3.4.0")
    implementation("io.ktor:ktor-client-okhttp:3.4.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.4.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.0")
    implementation("io.ktor:ktor-client-encoding:3.4.0")
    implementation("org.brotli:dec:0.1.2")
    implementation("com.github.MetrolistGroup:MetrolistExtractor:6305155") {
        exclude(group = "com.google.protobuf")
    }
    implementation("com.jakewharton.timber:timber:5.0.1")
    testImplementation("junit:junit:4.13.2")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
