package de.moba.adapters.simulator

import de.moba.core.station.CommandStation
import de.moba.core.station.Direction
import de.moba.core.station.LocoState
import de.moba.core.station.StationEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Virtuelle Zentrale ohne Hardware. Bestaetigt jeden Befehl sofort ueber [events],
 * so wie es eine echte Zentrale tut. Belegtmelder lassen sich mit [simulateFeedback] ausloesen.
 */
class SimulatorStation(
    override val name: String = "Simulator",
    private val s88Modules: Int = 4,
) : CommandStation {
    private val _events = MutableSharedFlow<StationEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<StationEvent> = _events

    private val locos = mutableMapOf<Int, LocoState>()
    private val turnouts = mutableMapOf<Int, Boolean>()
    private val occupied = mutableSetOf<Pair<Int, Int>>()

    var connected = false
        private set
    var trackPower = false
        private set

    fun loco(address: Int): LocoState = locos[address] ?: LocoState(address)
    fun turnout(address: Int): Boolean = turnouts[address] ?: false
    fun isOccupied(module: Int, contact: Int): Boolean = (module to contact) in occupied

    override suspend fun connect() {
        connected = true
        _events.emit(StationEvent.ConnectionChanged(true))
    }

    override suspend fun disconnect() {
        connected = false
        _events.emit(StationEvent.ConnectionChanged(false))
    }

    override suspend fun setTrackPower(on: Boolean) {
        trackPower = on
        _events.emit(StationEvent.TrackPowerChanged(on))
    }

    override suspend fun emergencyStop() {
        locos.replaceAll { _, state -> state.copy(speed = 0) }
        _events.emit(StationEvent.EmergencyStopped)
        locos.values.forEach { _events.emit(StationEvent.LocoChanged(it)) }
    }

    override suspend fun setLocoSpeed(address: Int, speed: Int, direction: Direction) {
        require(speed in 0..126) { "Fahrstufe muss zwischen 0 und 126 liegen" }
        val state = loco(address).copy(speed = speed, direction = direction)
        locos[address] = state
        _events.emit(StationEvent.LocoChanged(state))
    }

    override suspend fun setLocoFunction(address: Int, function: Int, on: Boolean) {
        val current = loco(address)
        val state = current.copy(functions = if (on) current.functions + function else current.functions - function)
        locos[address] = state
        _events.emit(StationEvent.LocoChanged(state))
    }

    override suspend fun setTurnout(address: Int, thrown: Boolean) {
        turnouts[address] = thrown
        _events.emit(StationEvent.TurnoutChanged(address, thrown))
    }

    /** Simuliert einen s88-Belegtmelder, als faehrt ein Zug in den Abschnitt ein oder aus. */
    suspend fun simulateFeedback(module: Int, contact: Int, occupied: Boolean) {
        require(module in 1..s88Modules) { "s88-Modul $module gibt es nicht (1 bis $s88Modules)" }
        require(contact in 1..16) { "Ein s88-Modul hat die Kontakte 1 bis 16" }
        if (occupied) this.occupied += module to contact else this.occupied -= module to contact
        _events.emit(StationEvent.FeedbackChanged(module, contact, occupied))
    }
}
