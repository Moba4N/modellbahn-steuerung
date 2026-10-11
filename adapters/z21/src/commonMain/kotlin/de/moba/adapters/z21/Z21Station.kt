package de.moba.adapters.z21

import de.moba.core.station.CommandStation
import de.moba.core.station.Direction
import de.moba.core.station.LocoState
import de.moba.core.station.StationEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Verbindung zur Zentrale fehlgeschlagen oder abgebrochen. */
class Z21Exception(message: String) : Exception(message)

/** Sendet und empfaengt rohe UDP-Datagramme. Auf Android und Windows: [UdpZ21Transport]. */
interface Z21Transport {
    suspend fun send(packet: ByteArray)

    /** Wartet auf das naechste Datagramm. */
    suspend fun receive(): ByteArray
    fun close()
}

/**
 * Adapter fuer Zentralen mit Z21-LAN-Protokoll, z. B. YaMoRC YD7001 oder Roco z21.
 *
 * Gemeldet wird nur, was die Zentrale bestaetigt: Ein Fahrbefehl erzeugt erst dann ein
 * [StationEvent.LocoChanged], wenn die Zentrale die neue Lokinfo zurueckschickt.
 */
class Z21Station(
    override val name: String,
    private val transportFactory: () -> Z21Transport,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val connectTimeoutMillis: Long = 3_000,
    private val keepAliveMillis: Long = 30_000,
    private val turnoutPulseMillis: Long = 100,
) : CommandStation {
    private val _events = MutableSharedFlow<StationEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<StationEvent> = _events

    private var transport: Z21Transport? = null
    private var scope: CoroutineScope? = null
    private val subscribedLocos = mutableSetOf<Int>()
    private val rmBusInputs = mutableMapOf<Int, Int>()

    override suspend fun connect() {
        if (transport != null) return
        val t = transportFactory()
        val s = CoroutineScope(SupervisorJob() + dispatcher)
        val firstReply = CompletableDeferred<Unit>()
        transport = t
        scope = s

        s.launch {
            while (isActive) {
                val datagram = runCatching { t.receive() }.getOrNull() ?: break
                firstReply.complete(Unit)
                Z21Codec.decode(datagram).forEach { handle(it) }
            }
        }

        t.send(Z21Codec.setBroadcastFlags())
        t.send(Z21Codec.getSerialNumber())
        t.send(Z21Codec.getStatus())

        if (withTimeoutOrNull(connectTimeoutMillis) { firstReply.await() } == null) {
            disconnect()
            throw Z21Exception("$name antwortet nicht. Stimmt die IP-Adresse, und ist die Zentrale im selben Netz?")
        }
        _events.emit(StationEvent.ConnectionChanged(true))

        // Aktuellen Stand der Rueckmelder holen.
        t.send(Z21Codec.rmBusGetData(0))
        t.send(Z21Codec.rmBusGetData(1))

        // Die Zentrale meldet einen Client nach 60 s ohne Verkehr ab.
        s.launch {
            while (isActive) {
                delay(keepAliveMillis)
                runCatching { t.send(Z21Codec.systemStateGetData()) }
            }
        }
    }

    override suspend fun disconnect() {
        val t = transport ?: return
        runCatching { t.send(Z21Codec.logoff()) }
        scope?.cancel()
        t.close()
        transport = null
        scope = null
        subscribedLocos.clear()
        rmBusInputs.clear()
        _events.emit(StationEvent.ConnectionChanged(false))
    }

    override suspend fun setTrackPower(on: Boolean) =
        send(if (on) Z21Codec.trackPowerOn() else Z21Codec.trackPowerOff())

    override suspend fun emergencyStop() = send(Z21Codec.stopAll())

    override suspend fun setLocoSpeed(address: Int, speed: Int, direction: Direction) {
        subscribe(address)
        send(Z21Codec.setLocoDrive(address, speed, forward = direction == Direction.FORWARD))
    }

    override suspend fun setLocoFunction(address: Int, function: Int, on: Boolean) {
        subscribe(address)
        send(Z21Codec.setLocoFunction(address, function, on))
    }

    override suspend fun setTurnout(address: Int, thrown: Boolean) {
        send(Z21Codec.setTurnout(address, thrown, activate = true))
        delay(turnoutPulseMillis)
        send(Z21Codec.setTurnout(address, thrown, activate = false))
    }

    /** Lokinfo abfragen; die Zentrale schickt danach jede Aenderung dieser Lok. */
    private suspend fun subscribe(address: Int) {
        if (subscribedLocos.add(address)) send(Z21Codec.getLocoInfo(address))
    }

    private suspend fun send(packet: ByteArray) {
        val t = transport ?: throw Z21Exception("Nicht mit $name verbunden")
        t.send(packet)
    }

    private suspend fun handle(message: Z21Message) {
        when (message) {
            is Z21Message.TrackPower -> _events.emit(StationEvent.TrackPowerChanged(message.on))
            is Z21Message.Status -> _events.emit(StationEvent.TrackPowerChanged(!message.trackPowerOff))
            Z21Message.EmergencyStopped -> _events.emit(StationEvent.EmergencyStopped)
            Z21Message.ShortCircuit -> _events.emit(StationEvent.TrackPowerChanged(false))
            is Z21Message.TurnoutInfo -> _events.emit(StationEvent.TurnoutChanged(message.address, message.thrown))
            is Z21Message.LocoInfo -> _events.emit(
                StationEvent.LocoChanged(
                    LocoState(
                        address = message.address,
                        speed = message.speed,
                        direction = if (message.forward) Direction.FORWARD else Direction.REVERSE,
                        functions = message.functions,
                    ),
                ),
            )
            is Z21Message.RmBusModule -> handleRmBus(message)
            is Z21Message.LocoNetOccupancy -> {
                val index = message.address - 1
                _events.emit(StationEvent.FeedbackChanged(index / 16 + 1, index % 16 + 1, message.occupied))
            }
            is Z21Message.SerialNumber, Z21Message.SystemState -> Unit
        }
    }

    /** Meldet nur geaenderte Eingaenge; beim ersten Stand alle belegten. */
    private suspend fun handleRmBus(message: Z21Message.RmBusModule) {
        val previous = rmBusInputs[message.module]
        rmBusInputs[message.module] = message.inputs
        val changed = if (previous == null) message.inputs else previous xor message.inputs
        for (bit in 0..7) {
            if (changed and (1 shl bit) == 0) continue
            val s88Module = (message.module - 1) / 2 + 1
            val contact = ((message.module - 1) % 2) * 8 + bit + 1
            _events.emit(StationEvent.FeedbackChanged(s88Module, contact, message.inputs and (1 shl bit) != 0))
        }
    }
}
