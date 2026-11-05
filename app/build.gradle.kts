plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "app.stickermaker"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.stickermaker"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.github.yalantis:ucrop:2.2.8")

    // GIF/video -> sticker animado. Descomenta UNA línea con una build de ffmpeg-kit que exista
    // (la oficial de arthenica fue retirada; busca un fork comunitario vigente en Maven/JitPack).
    // El código lo usa por reflexión, así que el APK compila aunque esta línea siga comentada.
    // implementation("com.arthenica:ffmpeg-kit-full:6.0-2.LTS")
}
