@file:OptIn(ExperimentalCoroutinesApi::class)

package de.moba.adapters.dccex

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

/** Spielt eine DCC-EX: beantwortet Status, Gleisspannung und Fahrbefehle wie die Firmware 5.x. */
private class FakeDccEx(scope: TestScope) {
    private val pair = MemoryByteStream.pair()
    val appSide = pair.first
    val side = pair.second
    val received = mutableListOf<String>()
    private val functions = mutableMapOf<Int, Long>()
    private val speeds = mutableMapOf<Int, Int>()

    init {
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
            val splitter = DccExSplitter()
            while (true) {
                val bytes = side.read() ?: break
                splitter.feed(bytes).forEach { answer(it) }
            }
        }
    }

    private suspend fun answer(command: String) {
        received += command
        val p = command.split(' ')
        when (p[0]) {
            "s" -> send("<iDCC-EX V-5.0.0 / MEGA / STANDARD_MOTOR_SHIELD G-devel>\n<p0>")
            "1" -> send("<p1>")
            "0" -> send("<p0>")
            "t" -> {
                val cab = p[1].toInt()
                speeds[cab] = (if (p[3] == "1") 0x80 else 0) or (if (p[2].toInt() == 0) 0 else p[2].toInt() + 1)
                send("<l $cab 0 ${speeds[cab]} ${functions[cab] ?: 0}>")
            }
            "F" -> {
                val cab = p[1].toInt()
                val bit = 1L shl p[2].toInt()
                functions[cab] = if (p[3] == "1") (functions[cab] ?: 0) or bit else (functions[cab] ?: 0) and bit.inv()
                send("<l $cab 0 ${speeds[cab] ?: 0x80} ${functions[cab]}>")
            }
        }
    }

    suspend fun send(text: String) = side.write(text.encodeToByteArray())
}

class DccExStationTest {
    private fun TestScope.setUp(): Triple<FakeDccEx, DccExStation, MutableList<StationEvent>> {
        val fake = FakeDccEx(this)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val station = DccExStation("DCC-EX", { fake.appSide }, dispatcher = dispatcher)
        val events = mutableListOf<StationEvent>()
        backgroundScope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) { station.events.collect { events += it } }
        return Triple(fake, station, events)
    }

    @Test
    fun splitsMessagesWithoutLineBreaksAndAcrossReads() {
        val splitter = DccExSplitter()
        assertEquals(listOf("p1"), splitter.feed("<p1><l 3".encodeToByteArray()))
        assertEquals(listOf("l 3 0 128 0"), splitter.feed(" 0 128 0>\n".encodeToByteArray()))
    }

    @Test
    fun connectsAndSwitchesPower() = runTest {
        val (fake, station, events) = setUp()
        station.connect()
        station.setTrackPower(true)
        advanceUntilIdle()

        assertEquals("s", fake.received.first())
        assertTrue(StationEvent.ConnectionChanged(true) in events)
        assertEquals(StationEvent.TrackPowerChanged(true), events.last())
    }

    @Test
    fun drivesAndSwitchesFunctionsWithConfirmation() = runTest {
        val (fake, station, events) = setUp()
        station.connect()

        station.setLocoSpeed(3, 50, Direction.FORWARD)
        station.setLocoFunction(3, 0, on = true)
        advanceUntilIdle()

        assertTrue("t 3 50 1" in fake.received)
        assertTrue("F 3 0 1" in fake.received)
        assertEquals(
            LocoState(3, speed = 50, direction = Direction.FORWARD, functions = setOf(0)),
            (events.last() as StationEvent.LocoChanged).state,
        )
    }

    @Test
    fun reportsSensorsAsFeedback() = runTest {
        val (fake, station, events) = setUp()
        station.connect()

        fake.send("<Q 17>")
        advanceUntilIdle()
        assertEquals(StationEvent.FeedbackChanged(2, 1, occupied = true), events.last())

        fake.send("<q 17>")
        advanceUntilIdle()
        assertEquals(StationEvent.FeedbackChanged(2, 1, occupied = false), events.last())
    }
}
