package de.moba.adapters.z21

/**
 * Kodiert und dekodiert Pakete des Z21-LAN-Protokolls (Roco/Fleischmann z21, YaMoRC YD7001 u. a.).
 *
 * Jedes Paket: DataLen (2 Byte, little endian, inklusive Kopf), Header (2 Byte, little endian), Daten.
 * Ein UDP-Datagramm kann mehrere Pakete hintereinander enthalten.
 */
object Z21Codec {
    const val PORT = 21105

    const val HEADER_GET_SERIAL = 0x10
    const val HEADER_LOGOFF = 0x30
    const val HEADER_X = 0x40
    const val HEADER_SET_BROADCAST_FLAGS = 0x50
    const val HEADER_RMBUS_DATACHANGED = 0x80
    const val HEADER_RMBUS_GETDATA = 0x81
    const val HEADER_SYSTEMSTATE_DATACHANGED = 0x84
    const val HEADER_SYSTEMSTATE_GETDATA = 0x85
    const val HEADER_LOCONET_DETECTOR = 0xA4

    /** Fahren und Schalten (Lok- und Weicheninfos, Gleisspannung). */
    const val FLAG_DRIVING_SWITCHING = 0x00000001

    /** Rueckmelder am R-Bus (bei der YD7001 vermutlich auch s88, siehe README). */
    const val FLAG_RMBUS = 0x00000002

    /** Systemzustand (Strom, Spannung, Temperatur), dient zugleich als Lebenszeichen. */
    const val FLAG_SYSTEM_STATE = 0x00000100

    /** Belegtmelder am LocoNet. */
    const val FLAG_LOCONET_DETECTOR = 0x08000000

    const val DEFAULT_FLAGS = FLAG_DRIVING_SWITCHING or FLAG_RMBUS or FLAG_SYSTEM_STATE or FLAG_LOCONET_DETECTOR

    fun packet(header: Int, data: ByteArray = ByteArray(0)): ByteArray {
        val len = 4 + data.size
        return byteArrayOf(len.toByte(), (len shr 8).toByte(), header.toByte(), (header shr 8).toByte()) + data
    }

    /** LAN_X-Paket: XpressNet-Bytes plus XOR-Pruefsumme. */
    fun xPacket(vararg bytes: Int): ByteArray {
        val data = bytes.map { it.toByte() }.toByteArray()
        val checksum = data.fold(0) { acc, b -> acc xor (b.toInt() and 0xFF) }
        return packet(HEADER_X, data + checksum.toByte())
    }

    fun getSerialNumber() = packet(HEADER_GET_SERIAL)
    fun logoff() = packet(HEADER_LOGOFF)
    fun systemStateGetData() = packet(HEADER_SYSTEMSTATE_GETDATA)

    fun setBroadcastFlags(flags: Int = DEFAULT_FLAGS) = packet(
        HEADER_SET_BROADCAST_FLAGS,
        byteArrayOf(flags.toByte(), (flags shr 8).toByte(), (flags shr 16).toByte(), (flags shr 24).toByte()),
    )

    /** Fragt die Rueckmelder einer Gruppe ab: 0 = Module 1 bis 10, 1 = Module 11 bis 20. */
    fun rmBusGetData(group: Int) = packet(HEADER_RMBUS_GETDATA, byteArrayOf(group.toByte()))

    fun getStatus() = xPacket(0x21, 0x24)
    fun trackPowerOn() = xPacket(0x21, 0x81)
    fun trackPowerOff() = xPacket(0x21, 0x80)

    /** Nothalt: alle Loks halten an, die Gleisspannung bleibt an. */
    fun stopAll() = xPacket(0x80)

    fun getLocoInfo(address: Int): ByteArray {
        val (msb, lsb) = locoAddress(address)
        return xPacket(0xE3, 0xF0, msb, lsb)
    }

