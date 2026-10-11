package de.moba.core.station

import kotlinx.coroutines.flow.SharedFlow

/**
 * Einheitlicher Befehlssatz fuer jede Digitalzentrale.
 *
 * Fachmodule kennen nur diese Schnittstelle. Je Zentrale (Z21-Protokoll, LocoNet, Simulator ...)
 * gibt es einen Adapter, der sie umsetzt. Angezeigt wird immer der Zustand, den die Zentrale
 * ueber [events] bestaetigt, nicht der zuletzt gesendete Befehl.
 */
interface CommandStation {
    /** Anzeigename, z. B. "YaMoRC YD7001 (Z21)". */
    val name: String

    /** Rueckmeldungen der Zentrale: Lokzustaende, Weichen, Belegtmelder, Gleisspannung. */
    val events: SharedFlow<StationEvent>

    suspend fun connect()
    suspend fun disconnect()

    suspend fun setTrackPower(on: Boolean)

    /** Nothalt: alle Loks sofort anhalten. Muss immer funktionieren, unabhaengig von Lizenzen. */
    suspend fun emergencyStop()

    /** [speed] von 0 (Halt) bis 126 (Hoechstgeschwindigkeit, 128 Fahrstufen). */
    suspend fun setLocoSpeed(address: Int, speed: Int, direction: Direction)
    suspend fun setLocoFunction(address: Int, function: Int, on: Boolean)

    suspend fun setTurnout(address: Int, thrown: Boolean)
}

enum class Direction { FORWARD, REVERSE }

data class LocoState(
    val address: Int,
    val speed: Int = 0,
    val direction: Direction = Direction.FORWARD,
    val functions: Set<Int> = emptySet(),
)

sealed interface StationEvent {
    data class ConnectionChanged(val connected: Boolean) : StationEvent
    data class TrackPowerChanged(val on: Boolean) : StationEvent
    data object EmergencyStopped : StationEvent
    data class LocoChanged(val state: LocoState) : StationEvent
    data class TurnoutChanged(val address: Int, val thrown: Boolean) : StationEvent

    /** Belegtmelder, z. B. s88: [module] ab 1, [contact] 1 bis 16. */
    data class FeedbackChanged(val module: Int, val contact: Int, val occupied: Boolean) : StationEvent
}
