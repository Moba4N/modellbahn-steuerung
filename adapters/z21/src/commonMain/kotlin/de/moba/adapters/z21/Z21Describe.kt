package de.moba.adapters.z21

/** Lesbare Deutung von Z21-Paketen fuer den Monitor. */
object Z21Describe {
    /** Ein Paket von der App an die Zentrale. */
    fun outgoing(packet: ByteArray): String? {
        if (packet.size < 4) return null
        val header = u8(packet, 2) or (u8(packet, 3) shl 8)
        val d = packet.copyOfRange(4, packet.size)
        return when (header) {
            Z21Codec.HEADER_GET_SERIAL -> "Seriennummer abfragen"
            Z21Codec.HEADER_LOGOFF -> "Abmelden"
            Z21Codec.HEADER_SET_BROADCAST_FLAGS -> "Meldungen abonnieren"
            Z21Codec.HEADER_RMBUS_GETDATA -> "Rückmelder abfragen (Gruppe ${d.getOrNull(0)?.toInt() ?: 0})"
            Z21Codec.HEADER_SYSTEMSTATE_GETDATA -> "Systemzustand abfragen"
            Z21Codec.HEADER_X -> outgoingX(d)
            else -> null
        }
    }

    private fun outgoingX(d: ByteArray): String? {
        if (d.isEmpty()) return null
        fun b(i: Int) = d.getOrNull(i)?.let { it.toInt() and 0xFF } ?: 0
        fun loco() = ((b(2) and 0x3F) shl 8) or b(3)
        return when {
            b(0) == 0x21 && b(1) == 0x24 -> "Status abfragen"
            b(0) == 0x21 && b(1) == 0x81 -> "Gleisspannung ein"
            b(0) == 0x21 && b(1) == 0x80 -> "Gleisspannung aus"
            b(0) == 0x80 -> "Nothalt"
            b(0) == 0xE3 && b(1) == 0xF0 -> "Lok ${loco()}: Zustand abfragen"
            b(0) == 0xE4 && b(1) == 0x13 -> {
                val wire = b(4) and 0x7F
                val speed = if (wire <= 1) 0 else wire - 1
                "Lok ${loco()}: Fahrstufe $speed ${if (b(4) and 0x80 != 0) "vorwärts" else "rückwärts"}"
            }
            b(0) == 0xE4 && b(1) == 0xF8 -> "Lok ${loco()}: F${b(4) and 0x3F} ${if (b(4) and 0x40 != 0) "ein" else "aus"}"
            b(0) == 0x53 -> {
                val address = ((b(1) shl 8) or b(2)) + 1
                "Weiche $address ${if (b(3) and 1 != 0) "abzweigend" else "gerade"}, Ausgang ${if (b(3) and 0x08 != 0) "ein" else "aus"}"
            }
            b(0) == 0x23 && b(1) == 0x11 -> "CV ${((b(2) shl 8) or b(3)) + 1} lesen (Programmiergleis)"
            b(0) == 0x24 && b(1) == 0x12 -> "CV ${((b(2) shl 8) or b(3)) + 1} = ${b(4)} schreiben (Programmiergleis)"
            b(0) == 0xE6 && b(1) == 0x30 -> "Lok ${loco()}: CV ${(((b(4) and 0x03) shl 8) or b(5)) + 1} = ${b(6)} schreiben (Hauptgleis)"
            else -> null
        }
    }

    /** Ein Datagramm von der Zentrale, so wie es die App verstanden hat. */
    fun incoming(datagram: ByteArray): String? =
        Z21Codec.decode(datagram).mapNotNull(::message).joinToString("; ").ifEmpty { null }

    fun message(m: Z21Message): String? = when (m) {
        is Z21Message.SerialNumber -> "Seriennummer ${m.serial}"
        is Z21Message.TrackPower -> if (m.on) "Gleisspannung ein" else "Gleisspannung aus"
        Z21Message.ShortCircuit -> "Kurzschluss"
        Z21Message.EmergencyStopped -> "Nothalt"
        is Z21Message.Status -> buildList {
            if (m.emergencyStop) add("Nothalt")
            if (m.trackPowerOff) add("Gleis aus")
            if (m.shortCircuit) add("Kurzschluss")
        }.ifEmpty { listOf("in Betrieb") }.joinToString(", ", prefix = "Status: ")
        is Z21Message.TurnoutInfo -> "Weiche ${m.address} ${if (m.thrown) "abzweigend" else "gerade"}"
        is Z21Message.LocoInfo -> "Lok ${m.address}: Fahrstufe ${m.speed} ${if (m.forward) "vorwärts" else "rückwärts"}" +
            if (m.functions.isEmpty()) "" else ", " + m.functions.sorted().joinToString(" ") { "F$it" }
        is Z21Message.RmBusModule -> "Rückmelder Modul ${m.module}: " +
            (0..7).joinToString("") { if (m.inputs and (1 shl it) != 0) "■" else "□" }
        is Z21Message.LocoNetOccupancy -> "LocoNet-Melder ${m.address} ${if (m.occupied) "belegt" else "frei"}"
        Z21Message.SystemState -> "Systemzustand"
        is Z21Message.CvResult -> "CV ${m.cv} = ${m.value}"
        is Z21Message.CvNack -> if (m.shortCircuit) "CV: Kurzschluss" else "CV: keine Antwort vom Decoder"
    }

    private fun u8(bytes: ByteArray, index: Int) = bytes[index].toInt() and 0xFF
}
