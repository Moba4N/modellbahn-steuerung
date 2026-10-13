package de.moba.adapters.simulator

import de.moba.core.station.CommandStation
import de.moba.core.station.CvException
import de.moba.core.station.CvProgrammer
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
) : CommandStation, CvProgrammer {
    private val _events = MutableSharedFlow<StationEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<StationEvent> = _events

    private val locos = mutableMapOf<Int, LocoState>()
    private val turnouts = mutableMapOf<Int, Boolean>()
    private val occupied = mutableSetOf<Pair<Int, Int>>()

    /** Virtueller Decoder auf dem Programmiergleis, CV-Nummer zu Wert. */
    val programmingTrack = defaultDecoder().toMutableMap()

    /** Steht eine Lok auf dem Programmiergleis? Ohne Lok antwortet kein Decoder. */
    var decoderOnProgrammingTrack = true

    /** Waehrend der Fahrt geschriebene CVs je Lokadresse. */
    val mainTrackCvs = mutableMapOf<Int, MutableMap<Int, Int>>()

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

    override suspend fun readCv(cv: Int): Int {
        checkCv(cv, 0)
        if (!decoderOnProgrammingTrack) throw CvException("Der Decoder antwortet nicht (CV $cv). Steht die Lok auf dem Programmiergleis?")
        return programmingTrack[cv] ?: 0
    }

    override suspend fun writeCv(cv: Int, value: Int) {
        checkCv(cv, value)
        if (!decoderOnProgrammingTrack) throw CvException("Der Decoder antwortet nicht (CV $cv). Steht die Lok auf dem Programmiergleis?")
        // CV 8 = 8 setzt viele Decoder auf Werkseinstellung zurueck.
        if (cv == 8 && value == 8) {
            programmingTrack.clear()
            programmingTrack.putAll(defaultDecoder())
        } else if (cv !in READ_ONLY_CVS) {
            programmingTrack[cv] = value
        }
    }

    override suspend fun writeCvOnMain(address: Int, cv: Int, value: Int) {
        checkCv(cv, value)
        mainTrackCvs.getOrPut(address) { mutableMapOf() }[cv] = value
    }

    private fun checkCv(cv: Int, value: Int) {
        require(cv in 1..1024) { "CV muss zwischen 1 und 1024 liegen" }
        require(value in 0..255) { "Ein CV-Wert liegt zwischen 0 und 255" }
    }

    /** Simuliert einen s88-Belegtmelder, als faehrt ein Zug in den Abschnitt ein oder aus. */
    suspend fun simulateFeedback(module: Int, contact: Int, occupied: Boolean) {
        require(module in 1..s88Modules) { "s88-Modul $module gibt es nicht (1 bis $s88Modules)" }
        require(contact in 1..16) { "Ein s88-Modul hat die Kontakte 1 bis 16" }
        if (occupied) this.occupied += module to contact else this.occupied -= module to contact
        _events.emit(StationEvent.FeedbackChanged(module, contact, occupied))
    }

    companion object {
        /** CV 7 (Version) und CV 8 (Hersteller) lassen sich nur lesen. */
        val READ_ONLY_CVS = setOf(7, 8)

        /** Werkseinstellung des virtuellen Decoders: Adresse 3, 28/128 Fahrstufen. */
        fun defaultDecoder() = mapOf(1 to 3, 2 to 1, 3 to 8, 4 to 6, 5 to 255, 6 to 85, 7 to 42, 8 to 151, 17 to 192, 18 to 3, 29 to 6)
    }
}
