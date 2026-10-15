rootProject.name = "modellbahn-steuerung"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

// Kern und Adapter: reine Kotlin-Logik, laeuft auf Android und Windows (JVM).
include(
    ":core:model",
    ":core:station-api",
    ":core:runtime",
    ":adapters:simulator",
    ":adapters:net",
    ":adapters:z21",
    ":adapters:marklin-cs",
    ":adapters:ecos",
    ":adapters:dccex",
    ":adapters:xpressnet",
    ":adapters:catalog",
    ":modules:driving:api",
    ":modules:driving:impl",
    ":modules:trackplan:api",
    ":modules:trackplan:impl",
    ":modules:vehicles:api",
    ":modules:vehicles:impl",
    ":modules:decoder:api",
    ":modules:decoder:impl",
    ":modules:symbols:api",
    ":modules:symbols:impl",
    ":modules:throttles:api",
    ":modules:throttles:impl",
    ":modules:calibration:api",
    ":modules:calibration:impl",
    ":modules:routes:api",
    ":modules:routes:impl",
    ":modules:monitor:api",
    ":modules:monitor:impl",
    ":tools:sandbox",
    ":tools:license",
)

// Oberflaeche und Apps brauchen Compose und das Android SDK.
// Mit -Pmoba.apps=false laesst sich nur die Logik bauen (z. B. ohne Android SDK).
if (providers.gradleProperty("moba.apps").getOrElse("true").toBoolean()) {
    include(
        ":core:ui-shell",
        ":modules:driving:ui",
        ":modules:trackplan:ui",
        ":modules:vehicles:ui",
        ":modules:decoder:ui",
        ":modules:symbols:ui",
        ":modules:throttles:ui",
        ":modules:calibration:ui",
        ":modules:routes:ui",
        ":modules:monitor:ui",
        ":apps:desktop",
        ":apps:android",
    )
}
