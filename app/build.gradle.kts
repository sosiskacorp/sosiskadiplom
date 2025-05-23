plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.sosiso4kawo.zschoolapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sosiso4kawo.zschoolapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.gridlayout)
    
    // Retrofit for network requests
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    
    // Koin for dependency injection
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.navigation)
    
    // Coroutines for asynchronous programming
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.glide)
    implementation(libs.okhttp)
    implementation(libs.ucrop)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.exoplayer)
    implementation(libs.photoview)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    annotationProcessor(libs.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.junit) // JUnit 4
    testImplementation(libs.kotlinx.coroutines.test) // Coroutines testing
    testImplementation(libs.mockk) // MockK for mocking
    testImplementation(libs.androidx.core.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}