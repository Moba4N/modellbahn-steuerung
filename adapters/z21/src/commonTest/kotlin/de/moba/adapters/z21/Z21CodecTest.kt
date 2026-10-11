package de.moba.adapters.z21

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Z21CodecTest {
    private fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()

    @Test
    fun encodesPacketsFromTheProtocolSpec() {
        assertContentEquals(bytes(0x04, 0x00, 0x10, 0x00), Z21Codec.getSerialNumber())
        assertContentEquals(bytes(0x07, 0x00, 0x40, 0x00, 0x21, 0x81, 0xA0), Z21Codec.trackPowerOn())
        assertContentEquals(bytes(0x07, 0x00, 0x40, 0x00, 0x21, 0x80, 0xA1), Z21Codec.trackPowerOff())
        assertContentEquals(bytes(0x06, 0x00, 0x40, 0x00, 0x80, 0x80), Z21Codec.stopAll())
        assertContentEquals(bytes(0x08, 0x00, 0x50, 0x00, 0x01, 0x00, 0x00, 0x00), Z21Codec.setBroadcastFlags(0x01))
    }

    @Test
    fun encodesLocoDriveWith128Steps() {
        // Lok 3, Fahrstufe 40 vorwaerts: auf dem Draht 41 mit Richtungsbit = 0xA9.
        assertContentEquals(
            bytes(0x0A, 0x00, 0x40, 0x00, 0xE4, 0x13, 0x00, 0x03, 0xA9, 0x5D),
            Z21Codec.setLocoDrive(3, 40, forward = true),
        )
        // Halt bleibt 0, damit die Lok normal bremst statt Nothalt.
        assertEquals(0x00, Z21Codec.setLocoDrive(3, 0, forward = false)[8].toInt() and 0xFF)
    }

    @Test
    fun encodesLongAddresses() {
        val packet = Z21Codec.setLocoDrive(1000, 1, forward = true)
        assertEquals(0xC3, packet[6].toInt() and 0xFF)
        assertEquals(0xE8, packet[7].toInt() and 0xFF)
    }

    @Test
    fun encodesFunctionAndTurnout() {
        val f = Z21Codec.setLocoFunction(3, 5, on = true)
        assertEquals(0x45, f[8].toInt() and 0xFF)
        // Weiche 1 = FAdr 0, abzweigend, aktivieren.
        val w = Z21Codec.setTurnout(1, thrown = true, activate = true)
        assertContentEquals(bytes(0x53, 0x00, 0x00, 0x89), w.copyOfRange(4, 8))
    }

    @Test
    fun decodesLocoInfo() {
        // Lok 3, 128 Fahrstufen, vorwaerts Fahrstufe 40, F0 + F1 + F5.
        val data = bytes(0xEF, 0x00, 0x03, 0x04, 0xA9, 0x11, 0x01)
        val datagram = Z21Codec.packet(Z21Codec.HEADER_X, data + 0x00)
        assertEquals(
            listOf(Z21Message.LocoInfo(3, speed = 40, forward = true, functions = setOf(0, 1, 5))),
            Z21Codec.decode(datagram),
        )
    }

    @Test
    fun decodesSeveralPacketsInOneDatagram() {
        val datagram = bytes(0x07, 0x00, 0x40, 0x00, 0x61, 0x01, 0x60) +
            bytes(0x06, 0x00, 0x40, 0x00, 0x81, 0x00) +
            bytes(0x09, 0x00, 0x40, 0x00, 0x43, 0x00, 0x04, 0x02, 0x45)
        assertEquals(
            listOf(Z21Message.TrackPower(on = true), Z21Message.EmergencyStopped, Z21Message.TurnoutInfo(5, thrown = true)),
            Z21Codec.decode(datagram),
        )
    }

    @Test
    fun decodesFeedback() {
        val rm = Z21Codec.packet(Z21Codec.HEADER_RMBUS_DATACHANGED, bytes(0x00, 0x00, 0x10, 0, 0, 0, 0, 0, 0, 0, 0))
        val modules = Z21Codec.decode(rm)
        assertEquals(10, modules.size)
        assertEquals(Z21Message.RmBusModule(2, 0x10), modules[1])

        val ln = Z21Codec.packet(Z21Codec.HEADER_LOCONET_DETECTOR, bytes(0x01, 0x15, 0x00, 0x01))
        assertEquals(listOf(Z21Message.LocoNetOccupancy(21, occupied = true)), Z21Codec.decode(ln))
    }

    @Test
    fun ignoresTruncatedAndUnknownPackets() {
        assertEquals(emptyList(), Z21Codec.decode(bytes(0x10, 0x00, 0x40)))
        assertEquals(emptyList(), Z21Codec.decode(Z21Codec.packet(0x99, bytes(1, 2, 3))))
    }
}