    /**
     * Fahrbefehl mit 128 Fahrstufen. [speed] 0 = Halt, 1 bis 126 = Fahrstufe.
     * Auf dem Draht ist 0 = Halt, 1 = Nothalt der einzelnen Lok, 2 bis 127 = Fahrstufe 1 bis 126.
     */
    fun setLocoDrive(address: Int, speed: Int, forward: Boolean): ByteArray {
        require(speed in 0..126) { "Fahrstufe muss zwischen 0 und 126 liegen" }
        val (msb, lsb) = locoAddress(address)
        val wire = if (speed == 0) 0 else speed + 1
        val rv = (if (forward) 0x80 else 0) or wire
        return xPacket(0xE4, 0x13, msb, lsb, rv)
    }

    fun setLocoFunction(address: Int, function: Int, on: Boolean): ByteArray {
        require(function in 0..31) { "Funktion muss zwischen F0 und F31 liegen" }
        val (msb, lsb) = locoAddress(address)
        val tt = if (on) 0x40 else 0x00
        return xPacket(0xE4, 0xF8, msb, lsb, tt or function)
    }

    /**
     * Schaltet einen Weichenausgang. Adresse 1 in der Bedienung entspricht FAdr 0 im Protokoll.
     * [activate] = true schaltet den Ausgang ein; danach mit false wieder aus.
     */
    fun setTurnout(address: Int, thrown: Boolean, activate: Boolean): ByteArray {
        require(address in 1..2048) { "Weichenadresse muss zwischen 1 und 2048 liegen" }
        val fAdr = address - 1
        val value = 0x80 or (if (activate) 0x08 else 0) or (if (thrown) 1 else 0)
        return xPacket(0x53, fAdr shr 8, fAdr and 0xFF, value)
    }

    private fun locoAddress(address: Int): Pair<Int, Int> {
        require(address in 1..9999) { "Lokadresse muss zwischen 1 und 9999 liegen" }
        val msb = (address shr 8) and 0x3F
        return (if (address >= 128) msb or 0xC0 else msb) to (address and 0xFF)
    }

    /** Zerlegt ein UDP-Datagramm in Nachrichten. Unbekannte Pakete werden uebersprungen. */
    fun decode(datagram: ByteArray): List<Z21Message> {
        val messages = mutableListOf<Z21Message>()
        var offset = 0
        while (offset + 4 <= datagram.size) {
            val len = u8(datagram, offset) or (u8(datagram, offset + 1) shl 8)
            if (len < 4 || offset + len > datagram.size) break
            val header = u8(datagram, offset + 2) or (u8(datagram, offset + 3) shl 8)
            val data = datagram.copyOfRange(offset + 4, offset + len)
            decodePacket(header, data)?.let(messages::addAll)
            offset += len
        }
        return messages
    }

    private fun decodePacket(header: Int, data: ByteArray): List<Z21Message>? = when (header) {
        HEADER_GET_SERIAL -> if (data.size >= 4) listOf(Z21Message.SerialNumber(le32(data, 0).toLong() and 0xFFFFFFFFL)) else null
        HEADER_X -> decodeX(data)?.let(::listOf)
        HEADER_RMBUS_DATACHANGED -> decodeRmBus(data)
        HEADER_LOCONET_DETECTOR -> decodeLocoNetDetector(data)?.let(::listOf)
        HEADER_SYSTEMSTATE_DATACHANGED -> listOf(Z21Message.SystemState)
        else -> null
    }

    private fun decodeX(data: ByteArray): Z21Message? {
        if (data.size < 2) return null
        val x = u8(data, 0)
        return when {
            x == 0x61 && data.size >= 2 -> when (u8(data, 1)) {
                0x00 -> Z21Message.TrackPower(on = false)
                0x01 -> Z21Message.TrackPower(on = true)
                0x08 -> Z21Message.ShortCircuit
                else -> null
            }
            x == 0x81 && u8(data, 1) == 0x00 -> Z21Message.EmergencyStopped
            x == 0x62 && u8(data, 1) == 0x22 && data.size >= 3 -> {
                val state = u8(data, 2)
                Z21Message.Status(
                    emergencyStop = state and 0x01 != 0,
                    trackPowerOff = state and 0x02 != 0,
                    shortCircuit = state and 0x04 != 0,
                )
            }
            x == 0x43 && data.size >= 4 -> {
                val address = ((u8(data, 1) shl 8) or u8(data, 2)) + 1
                when (u8(data, 3) and 0x03) {
                    0x01 -> Z21Message.TurnoutInfo(address, thrown = false)
                    0x02 -> Z21Message.TurnoutInfo(address, thrown = true)
                    else -> null
                }
            }
            x == 0xEF && data.size >= 6 -> decodeLocoInfo(data)
            else -> null
        }
    }

