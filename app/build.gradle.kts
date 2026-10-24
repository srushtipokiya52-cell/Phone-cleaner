plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services) apply false
}

// Firebase needs app/google-services.json (from the Firebase console); build without it until added
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "com.phone.cleanerapp.storage.mediafiles"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.phone.cleanerapp.storage.mediafiles"
        minSdk = 24
        targetSdk = 36
        multiDexEnabled = true
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.work)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.coil)
    // Ads core
    implementation(libs.sdk.ads)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    implementation(libs.shimmer)
    // Remote Config overrides + analytics
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)
    implementation(libs.firebase.analytics)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.moshi.kotlin)
    implementation(libs.gson)
    implementation(libs.timber)
    implementation(libs.multidex)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
