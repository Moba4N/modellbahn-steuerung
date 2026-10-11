package de.moba.adapters.xpressnet

/**
 * XpressNet-Nachrichten (Lenz), wie sie das LAN/USB-Interface 23151 ueber TCP (Port 5550) weiterreicht.
 *
 * Jede Nachricht: Kopfbyte (untere 4 Bit = Anzahl Datenbytes), Daten, XOR-Pruefbyte.
 * Das Interface stellt jeder Nachricht 0xFF 0xFE (zur Zentrale) bzw. 0xFF 0xFD (von der Zentrale) voran.
 */
object XpressNetCodec {
    const val PORT = 5550

    fun message(vararg bytes: Int): ByteArray {
        val checksum = bytes.fold(0) { acc, b -> acc xor (b and 0xFF) }
        return byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + ByteArray(bytes.size) { bytes[it].toByte() } + checksum.toByte()
    }

    fun statusRequest() = message(0x21, 0x24)
    fun trackPowerOn() = message(0x21, 0x81)
    fun trackPowerOff() = message(0x21, 0x80)
    fun stopAll() = message(0x80)

    /** Adressen ab 100 sind bei Lenz lange Adressen. */
    fun address(address: Int): Pair<Int, Int> =
        if (address < 100) 0 to address else (0xC0 or (address shr 8)) to (address and 0xFF)

    fun locoInfo(address: Int): ByteArray {
        val (ah, al) = address(address)
        return message(0xE3, 0x00, ah, al)
    }

    /** 128 Fahrstufen: auf dem Draht 0 = Halt, 1 = Nothalt, 2 bis 127 = Fahrstufe 1 bis 126. */
    fun drive(address: Int, speed: Int, forward: Boolean): ByteArray {
        val (ah, al) = address(address)
        val wire = if (speed == 0) 0 else speed + 1
        return message(0xE4, 0x13, ah, al, (if (forward) 0x80 else 0) or wire)
    }

    /** Funktionen werden gruppenweise gesendet; [functions] ist der gewuenschte Gesamtzustand. */
    fun functionGroup(address: Int, function: Int, functions: Set<Int>): ByteArray {
        val (ah, al) = address(address)
        fun bits(range: IntRange) = range.foldIndexed(0) { i, acc, f -> if (f in functions) acc or (1 shl i) else acc }
        return when (function) {
            in 0..4 -> message(0xE4, 0x20, ah, al, bits(1..4) or (if (0 in functions) 0x10 else 0))
            in 5..8 -> message(0xE4, 0x21, ah, al, bits(5..8))
            in 9..12 -> message(0xE4, 0x22, ah, al, bits(9..12))
            in 13..20 -> message(0xE4, 0x23, ah, al, bits(13..20))
            in 21..28 -> message(0xE4, 0x28, ah, al, bits(21..28))
            else -> throw IllegalArgumentException("XpressNet kennt Funktionen F0 bis F28")
        }
    }

    /** Weiche [address] ab 1; [activate] schaltet die Spule an oder aus. */
    fun accessory(address: Int, thrown: Boolean, activate: Boolean): ByteArray {
        val a = address - 1
        val data = 0x80 or (if (activate) 0x08 else 0) or ((a % 4) shl 1) or (if (thrown) 1 else 0)
        return message(0x52, a / 4, data)
    }

    /**
     * Zerlegt empfangene Bytes in Nachrichten (ohne Praefix und Pruefbyte).
     * Liefert die Nachrichten und den noch unvollstaendigen Rest.
     */
    fun split(bytes: ByteArray): Pair<List<IntArray>, ByteArray> {
        val messages = mutableListOf<IntArray>()
        var i = 0
        while (i < bytes.size) {
            if (bytes[i] == 0xFF.toByte()) {
                if (i + 1 >= bytes.size) break
                i += 2 // Praefix 0xFF 0xFD oder 0xFF 0xFE
                continue
            }
            val header = bytes[i].toInt() and 0xFF
            val length = (header and 0x0F) + 2
            if (i + length > bytes.size) break
            messages += IntArray(length - 1) { bytes[i + it].toInt() and 0xFF }
            i += length
        }
        return messages to bytes.copyOfRange(i, bytes.size)
    }
}
