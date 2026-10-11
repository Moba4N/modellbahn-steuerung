package de.moba.adapters.xpressnet

import de.moba.adapters.net.ByteStream
import de.moba.adapters.net.StreamStation
import de.moba.adapters.net.feedbackEvent
import de.moba.core.station.Direction
import de.moba.core.station.StationEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Adapter fuer Lenz-Zentralen (LZV100, LZV200) und andere XpressNet-Zentralen am
 * Lenz LAN/USB-Interface 23151 (TCP 5550).
 *
 * XpressNet meldet Fahrbefehle nicht von selbst zurueck. Der Adapter fragt deshalb nach jedem
 * Fahr- oder Funktionsbefehl den Lokzustand ab und meldet erst die Antwort der Zentrale.
 * Rueckmelder (z. B. LR101 mit 8 Eingaengen je Adresse) werden auf Module zu 16 Kontakten umgerechnet.
 */
class XpressNetStation(
    name: String,
    streamFactory: suspend () -> ByteStream,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    connectTimeoutMillis: Long = 3_000,
    private val keepAliveMillis: Long = 30_000,
    private val turnoutPulseMillis: Long = 100,
) : StreamStation(name, streamFactory, dispatcher, connectTimeoutMillis) {
    private var pending = ByteArray(0)

    /** Die Antwort auf eine Lokabfrage enthaelt keine Adresse; sie kommen in Abfrage-Reihenfolge. */
    private val infoLock = Mutex()
    private val infoRequests = ArrayDeque<Int>()
    private val feedbackNibbles = mutableMapOf<Int, Int>()

    override suspend fun handshake() = write(XpressNetCodec.statusRequest())

    override suspend fun afterConnect() {
        scope?.launch {
            while (isActive) {
                delay(keepAliveMillis)
                runCatching { write(XpressNetCodec.statusRequest()) }
            }
        }
    }

    override fun reset() {
        pending = ByteArray(0)
        infoRequests.clear()
        feedbackNibbles.clear()
    }

    override suspend fun setTrackPower(on: Boolean) =
        write(if (on) XpressNetCodec.trackPowerOn() else XpressNetCodec.trackPowerOff())

    override suspend fun emergencyStop() = write(XpressNetCodec.stopAll())

    override suspend fun setLocoSpeed(address: Int, speed: Int, direction: Direction) {
        require(speed in 0..126) { "Fahrstufe muss zwischen 0 und 126 liegen" }
        write(XpressNetCodec.drive(address, speed, forward = direction == Direction.FORWARD))
        requestInfo(address)
    }

    override suspend fun setLocoFunction(address: Int, function: Int, on: Boolean) {
        val current = knownLoco(address)?.functions ?: emptySet()
        val wanted = if (on) current + function else current - function
        write(XpressNetCodec.functionGroup(address, function, wanted))
        if (function > 12) {
            // F13 bis F28 liefert die einfache Lokabfrage nicht zurueck.
            confirmLoco(address, function = function to on)
        } else {
            requestInfo(address)
        }
    }

    override suspend fun setTurnout(address: Int, thrown: Boolean) {
        write(XpressNetCodec.accessory(address, thrown, activate = true))
        delay(turnoutPulseMillis)
        write(XpressNetCodec.accessory(address, thrown, activate = false))
    }

    private suspend fun requestInfo(address: Int) = infoLock.withLock {
        infoRequests.addLast(address)
        write(XpressNetCodec.locoInfo(address))
    }

    override suspend fun onBytes(bytes: ByteArray) {
        val (messages, rest) = XpressNetCodec.split(pending + bytes)
        pending = rest
        messages.forEach { handle(it) }
    }

    private suspend fun handle(m: IntArray) {
        markReady()
        val header = m[0]
        when {
            // Broadcasts: Normalbetrieb, Gleisspannung aus, Nothalt
            header == 0x61 && m.size >= 2 -> when (m[1]) {
                0x01 -> eventFlow.emit(StationEvent.TrackPowerChanged(true))
                0x00 -> eventFlow.emit(StationEvent.TrackPowerChanged(false))
            }
            header == 0x81 && m.size >= 2 && m[1] == 0x00 -> eventFlow.emit(StationEvent.EmergencyStopped)
            // Zentralenstatus: Bit 0 = Gleisspannung aus, Bit 1 = Nothalt
            header == 0x62 && m.size >= 3 && m[1] == 0x22 -> {
                eventFlow.emit(StationEvent.TrackPowerChanged(m[2] and 0x01 == 0))
                if (m[2] and 0x02 != 0) eventFlow.emit(StationEvent.EmergencyStopped)
            }
            // Lokinformation: E4 ID SPD FKTA FKTB
            header == 0xE4 && m.size >= 5 -> {
                val address = infoLock.withLock { infoRequests.removeFirstOrNull() } ?: return
                handleLocoInfo(address, m[1], m[2], m[3], m[4])
            }
            // Lok wird jetzt von einem anderen Handregler gesteuert: Zustand neu holen.
            header == 0xE3 && m.size >= 4 && m[1] == 0x40 -> {
                val address = ((m[2] and 0x3F) shl 8) or m[3]
                requestInfo(address)
            }
            // Schaltinformationen von Weichen und Rueckmeldern: Paare aus Adresse und Daten
            header and 0xF0 == 0x40 -> {
                var i = 1
                while (i + 1 < m.size) {
                    handleAccessoryInfo(m[i], m[i + 1])
                    i += 2
                }
            }
        }
    }

    private suspend fun handleLocoInfo(address: Int, id: Int, speedByte: Int, fa: Int, fb: Int) {
        val wire = speedByte and 0x7F
        val speed = when (id and 0x07) {
            4 -> if (wire <= 1) 0 else wire - 1 // 128 Fahrstufen
            else -> (wire * 126 / 127) // andere Stufen nur grob umgerechnet
        }
        val functions = buildSet {
            if (fa and 0x10 != 0) add(0)
            for (i in 0..3) if (fa and (1 shl i) != 0) add(1 + i)
            for (i in 0..7) if (fb and (1 shl i) != 0) add(5 + i)
        }
        confirmLoco(address) { old ->
            old.copy(
                speed = speed,
                direction = if (speedByte and 0x80 != 0) Direction.FORWARD else Direction.REVERSE,
                functions = old.functions.filter { it > 12 }.toSet() + functions,
            )
        }
    }

    private suspend fun handleAccessoryInfo(address: Int, data: Int) {
        val type = (data shr 5) and 0x03
        val upper = (data shr 4) and 0x01
        val bits = data and 0x0F
        if (type == 2) {
            // Rueckmeldemodul: je Adresse 8 Eingaenge, Nibble 0 = Eingang 1 bis 4.
            val key = address * 2 + upper
            val previous = feedbackNibbles[key]
            feedbackNibbles[key] = bits
            val changed = if (previous == null) bits else previous xor bits
            for (i in 0..3) {
                if (changed and (1 shl i) != 0) {
                    eventFlow.emit(feedbackEvent(address * 8 + upper * 4 + i, occupied = bits and (1 shl i) != 0))
                }
            }
        } else {
            // Weichen: zwei je Nibble, 01 = gerade, 10 = abzweigend.
            val first = address * 4 + upper * 2 + 1
            for (n in 0..1) {
                when ((bits shr (n * 2)) and 0x03) {
                    1 -> eventFlow.emit(StationEvent.TurnoutChanged(first + n, thrown = false))
                    2 -> eventFlow.emit(StationEvent.TurnoutChanged(first + n, thrown = true))
                }
            }
        }
    }
}
