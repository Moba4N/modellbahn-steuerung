package de.moba.core.station

import kotlinx.coroutines.flow.SharedFlow

/** Ein Datenpaket zwischen App und Zentrale, so wie es ueber die Leitung geht. */
class TrafficPacket(
    /** true = von der App zur Zentrale, false = von der Zentrale zur App. */
    val outgoing: Boolean,
    val bytes: ByteArray,
    /** Lesbare Deutung, z. B. "Lok 3: Fahrstufe 64 vorwärts" oder der Klartext eines Textprotokolls; null = unbekannt. */
    val description: String? = null,
)

/**
 * Zentralen, deren Datenverkehr sich mitlesen laesst (fuer den Monitor). Pakete werden nur
 * weitergegeben, solange jemand zuhoert; langsame Zuhoerer verpassen eher Pakete, als die Verbindung zu bremsen.
 */
interface TrafficSource {
    val traffic: SharedFlow<TrafficPacket>
}

/** Bytes als Hex, z. B. "07 00 40 00 61 00 21". */
fun ByteArray.toHex(): String = joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }

/** Klartext, wenn die Bytes druckbarer Text sind (Textprotokolle wie ECoS oder DCC-EX), sonst null. */
fun ByteArray.asPrintableText(): String? {
    if (isEmpty()) return null
    val ok = all { b -> val c = b.toInt() and 0xFF; c == 9 || c == 10 || c == 13 || c in 32..126 }
    return if (ok) decodeToString().trim().ifEmpty { null } else null
}

/** Lesbare Beschreibung einer Meldung, z. B. "Lok 3: Fahrstufe 64 vorwärts, F0". */
fun StationEvent.describe(): String = when (this) {
    is StationEvent.ConnectionChanged -> if (connected) "Verbunden" else "Verbindung getrennt"
    is StationEvent.TrackPowerChanged -> if (on) "Gleisspannung ein" else "Gleisspannung aus"
    StationEvent.EmergencyStopped -> "Nothalt"
    is StationEvent.LocoChanged -> "Lok ${state.address}: Fahrstufe ${state.speed} ${if (state.direction == Direction.FORWARD) "vorwärts" else "rückwärts"}" +
        if (state.functions.isEmpty()) "" else ", " + state.functions.sorted().joinToString(" ") { "F$it" }
    is StationEvent.TurnoutChanged -> "Weiche $address ${if (thrown) "abzweigend" else "gerade"}"
    is StationEvent.FeedbackChanged -> "Melder $module.$contact ${if (occupied) "belegt" else "frei"}"
}
