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
            api(project(":modules:throttles:api"))
            implementation(project(":core:runtime"))
            implementation(project(":modules:driving:api"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":adapters:simulator"))
            implementation(project(":modules:driving:impl"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
