package de.moba.adapters.cs

import de.moba.adapters.net.ByteStream
import de.moba.adapters.net.StreamStation
import de.moba.adapters.net.feedbackEvent
import de.moba.core.station.Direction
import de.moba.core.station.StationEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

/**
 * Adapter fuer die Maerklin Central Station 2, 3 und 3plus ueber TCP.
 *
 * Loks werden ueber eine Adresse im gewaehlten Protokoll angesprochen ([locoProtocol]); bei mfx ist
 * das die Adresse, die die CS fuer die Lok anzeigt. Gemeldet wird nur, was die CS bestaetigt.
 */
class CsStation(
    name: String,
    streamFactory: suspend () -> ByteStream,
    private val locoProtocol: CsProtocol = CsProtocol.MM,
    private val accessoryProtocol: CsProtocol = CsProtocol.MM,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    connectTimeoutMillis: Long = 3_000,
    private val turnoutPulseMillis: Long = 200,
) : StreamStation(name, streamFactory, dispatcher, connectTimeoutMillis) {
    private var pending = ByteArray(0)

    override suspend fun handshake() = send(CsCodec.ping())

    override fun reset() {
        pending = ByteArray(0)
    }

    override suspend fun setTrackPower(on: Boolean) = send(CsCodec.system(if (on) CsCodec.SYSTEM_GO else CsCodec.SYSTEM_STOP))

    override suspend fun emergencyStop() = send(CsCodec.system(CsCodec.SYSTEM_HALT))

    override suspend fun setLocoSpeed(address: Int, speed: Int, direction: Direction) {
        require(speed in 0..126) { "Fahrstufe muss zwischen 0 und 126 liegen" }
        val uid = locoProtocol.locoUid(address)
        // Ein Richtungswechsel haelt die Lok an der CS an; danach die neue Fahrstufe setzen.
        if (knownLoco(address)?.direction != direction) {
            send(CsCodec.locoDirection(uid, direction == Direction.FORWARD))
        }
        send(CsCodec.locoSpeed(uid, CsCodec.toCsSpeed(speed)))
    }

    override suspend fun setLocoFunction(address: Int, function: Int, on: Boolean) =
        send(CsCodec.locoFunction(locoProtocol.locoUid(address), function, on))

    override suspend fun setTurnout(address: Int, thrown: Boolean) {
        val uid = accessoryProtocol.accessoryBase + address - 1
        val position = if (thrown) 0 else 1
        send(CsCodec.accessory(uid, position, power = 1))
        delay(turnoutPulseMillis)
        send(CsCodec.accessory(uid, position, power = 0))
    }

    private suspend fun send(frame: CanFrame) = write(CsCodec.encode(frame))

    override suspend fun onBytes(bytes: ByteArray) {
        // TCP liefert die 13-Byte-Telegramme nicht unbedingt einzeln.
        val all = pending + bytes
        val complete = all.size / CsCodec.FRAME_SIZE * CsCodec.FRAME_SIZE
        pending = all.copyOfRange(complete, all.size)
        for (offset in 0 until complete step CsCodec.FRAME_SIZE) {
            handle(CsCodec.decode(all, offset))
        }
    }

    private suspend fun handle(frame: CanFrame) {
        markReady()
        when (frame.command) {
            CsCodec.CMD_SYSTEM -> if (frame.dlc >= 5) {
                when (frame.u8(4)) {
                    CsCodec.SYSTEM_STOP -> eventFlow.emit(StationEvent.TrackPowerChanged(false))
                    CsCodec.SYSTEM_GO -> eventFlow.emit(StationEvent.TrackPowerChanged(true))
                    CsCodec.SYSTEM_HALT -> eventFlow.emit(StationEvent.EmergencyStopped)
                }
            }
            CsCodec.CMD_LOCO_SPEED -> if (frame.dlc == 6) {
                val address = CsProtocol.locoAddress(frame.u32(0)) ?: return
                confirmLoco(address, speed = CsCodec.fromCsSpeed(frame.u16(4)))
            }
            CsCodec.CMD_LOCO_DIRECTION -> if (frame.dlc == 5) {
                val address = CsProtocol.locoAddress(frame.u32(0)) ?: return
                val direction = when (frame.u8(4)) {
                    1 -> Direction.FORWARD
                    2 -> Direction.REVERSE
                    else -> return
                }
                // Die CS haelt die Lok beim Richtungswechsel an.
                confirmLoco(address, speed = 0, direction = direction)
            }
            CsCodec.CMD_LOCO_FUNCTION -> if (frame.dlc >= 6) {
                val address = CsProtocol.locoAddress(frame.u32(0)) ?: return
                confirmLoco(address, function = frame.u8(4) to (frame.u8(5) > 0))
            }
            CsCodec.CMD_ACCESSORY -> if (frame.dlc >= 6 && frame.u8(5) == 1) {
                val address = CsProtocol.accessoryAddress(frame.u32(0)) ?: return
                eventFlow.emit(StationEvent.TurnoutChanged(address, thrown = frame.u8(4) == 0))
            }
            CsCodec.CMD_S88_EVENT -> if (frame.dlc >= 6) {
                val contact = frame.u16(2)
                if (contact > 0) eventFlow.emit(feedbackEvent(contact - 1, occupied = frame.u8(5) != 0))
            }
        }
    }
}
