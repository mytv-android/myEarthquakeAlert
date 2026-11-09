import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

// Same signing as :app — the watch app shares the phone app's applicationId, and the
// Wearable Data Layer only pairs the two when both are signed with the same key.
val keystorePropertiesFile = rootProject.file("signing.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val localKeystore = rootProject.file("keystore.jks")
val userKeystore = project.file(
    System.getenv("KEYSTORE") ?: keystoreProperties.getProperty("storeFile")
        ?: "keystore.jks"
)
val releaseKeystore = when {
    userKeystore.exists() && userKeystore.length() > 0 -> userKeystore
    localKeystore.exists() && localKeystore.length() > 0 -> localKeystore
    else -> null
}

// versionName 与手机端一致固定为 1.0，以 versionCode（CI 运行号）区分版本
val appVersionName = "1.0"

android {
    namespace = "com.github.mytv.myearthquakealert.wear"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        // Must equal the phone app's applicationId (Data Layer pairing).
        applicationId = "com.github.mytv.myearthquakealert"
        minSdk = 30
        targetSdk = 36
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = appVersionName
    }

    signingConfigs {
        create("release") {
            storeFile = releaseKeystore
            storePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: keystoreProperties.getProperty("storePassword")
            keyAlias = System.getenv("KEY_ALIAS") ?: keystoreProperties.getProperty("keyAlias")
            keyPassword = System.getenv("KEY_PASSWORD") ?: keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (releaseKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.service)

    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)

    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.expression)
    implementation(libs.wear.protolayout.material3)
    implementation(libs.wear.complications.data.source.ktx)
    implementation(libs.wear.remote.interactions)
    implementation(libs.concurrent.futures.ktx)
    implementation(libs.graphics.shapes)

    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.osmdroid)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
