plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.openmultitrack.remote"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation(project(":domain"))
    implementation(libs.kotlinx.coroutines.android)
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("org.nanohttpd:nanohttpd:2.3.1")
    api("org.nanohttpd:nanohttpd-websocket:2.3.1")
    implementation("org.json:json:20240303")
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
