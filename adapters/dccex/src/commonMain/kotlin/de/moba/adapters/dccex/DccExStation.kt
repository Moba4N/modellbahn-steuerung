package de.moba.adapters.dccex

import de.moba.adapters.net.ByteStream
import de.moba.adapters.net.StreamStation
import de.moba.adapters.net.feedbackEvent
import de.moba.core.station.Direction
import de.moba.core.station.StationEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Zerlegt den Strom der DCC-EX in Nachrichten der Form `<...>`, auch ohne Zeilenumbrueche. */
class DccExSplitter {
    private val pending = StringBuilder()

    fun feed(bytes: ByteArray): List<String> {
        pending.append(bytes.decodeToString())
        val messages = mutableListOf<String>()
        while (true) {
            val start = pending.indexOf('<')
            if (start < 0) {
                pending.clear()
                break
            }
            val end = pending.indexOf('>', start)
            if (end < 0) {
                pending.deleteRange(0, start)
                break
            }
            messages += pending.substring(start + 1, end).trim()
            pending.deleteRange(0, end + 1)
        }
        return messages
    }
}

/**
 * Adapter fuer DCC-EX (Arduino-Eigenbau) ueber WLAN/LAN (TCP, Standardport 2560).
 *
 * Loks bestaetigt die DCC-EX mit `<l ...>`. Fuer Nothalt und Weichen schickt sie keine Bestaetigung;
 * diese meldet der Adapter, sobald der Befehl gesendet ist.
 */
class DccExStation(
    name: String,
    streamFactory: suspend () -> ByteStream,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    connectTimeoutMillis: Long = 3_000,
) : StreamStation(name, streamFactory, dispatcher, connectTimeoutMillis) {
    private val splitter = DccExSplitter()

    override suspend fun handshake() = send("s")

    override suspend fun setTrackPower(on: Boolean) = send(if (on) "1" else "0")

    override suspend fun emergencyStop() {
        send("!")
        eventFlow.emit(StationEvent.EmergencyStopped)
    }

    override suspend fun setLocoSpeed(address: Int, speed: Int, direction: Direction) {
        require(speed in 0..126) { "Fahrstufe muss zwischen 0 und 126 liegen" }
        send("t $address $speed ${if (direction == Direction.FORWARD) 1 else 0}")
    }

    override suspend fun setLocoFunction(address: Int, function: Int, on: Boolean) =
        send("F $address $function ${if (on) 1 else 0}")

    override suspend fun setTurnout(address: Int, thrown: Boolean) {
        send("a $address ${if (thrown) 1 else 0}")
        eventFlow.emit(StationEvent.TurnoutChanged(address, thrown))
    }

    private suspend fun send(command: String) = write("<$command>".encodeToByteArray())

    override suspend fun onBytes(bytes: ByteArray) {
        for (message in splitter.feed(bytes)) handle(message)
    }

    private suspend fun handle(message: String) {
        if (message.isEmpty()) return
        markReady()
        val parts = message.substring(1).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        when (message[0]) {
            'p' -> when (parts.firstOrNull()) {
                "1" -> eventFlow.emit(StationEvent.TrackPowerChanged(true))
                "0" -> eventFlow.emit(StationEvent.TrackPowerChanged(false))
            }
            'l' -> {
                // <l cab reg speedByte functMap>
                val cab = parts.getOrNull(0)?.toIntOrNull() ?: return
                val speedByte = parts.getOrNull(2)?.toIntOrNull() ?: return
                val functions = parts.getOrNull(3)?.toLongOrNull() ?: 0L
                val wire = speedByte and 0x7F
                confirmLoco(cab) {
                    it.copy(
                        speed = if (wire <= 1) 0 else wire - 1,
                        direction = if (speedByte and 0x80 != 0) Direction.FORWARD else Direction.REVERSE,
                        functions = (0..28).filter { f -> functions and (1L shl f) != 0L }.toSet(),
                    )
                }
            }
            'Q', 'q' -> {
                val id = parts.firstOrNull()?.toIntOrNull() ?: return
                if (id >= 1) eventFlow.emit(feedbackEvent(id - 1, occupied = message[0] == 'Q'))
            }
        }
    }

    companion object {
        const val PORT = 2560
    }
}
