@file:OptIn(ExperimentalCoroutinesApi::class)

package de.moba.adapters.z21

import de.moba.core.station.CvException
import de.moba.core.station.Direction
import de.moba.core.station.LocoState
import de.moba.core.station.StationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Spielt die Zentrale: merkt sich gesendete Pakete, Antworten kommen ueber [reply]. */
private class FakeTransport(private val answerSerial: Boolean = true) : Z21Transport {
    val sent = mutableListOf<ByteArray>()
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    var closed = false

    override suspend fun send(packet: ByteArray) {
        sent += packet
        if (answerSerial && packet.contentEquals(Z21Codec.getSerialNumber())) {
            reply(Z21Codec.packet(Z21Codec.HEADER_GET_SERIAL, byteArrayOf(0x39, 0x30, 0, 0)))
        }
    }

    fun reply(datagram: ByteArray) {
        incoming.trySend(datagram)
    }

    override suspend fun receive() = incoming.receive()
    override fun close() {
        closed = true
        incoming.close()
    }
}

class Z21StationTest {
    private fun TestScope.station(transport: FakeTransport) =
        Z21Station(
            "YD7001",
            { transport },
            dispatcher = StandardTestDispatcher(testScheduler),
            keepAliveMillis = Long.MAX_VALUE, // sonst laeuft advanceUntilIdle endlos
        )

    private fun TestScope.collect(station: Z21Station): MutableList<StationEvent> {
        val events = mutableListOf<StationEvent>()
        // Unconfined: Hintergrund-Aufgaben laufen sonst bei advanceUntilIdle nicht mit.
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)).launch(start = CoroutineStart.UNDISPATCHED) {
            station.events.collect { events += it }
        }
        return events
    }

    @Test
    fun connectsSubscribesAndReportsConnection() = runTest {
        val transport = FakeTransport()
        val station = station(transport)
        val events = collect(station)

        station.connect()
        advanceUntilIdle()

        assertContentEquals(Z21Codec.setBroadcastFlags(), transport.sent[0])
        assertTrue(StationEvent.ConnectionChanged(true) in events)
        assertTrue(transport.sent.any { it.contentEquals(Z21Codec.rmBusGetData(0)) })
        station.disconnect()
        assertTrue(transport.closed)
    }

    @Test
    fun failsWhenStationDoesNotAnswer() = runTest {
        val station = station(FakeTransport(answerSerial = false))
        assertFailsWith<Z21Exception> { station.connect() }
    }

    @Test
    fun reportsOnlyConfirmedLocoState() = runTest {
        val transport = FakeTransport()
        val station = station(transport)
        val events = collect(station)
        station.connect()

        station.setLocoSpeed(3, 40, Direction.FORWARD)
        advanceUntilIdle()
        assertContentEquals(Z21Codec.getLocoInfo(3), transport.sent[transport.sent.size - 2])
        assertContentEquals(Z21Codec.setLocoDrive(3, 40, forward = true), transport.sent.last())
        assertTrue(events.none { it is StationEvent.LocoChanged }, "Ohne Bestaetigung kein Ereignis")

        transport.reply(Z21Codec.xPacket(0xEF, 0x00, 0x03, 0x04, 0xA9, 0x10, 0x00))
        advanceUntilIdle()
        assertEquals(
            StationEvent.LocoChanged(LocoState(3, speed = 40, direction = Direction.FORWARD, functions = setOf(0))),
            events.last(),
        )
        station.disconnect()
    }

    @Test
    fun mapsRmBusInputsToS88Contacts() = runTest {
        val transport = FakeTransport()
        val station = station(transport)
        val events = collect(station)
        station.connect()

        // R-Bus-Modul 4, Eingang 5 belegt = s88-Modul 2, Kontakt 13.
        fun rm(module4: Int) = Z21Codec.packet(
            Z21Codec.HEADER_RMBUS_DATACHANGED,
            byteArrayOf(0, 0, 0, 0, module4.toByte(), 0, 0, 0, 0, 0, 0),
        )
        transport.reply(rm(0x10))
        transport.reply(rm(0x00))
        advanceUntilIdle()

        val feedback = events.filterIsInstance<StationEvent.FeedbackChanged>()
        assertEquals(
            listOf(StationEvent.FeedbackChanged(2, 13, true), StationEvent.FeedbackChanged(2, 13, false)),
            feedback,
        )
        station.disconnect()
    }

    @Test
    fun switchesTurnoutWithPulse() = runTest {
        val transport = FakeTransport()
        val station = station(transport)
        station.connect()
        station.setTurnout(12, thrown = true)
        assertContentEquals(Z21Codec.setTurnout(12, thrown = true, activate = false), transport.sent.last())
        assertContentEquals(Z21Codec.setTurnout(12, thrown = true, activate = true), transport.sent[transport.sent.size - 2])
        station.disconnect()
    }

    @Test
    fun readsAndWritesCvsOnTheProgrammingTrack() = runTest {
        val transport = FakeTransport()
        val station = station(transport)
        station.connect()
        transport.reply(Z21Codec.xPacket(0x61, 0x01)) // Gleisspannung an
        advanceUntilIdle()

        // runCurrent statt advanceUntilIdle: sonst laeuft die Wartezeit auf die Antwort ab.
        val read = async { station.readCv(29) }
        runCurrent()
        assertContentEquals(Z21Codec.cvRead(29), transport.sent.last())
        transport.reply(Z21Codec.xPacket(0x64, 0x14, 0x00, 0x1C, 0x06))
        assertEquals(6, read.await())
        // Programmiermodus verlassen: Gleisspannung wie vorher wieder an
        assertContentEquals(Z21Codec.trackPowerOn(), transport.sent.last())

        val write = async { station.writeCv(1, 3) }
        runCurrent()
        assertContentEquals(Z21Codec.cvWrite(1, 3), transport.sent.last())
        transport.reply(Z21Codec.xPacket(0x64, 0x14, 0x00, 0x00, 0x03))
        write.await()
        station.disconnect()
    }

    @Test
    fun reportsMissingDecoder() = runTest {
        val transport = FakeTransport()
        val station = station(transport)
        station.connect()
        val read = async { runCatching { station.readCv(1) } }
        runCurrent()
        transport.reply(Z21Codec.xPacket(0x61, 0x13))
        val error = read.await().exceptionOrNull()
        assertTrue(error is CvException && "Programmiergleis" in error.message!!)
        // Gleisspannung war aus und bleibt aus
        assertContentEquals(Z21Codec.trackPowerOff(), transport.sent.last())
        station.disconnect()
    }

    @Test
    fun writesOnMainWithoutWaiting() = runTest {
        val transport = FakeTransport()
        val station = station(transport)
        station.connect()
        station.writeCvOnMain(3, 3, 20)
        assertContentEquals(Z21Codec.cvPomWrite(3, 3, 20), transport.sent.last())
        station.disconnect()
    }
}
