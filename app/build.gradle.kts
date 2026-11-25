plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    namespace = "app.pageheld"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "app.pageheld"
        minSdk = 28
        targetSdk = 36
        versionCode = 8
        versionName = "1.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "edition"
    productFlavors {
        create("phone") { dimension = "edition" }
        create("tablet") { dimension = "edition" }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
dependencies {
    implementation("com.google.crypto.tink:tink-android:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.room:room-runtime:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    implementation("org.maplibre.gl:android-sdk-opengl:13.6.1")
}
