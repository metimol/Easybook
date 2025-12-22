import org.gradle.kotlin.dsl.annotationProcessor
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.crashlytics)
}

android {
    namespace = "com.metimol.easybook"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.metimol.easybook"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "1.3.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")

        if (localPropertiesFile.exists()) {
            properties.load(FileInputStream(localPropertiesFile))
        }

        val dbUrl = properties.getProperty("FIREBASE_DB_URL") ?: "\"https://placeholder-url\""
        val yandexAuthUrl = properties.getProperty("YANDEX_AUTH_BACKEND_URL") ?: "\"https://placeholder-url\""
        val yandexClientId = properties.getProperty("YANDEX_CLIENT_ID") ?: ""
        val audiobooksBaseUrl = properties.getProperty("AUDIOBOOKS_BASE_URL") ?: "\"https://placeholder-url\""
        val audiobooksAnonKey = properties.getProperty("AUDIOBOOKS_ANON_KEY") ?: ""

        buildConfigField("String", "FIREBASE_DB_URL", dbUrl)
        buildConfigField("String", "YANDEX_AUTH_BACKEND_URL", yandexAuthUrl)
        buildConfigField("String", "AUDIOBOOKS_BASE_URL", audiobooksBaseUrl)
        buildConfigField("String", "AUDIOBOOKS_ANON_KEY", "\"" + audiobooksAnonKey + "\"")

        manifestPlaceholders["YANDEX_CLIENT_ID"] = yandexClientId
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
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
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.navigation.fragment)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.cardview)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)
    implementation(libs.glide)
    annotationProcessor(libs.compiler)
    implementation(libs.swiperefreshlayout)
    implementation(libs.media3.datasource.okhttp)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.play.services.auth)
    implementation(libs.circleimageview)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.media3.ui)
    implementation(libs.media.compat)
    implementation(libs.yandex.authsdk)
}