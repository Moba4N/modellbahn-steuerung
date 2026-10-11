@file:OptIn(ExperimentalCoroutinesApi::class)

package de.moba.adapters.cs

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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Spielt die Central Station: bestaetigt jedes Telegramm mit gesetztem Antwort-Bit. */
private class FakeCs(scope: TestScope) {
    private val pair = MemoryByteStream.pair()
    val appSide = pair.first
    val csSide = pair.second
    val received = mutableListOf<CanFrame>()

    init {
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
            while (true) {
                val bytes = csSide.read() ?: break
                for (offset in bytes.indices step CsCodec.FRAME_SIZE) {
                    val frame = CsCodec.decode(bytes, offset)
                    received += frame
                    send(CanFrame(frame.command, true, frame.data))
                }
            }
        }
    }

    suspend fun send(frame: CanFrame) = csSide.write(CsCodec.encode(frame))
}

class CsStationTest {
    private fun TestScope.setUp(): Triple<FakeCs, CsStation, MutableList<StationEvent>> {
        val cs = FakeCs(this)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val station = CsStation("CS3", { cs.appSide }, dispatcher = dispatcher)
        val events = mutableListOf<StationEvent>()
        backgroundScope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) { station.events.collect { events += it } }
        return Triple(cs, station, events)
    }

    @Test
    fun hashHasRequiredBits() {
        assertEquals(0x0300, CsCodec.HASH and 0x0380)
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val frame = CsCodec.locoSpeed(0xC000 + 3, 500)
        val bytes = CsCodec.encode(frame)
        assertEquals(13, bytes.size)
        val back = CsCodec.decode(bytes)
        assertEquals(CsCodec.CMD_LOCO_SPEED, back.command)
        assertEquals(0xC003, back.u32(0))
        assertEquals(500, back.u16(4))
    }

    @Test
    fun connectsAndDrivesWithConfirmation() = runTest {
        val (cs, station, events) = setUp()
        station.connect()
        assertTrue(StationEvent.ConnectionChanged(true) in events)
        assertEquals(CsCodec.CMD_PING, cs.received.first().command)

        station.setLocoSpeed(3, 63, Direction.FORWARD)
        advanceUntilIdle()

        val direction = cs.received.first { it.command == CsCodec.CMD_LOCO_DIRECTION }
        assertEquals(3, direction.u32(0))
        assertEquals(1, direction.u8(4))
        assertEquals(500, cs.received.last { it.command == CsCodec.CMD_LOCO_SPEED }.u16(4))
        assertEquals(LocoState(3, speed = 63, direction = Direction.FORWARD), (events.last() as StationEvent.LocoChanged).state)
    }

    @Test
    fun usesDccUidsAndReportsTurnoutsAndPower() = runTest {
        val cs = FakeCs(this)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val station = CsStation("CS3", { cs.appSide }, CsProtocol.DCC, CsProtocol.DCC, dispatcher, turnoutPulseMillis = 0)
        val events = mutableListOf<StationEvent>()
        backgroundScope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) { station.events.collect { events += it } }
        station.connect()

        station.setLocoFunction(5, 0, on = true)
        station.setTurnout(7, thrown = true)
        station.setTrackPower(true)
        advanceUntilIdle()

        assertEquals(0xC005, cs.received.first { it.command == CsCodec.CMD_LOCO_FUNCTION }.u32(0))
        assertEquals(0x3806, cs.received.first { it.command == CsCodec.CMD_ACCESSORY }.u32(0))
        assertTrue(StationEvent.LocoChanged(LocoState(5, functions = setOf(0))) in events)
        assertTrue(StationEvent.TurnoutChanged(7, thrown = true) in events)
        assertTrue(StationEvent.TrackPowerChanged(true) in events)
    }

    @Test
    fun reportsS88AndStopFromTheCsEvenWhenSplit() = runTest {
        val (cs, station, events) = setUp()
        station.connect()

        val s88 = CsCodec.encode(CanFrame(CsCodec.CMD_S88_EVENT, true, byteArrayOf(0, 0, 0, 18, 0, 1, 0, 0)))
        cs.csSide.write(s88.copyOfRange(0, 6))
        cs.csSide.write(s88.copyOfRange(6, 13) + CsCodec.encode(CsCodec.system(CsCodec.SYSTEM_STOP)))
        advanceUntilIdle()

        assertTrue(StationEvent.FeedbackChanged(2, 2, occupied = true) in events)
        assertEquals(StationEvent.TrackPowerChanged(false), events.last())
    }

    @Test
    fun reportsLostConnection() = runTest {
        val (cs, station, events) = setUp()
        station.connect()
        cs.csSide.close()
        advanceUntilIdle()
        assertEquals(StationEvent.ConnectionChanged(false), events.last())
    }
}
