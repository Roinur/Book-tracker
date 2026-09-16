import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.roinur.booktracker"
    compileSdk = 34
    testBuildType = "release"

    val localKeystoreProperties = Properties().apply {
        val propertiesFile = rootProject.file("keystore.properties")
        if (propertiesFile.exists()) propertiesFile.inputStream().use(::load)
    }
    val releaseStoreFile = localKeystoreProperties.getProperty("storeFile")
        ?: (findProperty("BOOK_TRACKER_RELEASE_STORE_FILE") as String?)
        ?: "book-tracker-release.keystore"
    val releaseStorePassword = localKeystoreProperties.getProperty("storePassword")
        ?: (findProperty("BOOK_TRACKER_RELEASE_STORE_PASSWORD") as String?)
        ?: ""
    val releaseKeyAlias = localKeystoreProperties.getProperty("keyAlias")
        ?: (findProperty("BOOK_TRACKER_RELEASE_KEY_ALIAS") as String?)
        ?: ""
    val releaseKeyPassword = localKeystoreProperties.getProperty("keyPassword")
        ?: (findProperty("BOOK_TRACKER_RELEASE_KEY_PASSWORD") as String?)
        ?: ""

    defaultConfig {
        applicationId = "com.roinur.booktracker"
        minSdk = 26
        targetSdk = 34
        versionCode = 48
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(releaseStoreFile)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-roinur-debug"
            resValue("string", "app_name", "Book Tracker · Roinur debug")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            // Keep release stable while features iterate; avoid R8/resource shrink regressions.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
