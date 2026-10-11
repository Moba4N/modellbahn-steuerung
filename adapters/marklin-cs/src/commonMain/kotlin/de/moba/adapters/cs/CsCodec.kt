package de.moba.adapters.cs

/**
 * Ein CAN-Telegramm der Maerklin Central Station 2/3, wie es ueber TCP (Port 15731) laeuft:
 * 4 Byte Kennung (Prioritaet, Befehl, Antwort-Bit, Hash), 1 Byte Laenge, immer 8 Datenbytes.
 */
class CanFrame(val command: Int, val response: Boolean, val data: ByteArray, val hash: Int = CsCodec.HASH) {
    val dlc: Int get() = data.size

    fun u8(i: Int) = data[i].toInt() and 0xFF
    fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)
    fun u32(i: Int) = (u16(i) shl 16) or u16(i + 2)

    override fun toString() = "CanFrame(cmd=0x${command.toString(16)}, resp=$response, dlc=$dlc)"
}

object CsCodec {
    const val TCP_PORT = 15731

    /** Die CS sendet ihre Telegramme per UDP-Broadcast an diesen Port. */
    const val UDP_BROADCAST_PORT = 15730
    const val FRAME_SIZE = 13

    const val CMD_SYSTEM = 0x00
    const val CMD_LOCO_SPEED = 0x04
    const val CMD_LOCO_DIRECTION = 0x05
    const val CMD_LOCO_FUNCTION = 0x06
    const val CMD_ACCESSORY = 0x0B
    const val CMD_S88_EVENT = 0x11
    const val CMD_PING = 0x18

    const val SYSTEM_STOP = 0x00
    const val SYSTEM_GO = 0x01
    const val SYSTEM_HALT = 0x02

    /** Hash aus einer festen Geraetekennung; die Bits 7 bis 9 sind laut Spezifikation 0b110. */
    val HASH: Int = hashFor(0x4D6F6261)

    fun hashFor(uid: Int): Int {
        val h = ((uid ushr 16) xor uid) and 0xFFFF
        return ((h shl 3) and 0xFF00) or 0x0300 or (h and 0x7F)
    }

    fun encode(frame: CanFrame): ByteArray {
        val id = (frame.command shl 17) or (if (frame.response) 1 shl 16 else 0) or (frame.hash and 0xFFFF)
        val out = ByteArray(FRAME_SIZE)
        out[0] = (id ushr 24).toByte()
        out[1] = (id ushr 16).toByte()
        out[2] = (id ushr 8).toByte()
        out[3] = id.toByte()
        out[4] = frame.dlc.toByte()
        frame.data.copyInto(out, 5)
        return out
    }

    fun decode(bytes: ByteArray, offset: Int = 0): CanFrame {
        val id = ((bytes[offset].toInt() and 0xFF) shl 24) or ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
        val dlc = (bytes[offset + 4].toInt() and 0xFF).coerceAtMost(8)
        return CanFrame(
            command = (id ushr 17) and 0xFF,
            response = (id ushr 16) and 1 == 1,
            data = bytes.copyOfRange(offset + 5, offset + 5 + dlc),
            hash = id and 0xFFFF,
        )
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun uid(uid: Int) = intArrayOf(uid ushr 24, uid ushr 16, uid ushr 8, uid)

    fun system(sub: Int) = CanFrame(CMD_SYSTEM, false, bytes(0, 0, 0, 0, sub))
    fun ping() = CanFrame(CMD_PING, false, ByteArray(0))

    /** [csSpeed] 0 bis 1000. */
    fun locoSpeed(uid: Int, csSpeed: Int) = CanFrame(CMD_LOCO_SPEED, false, bytes(*uid(uid), csSpeed ushr 8, csSpeed))

    fun locoDirection(uid: Int, forward: Boolean) =
        CanFrame(CMD_LOCO_DIRECTION, false, bytes(*uid(uid), if (forward) 1 else 2))

    fun locoFunction(uid: Int, function: Int, on: Boolean) =
        CanFrame(CMD_LOCO_FUNCTION, false, bytes(*uid(uid), function, if (on) 1 else 0))

    /** [position] 0 = rund/rot (abzweigend), 1 = gerade/gruen. [power] 1 = Spule an, 0 = aus. */
    fun accessory(uid: Int, position: Int, power: Int) =
        CanFrame(CMD_ACCESSORY, false, bytes(*uid(uid), position, power))

    fun toCsSpeed(speed: Int) = (speed * 1000 + 63) / 126
    fun fromCsSpeed(csSpeed: Int) = ((csSpeed.coerceIn(0, 1000) * 126 + 500) / 1000)
}

/** Lok- und Zubehoerkennungen der CS je Protokoll. */
enum class CsProtocol(val id: String, val locoBase: Int, val locoRange: IntRange, val accessoryBase: Int) {
    MM("mm", 0x0000, 0x0000..0x03FF, 0x3000),
    MFX("mfx", 0x4000, 0x4000..0x7FFF, 0x3000),
    DCC("dcc", 0xC000, 0xC000..0xFFFF, 0x3800),
    ;

    fun locoUid(address: Int) = locoBase + address

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: MM

        /** Lokadresse zu einer Kennung, gleich welches Protokoll; null fuer Zubehoer und Unbekanntes. */
        fun locoAddress(uid: Int): Int? = entries.firstOrNull { uid in it.locoRange }?.let { uid - it.locoBase }

        /** Weichenadresse (ab 1) zu einer Zubehoerkennung (MM ab 0x3000, DCC ab 0x3800). */
        fun accessoryAddress(uid: Int): Int? = when (uid) {
            in 0x3000..0x33FF -> uid - 0x3000 + 1
            in 0x3800..0x3FFF -> uid - 0x3800 + 1
            else -> null
        }
    }
}