    private fun decodeLocoInfo(data: ByteArray): Z21Message.LocoInfo {
        val address = ((u8(data, 1) and 0x3F) shl 8) or u8(data, 2)
        val stepsCode = u8(data, 3) and 0x07
        val rv = u8(data, 4)
        val raw = rv and 0x7F
        // Fahrstufen werden auf die 126er-Skala der App umgerechnet.
        val speed = when (stepsCode) {
            0 -> if (raw <= 1) 0 else (raw - 1) * 126 / 14
            2 -> if (raw <= 1) 0 else (raw - 1) * 126 / 28
            else -> if (raw <= 1) 0 else raw - 1
        }
        val functions = mutableSetOf<Int>()
        val db4 = u8(data, 5)
        if (db4 and 0x10 != 0) functions += 0
        for (i in 0..3) if (db4 and (1 shl i) != 0) functions += 1 + i
        val groups = listOf(6 to 5, 7 to 13, 8 to 21, 9 to 29) // Datenbyte to erste Funktion
        for ((index, first) in groups) {
            if (index >= data.size - 1) break // letztes Byte ist die Pruefsumme
            val byte = u8(data, index)
            for (bit in 0..7) if (byte and (1 shl bit) != 0 && first + bit <= 31) functions += first + bit
        }
        return Z21Message.LocoInfo(address, speed, forward = rv and 0x80 != 0, functions = functions)
    }

    /**
     * R-Bus-Rueckmelder: Gruppe 0 = Module 1 bis 10, Gruppe 1 = Module 11 bis 20, je 8 Eingaenge.
     * Zwei R-Bus-Module bilden ein 16er-s88-Modul: R-Bus 1 = s88 1, Kontakte 1 bis 8; R-Bus 2 = s88 1, Kontakte 9 bis 16.
     */
    private fun decodeRmBus(data: ByteArray): List<Z21Message> {
        if (data.size < 11) return emptyList()
        val group = u8(data, 0)
        return (0 until 10).map { i ->
            val rmModule = group * 10 + i + 1
            Z21Message.RmBusModule(rmModule, u8(data, 1 + i))
        }
    }

    private fun decodeLocoNetDetector(data: ByteArray): Z21Message? {
        if (data.size < 4) return null
        val type = u8(data, 0)
        val address = u8(data, 1) or (u8(data, 2) shl 8)
        return if (type == 0x01) Z21Message.LocoNetOccupancy(address, occupied = u8(data, 3) != 0) else null
    }

    private fun u8(bytes: ByteArray, index: Int) = bytes[index].toInt() and 0xFF
    private fun le32(bytes: ByteArray, i: Int) =
        u8(bytes, i) or (u8(bytes, i + 1) shl 8) or (u8(bytes, i + 2) shl 16) or (u8(bytes, i + 3) shl 24)
}

sealed interface Z21Message {
    data class SerialNumber(val serial: Long) : Z21Message
    data class TrackPower(val on: Boolean) : Z21Message
    data object ShortCircuit : Z21Message
    data object EmergencyStopped : Z21Message
    data class Status(val emergencyStop: Boolean, val trackPowerOff: Boolean, val shortCircuit: Boolean) : Z21Message
    data class TurnoutInfo(val address: Int, val thrown: Boolean) : Z21Message
    data class LocoInfo(val address: Int, val speed: Int, val forward: Boolean, val functions: Set<Int>) : Z21Message

    /** Ein R-Bus-Modul mit 8 Eingaengen; Bit 0 = Eingang 1. */
    data class RmBusModule(val module: Int, val inputs: Int) : Z21Message

    /** LocoNet-Belegtmelder; [address] ab 1. */
    data class LocoNetOccupancy(val address: Int, val occupied: Boolean) : Z21Message
    data object SystemState : Z21Message
}
