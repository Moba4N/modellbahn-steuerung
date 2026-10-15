package de.moba.modules.monitor.api

import kotlinx.coroutines.flow.StateFlow

const val MONITOR_MODULE_ID = "moba.monitor"

/** Woher ein Eintrag stammt. */
enum class MonitorKind(val label: String) {
    SENT("Gesendet"),
    RECEIVED("Empfangen"),

    /** Was die App aus den empfangenen Daten verstanden hat (Lok, Weiche, Melder). */
    EVENT("Meldung"),
}

/** Eine Zeile im Monitor. [millis] zaehlt ab dem Start des Monitors. */
data class MonitorEntry(
    val seq: Long,
    val millis: Long,
    val kind: MonitorKind,
    /** Rohdaten als Hex; leer bei Meldungen. */
    val hex: String,
    val text: String?,
)

/**
 * Oeffentliche Schnittstelle des Moduls "Monitor": zeigt den Datenverkehr zwischen App und Zentrale.
 * Behaelt die letzten [MAX_ENTRIES] Eintraege.
 */
interface MonitorService {
    val entries: StateFlow<List<MonitorEntry>>
    val paused: StateFlow<Boolean>

    /** Liefert die Zentrale ihre Rohdaten? Sonst zeigt der Monitor nur Meldungen. */
    val rawAvailable: Boolean

    fun setPaused(paused: Boolean)
    fun clear()

    /** Alle Eintraege als Text, z. B. zum Kopieren in einen Fehlerbericht. */
    fun export(kinds: Set<MonitorKind> = MonitorKind.entries.toSet()): String

    companion object {
        const val MAX_ENTRIES = 2000
    }
}

/** Zeit als "12.345 s" bzw. "1:02.345". */
fun formatMonitorTime(millis: Long): String {
    val s = millis / 1000
    val ms = (millis % 1000).toString().padStart(3, '0')
    return if (s < 60) "$s.$ms s" else "${s / 60}:${(s % 60).toString().padStart(2, '0')}.$ms"
}
