package de.moba.core.station

/**
 * Was der Nutzer zum Verbinden angibt: welcher Treiber, wohin, mit welchen Zusatzeinstellungen.
 * [port] null bedeutet den Standardport des Treibers.
 */
data class StationConfig(
    val driverId: String,
    val host: String = "",
    val port: Int? = null,
    val options: Map<String, String> = emptyMap(),
)

/** Eine Zusatzeinstellung mit festen Werten, z. B. das Lokprotokoll bei Maerklin. */
data class DriverOption(
    val key: String,
    val label: String,
    /** Wert auf dem Draht zu Anzeigetext; der erste Eintrag ist der Standard. */
    val choices: List<Pair<String, String>>,
) {
    val default: String get() = choices.first().first
}

/**
 * Beschreibt eine Art Zentrale fuer den Verbindungsdialog und erzeugt den passenden Adapter.
 * Alle Treiber sind kostenlos; ein Treiber braucht keine Lizenz.
 */
interface StationDriver {
    /** Stabile Kennung, wird in den Einstellungen gespeichert, z. B. "z21" oder "ecos". */
    val id: String

    /** Anzeigename, z. B. "ESU ECoS". */
    val title: String

    /** Welche Geraete dazu passen, z. B. "ECoS 1 und 2, Maerklin CS1 Reloaded". */
    val devices: String

    /** Braucht der Treiber eine IP-Adresse? Nur der Simulator nicht. */
    val needsHost: Boolean get() = true

    val defaultPort: Int? get() = null

    val options: List<DriverOption> get() = emptyList()

    fun create(config: StationConfig): CommandStation
}

/** Eine im Netz gefundene Zentrale. */
data class FoundStation(val driverId: String, val host: String, val name: String)

/** Sucht Zentralen im lokalen Netz, die sich selbst melden (Z21, Maerklin CS). */
fun interface StationDiscovery {
    suspend fun discover(): List<FoundStation>
}

/** Verbindung zur Zentrale fehlgeschlagen oder abgebrochen. */
class StationException(message: String) : Exception(message)
