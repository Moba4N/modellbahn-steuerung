import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core:ui-shell"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "de.moba.desktop.MainKt"
        nativeDistributions {
            // Windows-Installer; die Java-Laufzeit wird mitgeliefert.
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Modellbahn-Steuerung"
            packageVersion = "0.1.0"
            windows {
                menu = true
                shortcut = true
            }
        }
    }
}
