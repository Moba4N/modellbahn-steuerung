import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "de.moba.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "de.moba.steuerung"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.compileSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core:ui-shell"))
    implementation(project(":modules:driving:ui"))
    implementation(project(":modules:driving:impl"))
    implementation(project(":modules:trackplan:ui"))
    implementation(project(":modules:trackplan:impl"))
    implementation(project(":adapters:catalog"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
}
