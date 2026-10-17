plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Короткий хеш коммита, из которого собрана APK, показывается в приложении рядом с версией.
val gitHash = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifEmpty { "nogit" } }.orElse("nogit")

// Release-ключ прописан в ~/.gradle/gradle.properties (android-setup.sh).
val releaseStoreFile = providers.gradleProperty("ANDROID_RELEASE_STORE_FILE").orNull

android {
    namespace = "app.tygdeblya"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.tygdeblya"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "0.3"
        buildConfigField("String", "GIT_HASH", "\"${gitHash.get()}\"")
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("ANDROID_RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("ANDROID_RELEASE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("ANDROID_RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.wtfay.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.splashscreen)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.coroutines.android)
    implementation(libs.maplibre)
    testImplementation(kotlin("test-junit"))
}
