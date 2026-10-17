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
            api(project(":core:station-api"))
            api(project(":modules:vehicles:api"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
