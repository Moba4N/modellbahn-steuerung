// Alle Plugins einmal hier deklarieren, damit sie im selben Classloader liegen.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}

allprojects {
    // Gruppe nach Ordner, damit gleichnamige Module (z. B. driving:ui und trackplan:ui) nicht verwechselt werden.
    group = "de.moba" + path.substringBeforeLast(':').replace(':', '.')
    version = "0.1.0"
}
