package de.moba.core.runtime

/** Versionsnummer nach Semantic Versioning: Haupt.Neben.Fehlerbehebung. */
data class SemVer(val major: Int, val minor: Int = 0, val patch: Int = 0) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int =
        compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)

    /** Gleiche Hauptversion und mindestens so neu wie [required]. */
    fun satisfies(required: SemVer): Boolean = major == required.major && this >= required

    override fun toString() = "$major.$minor.$patch"

    companion object {
        fun parse(text: String): SemVer {
            val parts = text.trim().split('.').map { it.toIntOrNull() ?: error("Ungueltige Version: $text") }
            require(parts.size in 1..3) { "Ungueltige Version: $text" }
            return SemVer(parts[0], parts.getOrElse(1) { 0 }, parts.getOrElse(2) { 0 })
        }
    }
}

/** Ein Modul braucht ein anderes Modul mit passender API-Version. */
data class Dependency(val moduleId: String, val minApiVersion: SemVer)
