import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    // Das JVM-Ziel wird von der Windows-App und von der Android-App genutzt.
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        jvmMain.dependencies {
            api(project(":core:station-api"))
            implementation(project(":adapters:net"))
            implementation(project(":adapters:simulator"))
            implementation(project(":adapters:z21"))
            implementation(project(":adapters:marklin-cs"))
            implementation(project(":adapters:ecos"))
            implementation(project(":adapters:dccex"))
            implementation(project(":adapters:xpressnet"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
