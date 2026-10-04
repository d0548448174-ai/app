plugins {
    id("com.android.application")
}

android {
    namespace = "com.d0548448174ai.covermaker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.d0548448174ai.covermaker"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.mpatric:mp3agic:0.9.1")
}
