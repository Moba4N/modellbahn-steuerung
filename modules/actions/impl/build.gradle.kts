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
            api(project(":modules:actions:api"))
            implementation(project(":modules:driving:api"))
            implementation(project(":modules:trackplan:api"))
            implementation(project(":modules:routes:api"))
            implementation(project(":core:runtime"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":adapters:simulator"))
            implementation(project(":modules:driving:impl"))
            implementation(project(":modules:trackplan:impl"))
            implementation(project(":modules:routes:impl"))
            implementation(project(":modules:vehicles:impl"))
        }
    }
}
