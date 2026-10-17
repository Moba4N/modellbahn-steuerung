import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":modules:routes:api"))
            implementation(project(":modules:calibration:api"))
            implementation(project(":modules:trackplan:api"))
            implementation(project(":core:runtime"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":adapters:simulator"))
            implementation(project(":modules:trackplan:impl"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
