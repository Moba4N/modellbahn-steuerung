package de.moba.adapters.net

import de.moba.core.station.CommandStation
import de.moba.core.station.Direction
import de.moba.core.station.LocoState
import de.moba.core.station.StationEvent
import de.moba.core.station.StationException
import de.moba.core.station.TrafficPacket
import de.moba.core.station.TrafficSource
import de.moba.core.station.asPrintableText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Gemeinsamer Rahmen fuer Zentralen an einer Byte-Verbindung (TCP, spaeter USB).
 *
 * Unterklassen senden in [handshake] eine erste Anfrage und rufen [markReady] auf, sobald die
 * Zentrale antwortet. Antwortet sie nicht innerhalb von [connectTimeoutMillis], schlaegt
 * [connect] fehl. Bricht die Verbindung ab, wird [StationEvent.ConnectionChanged] gemeldet.
 */
abstract class StreamStation(
    override val name: String,
    private val streamFactory: suspend () -> ByteStream,
    private val dispatcher: CoroutineDispatcher,
    private val connectTimeoutMillis: Long,
) : CommandStation, TrafficSource {
    protected val eventFlow = MutableSharedFlow<StationEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<StationEvent> = eventFlow

    private val trafficFlow = MutableSharedFlow<TrafficPacket>(extraBufferCapacity = 512)
    override val traffic: SharedFlow<TrafficPacket> = trafficFlow

    /** Lesbare Deutung eines Pakets fuer den Monitor; Standard: Klartext bei Textprotokollen. */
    protected open fun describe(bytes: ByteArray, outgoing: Boolean): String? = bytes.asPrintableText()

    private fun tap(bytes: ByteArray, outgoing: Boolean) {
        if (trafficFlow.subscriptionCount.value > 0) trafficFlow.tryEmit(TrafficPacket(outgoing, bytes, runCatching { describe(bytes, outgoing) }.getOrNull()))
    }

    private var stream: ByteStream? = null
    private var ready: CompletableDeferred<Unit>? = null

    /** Lebt so lange wie die Verbindung; fuer Hintergrundaufgaben der Unterklassen. */
    protected var scope: CoroutineScope? = null
        private set

    private val locos = MutableStateFlow<Map<Int, LocoState>>(emptyMap())

    override suspend fun connect() {
        if (stream != null) return
        val st = streamFactory()
        val s = CoroutineScope(SupervisorJob() + dispatcher)
        val r = CompletableDeferred<Unit>()
        stream = st
        scope = s
        ready = r

        s.launch {
            while (isActive) {
                val bytes = st.read() ?: break
                tap(bytes, outgoing = false)
                onBytes(bytes)
            }
            if (stream === st) {
                // Die Zentrale hat die Verbindung beendet.
                cleanUp(st, s)
                eventFlow.tryEmit(StationEvent.ConnectionChanged(false))
            }
        }

        handshake()
        if (withTimeoutOrNull(connectTimeoutMillis) { r.await() } == null) {
            disconnect()
            throw StationException("$name antwortet nicht. Stimmt die IP-Adresse, und ist die Zentrale im selben Netz?")
        }
        eventFlow.emit(StationEvent.ConnectionChanged(true))
        afterConnect()
    }

    override suspend fun disconnect() {
        val st = stream ?: return
        runCatching { beforeDisconnect() }
        cleanUp(st, scope)
        eventFlow.emit(StationEvent.ConnectionChanged(false))
    }

    private fun cleanUp(st: ByteStream, s: CoroutineScope?) {
        stream = null
        scope = null
        ready = null
        locos.value = emptyMap()
        reset()
        st.close()
        s?.cancel()
    }

    protected fun markReady() {
        ready?.complete(Unit)
    }

    protected suspend fun write(bytes: ByteArray) {
        val st = stream ?: throw StationException("Nicht mit $name verbunden")
        st.write(bytes)
        tap(bytes, outgoing = true)
    }

    protected suspend fun writeLine(line: String) = write("$line\n".encodeToByteArray())

    /** Zuletzt bestaetigter Zustand einer Lok, falls bekannt. */
    protected fun knownLoco(address: Int): LocoState? = locos.value[address]

    /** Uebernimmt eine Bestaetigung der Zentrale und meldet den vollstaendigen Lokzustand. */
    protected suspend fun confirmLoco(address: Int, change: (LocoState) -> LocoState) {
        val state = locos.updateAndGet { it + (address to change(it[address] ?: LocoState(address))) }.getValue(address)
        eventFlow.emit(StationEvent.LocoChanged(state))
    }

    protected suspend fun confirmLoco(address: Int, speed: Int? = null, direction: Direction? = null, function: Pair<Int, Boolean>? = null) =
        confirmLoco(address) { old ->
            old.copy(
                speed = speed ?: old.speed,
                direction = direction ?: old.direction,
                functions = function?.let { (f, on) -> if (on) old.functions + f else old.functions - f } ?: old.functions,
            )
        }

    protected abstract suspend fun handshake()
    protected abstract suspend fun onBytes(bytes: ByteArray)
    protected open suspend fun afterConnect() {}
    protected open suspend fun beforeDisconnect() {}

    /** Verbindungsbezogenen Zustand der Unterklasse verwerfen. */
    protected open fun reset() {}
}

/** Meldet von Modulen mit je 16 Eingaengen; [index] zaehlt ab 0 ueber alle Eingaenge. */
fun feedbackEvent(index: Int, occupied: Boolean) =
    StationEvent.FeedbackChanged(module = index / 16 + 1, contact = index % 16 + 1, occupied = occupied)
