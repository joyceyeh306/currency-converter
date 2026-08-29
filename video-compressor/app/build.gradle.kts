plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.joyce.videocompressor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.joyce.videocompressor"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.media3:media3-transformer:1.8.1")
    implementation("androidx.media3:media3-effect:1.8.1")
    implementation("androidx.media3:media3-common:1.8.1")
    implementation("net.qiujuer.lame:lame:1.0.0")
}
