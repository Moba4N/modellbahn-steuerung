@file:OptIn(ExperimentalCoroutinesApi::class)

package de.moba.adapters.xpressnet

import de.moba.adapters.net.MemoryByteStream
import de.moba.core.station.Direction
import de.moba.core.station.LocoState
import de.moba.core.station.StationEvent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Nachricht vom Interface zur App: Praefix 0xFF 0xFD, Daten, XOR-Pruefbyte. */
private fun fromCentral(vararg bytes: Int): ByteArray {
    val checksum = bytes.fold(0) { acc, b -> acc xor b }
    return byteArrayOf(0xFF.toByte(), 0xFD.toByte()) + ByteArray(bytes.size) { bytes[it].toByte() } + checksum.toByte()
}

/** Spielt eine LZV100 am LAN-Interface: Gleisspannung an, merkt sich Fahrbefehle fuer Lokabfragen. */
private class FakeLenz(scope: TestScope) {
    private val pair = MemoryByteStream.pair()
    val appSide = pair.first
    val side = pair.second
    val received = mutableListOf<IntArray>()
    private val speedBytes = mutableMapOf<Int, Int>()
    private val groupOne = mutableMapOf<Int, Int>()

    init {
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
            while (true) {
                val bytes = side.read() ?: break
                XpressNetCodec.split(bytes).first.forEach { answer(it) }
            }
        }
    }

    private suspend fun answer(m: IntArray) {
        received += m
        fun address() = ((m[2] and 0x3F) shl 8) or m[3]
        when {
            m[0] == 0x21 && m[1] == 0x24 -> side.write(fromCentral(0x62, 0x22, 0x00))
            m[0] == 0x21 && m[1] == 0x81 -> side.write(fromCentral(0x61, 0x01))
            m[0] == 0xE4 && m[1] == 0x13 -> speedBytes[address()] = m[4]
            m[0] == 0xE4 && m[1] == 0x20 -> groupOne[address()] = m[4]
            m[0] == 0xE3 && m[1] == 0x00 ->
                side.write(fromCentral(0xE4, 0x04, speedBytes[address()] ?: 0x80, groupOne[address()] ?: 0, 0))
        }
    }

    suspend fun send(vararg bytes: Int) = side.write(fromCentral(*bytes))
}

class XpressNetStationTest {
    private fun TestScope.setUp(): Triple<FakeLenz, XpressNetStation, MutableList<StationEvent>> {
        val fake = FakeLenz(this)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val station = XpressNetStation("LZV100", { fake.appSide }, dispatcher, keepAliveMillis = Long.MAX_VALUE, turnoutPulseMillis = 0)
        val events = mutableListOf<StationEvent>()
        backgroundScope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) { station.events.collect { events += it } }
        return Triple(fake, station, events)
    }

    @Test
    fun encodesLongAddressesAndAccessories() {
        // Lange Adresse 1234: 0xC4 0xD2; Pruefbyte ueber alles nach dem Praefix.
        val drive = XpressNetCodec.drive(1234, 50, forward = true)
        assertContentEquals(intArrayOf(0xFF, 0xFE, 0xE4, 0x13, 0xC4, 0xD2, 0xB3, 0x13 xor 0xE4 xor 0xC4 xor 0xD2 xor 0xB3), drive.map { it.toInt() and 0xFF }.toIntArray())
        // Weiche 7: Gruppe 1, Ausgang 2 von 4, abzweigend, aktiv: 0x80 | 0x08 | (2 shl 1) | 1
        val accessory = XpressNetCodec.accessory(7, thrown = true, activate = true)
        assertEquals(listOf(0x52, 0x01, 0x8D), accessory.drop(2).take(3).map { it.toInt() and 0xFF })
    }

    @Test
    fun connectsReadsPowerAndSwitchesItOn() = runTest {
        val (_, station, events) = setUp()
        station.connect()
        assertTrue(StationEvent.TrackPowerChanged(true) in events)

        station.setTrackPower(true)
        advanceUntilIdle()
        assertEquals(StationEvent.TrackPowerChanged(true), events.last())
    }

    @Test
    fun confirmsDrivingByAskingTheCentral() = runTest {
        val (fake, station, events) = setUp()
        station.connect()

        station.setLocoSpeed(3, 50, Direction.FORWARD)
        station.setLocoFunction(3, 0, on = true)
        advanceUntilIdle()

        assertTrue(fake.received.any { it[0] == 0xE4 && it[1] == 0x13 && it[4] == 0x80 or 51 })
        assertEquals(
            LocoState(3, speed = 50, direction = Direction.FORWARD, functions = setOf(0)),
            (events.last() as StationEvent.LocoChanged).state,
        )
    }

    @Test
    fun reportsFeedbackModulesAndTurnouts() = runTest {
        val (fake, station, events) = setUp()
        station.connect()

        // Rueckmelder Adresse 5, untere Haelfte, Eingaenge 1 und 3 belegt
        fake.send(0x42, 0x05, 0x45)
        // Weichengruppe 0, obere Haelfte: Weiche 3 abzweigend
        fake.send(0x42, 0x00, 0x12)
        advanceUntilIdle()

        assertTrue(StationEvent.FeedbackChanged(3, 9, occupied = true) in events)
        assertTrue(StationEvent.FeedbackChanged(3, 11, occupied = true) in events)
        assertEquals(StationEvent.TurnoutChanged(3, thrown = true), events.last())
    }
}
