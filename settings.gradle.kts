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
    ":tools:sandbox",
)

// Oberflaeche und Apps brauchen Compose und das Android SDK.
// Mit -Pmoba.apps=false laesst sich nur die Logik bauen (z. B. ohne Android SDK).
if (providers.gradleProperty("moba.apps").getOrElse("true").toBoolean()) {
    include(
        ":core:ui-shell",
        ":modules:driving:ui",
        ":apps:desktop",
        ":apps:android",
    )
}
