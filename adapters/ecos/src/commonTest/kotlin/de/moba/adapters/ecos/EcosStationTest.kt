@file:OptIn(ExperimentalCoroutinesApi::class)

package de.moba.adapters.ecos

import de.moba.adapters.net.LineSplitter
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

/**
 * Spielt eine ECoS mit einem s88-Modul und einer angelegten Lok (Adresse 5, Objekt 1000).
 * Fahrbefehle bestaetigt sie wie die echte ECoS mit einem Ereignis.
 */
private class FakeEcos(scope: TestScope) {
    private val pair = MemoryByteStream.pair()
    val appSide = pair.first
    val ecosSide = pair.second
    val received = mutableListOf<String>()
    private var nextId = 1001

    init {
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
            val splitter = LineSplitter()
            while (true) {
                val bytes = ecosSide.read() ?: break
                splitter.feed(bytes).forEach { answer(it) }
            }
        }
    }

    private suspend fun answer(command: String) {
        received += command
        val lines = when {
            command == "get(1, status)" -> listOf("1 status[GO]")
            command == "get(26, size)" -> listOf("26 size[1]")
            command == "get(100, state)" -> listOf("100 state[0x0]")
            command == "queryObjects(10, addr)" -> listOf("1000 addr[5]")
            command.startsWith("create(10,") -> listOf("10 id[${nextId++}]")
            command.matches(Regex("""get\((\d+), speed, dir\)""")) -> listOf("${command.substring(4).substringBefore(',')} speed[0] dir[0]")
            else -> emptyList()
        }
        send("<REPLY $command>", *lines.toTypedArray(), "<END 0 (OK)>")
        Regex("""set\((\d+), (speed|dir|func)\[(.*)]\)""").matchEntire(command)?.let {
            val (id, key, value) = it.destructured
            send("<EVENT $id>", "$id $key[$value]", "<END 0 (OK)>")
        }
        if (command == "set(1, stop)") send("<EVENT 1>", "1 status[STOP]", "<END 0 (OK)>")
    }

    suspend fun send(vararg lines: String) = ecosSide.write(lines.joinToString("") { "$it\r\n" }.encodeToByteArray())
}

class EcosStationTest {
    private fun TestScope.setUp(): Triple<FakeEcos, EcosStation, MutableList<StationEvent>> {
        val ecos = FakeEcos(this)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val station = EcosStation("ECoS", { ecos.appSide }, dispatcher = dispatcher)
        val events = mutableListOf<StationEvent>()
        backgroundScope.launch(dispatcher, start = CoroutineStart.UNDISPATCHED) { station.events.collect { events += it } }
        return Triple(ecos, station, events)
    }

    @Test
    fun parsesLines() {
        val line = EcosLine.parse("1005 speed[64] dir[1] name[\"BR 218\"]")!!
        assertEquals(1005, line.id)
        assertEquals("64", line.value("speed"))
        assertEquals("\"BR 218\"", line.value("name"))
    }

    @Test
    fun connectsAndReadsStatusAndFeedbackModules() = runTest {
        val (ecos, station, events) = setUp()
        station.connect()
        advanceUntilIdle()

        assertTrue(StationEvent.ConnectionChanged(true) in events)
        assertTrue(StationEvent.TrackPowerChanged(true) in events)
        assertTrue("request(100, view)" in ecos.received)

        ecos.send("<EVENT 100>", "100 state[0x0002]", "<END 0 (OK)>")
        advanceUntilIdle()
        assertEquals(StationEvent.FeedbackChanged(1, 2, occupied = true), events.last())
    }

    @Test
    fun drivesExistingLocoWithoutCreatingIt() = runTest {
        val (ecos, station, events) = setUp()
        station.connect()

        station.setLocoSpeed(5, 63, Direction.REVERSE)
        advanceUntilIdle()

        assertTrue(ecos.received.none { it.startsWith("create") })
        assertTrue("request(1000, view, control, force)" in ecos.received)
        assertTrue("set(1000, dir[1])" in ecos.received)
        assertTrue("set(1000, speed[64])" in ecos.received)
        assertEquals(LocoState(5, speed = 63, direction = Direction.REVERSE), (events.last() as StationEvent.LocoChanged).state)
    }

    @Test
    fun createsUnknownLocoAndSwitchesFunctions() = runTest {
        val (ecos, station, events) = setUp()
        station.connect()

        station.setLocoFunction(3, 2, on = true)
        advanceUntilIdle()

        assertTrue(ecos.received.any { it.startsWith("create(10, addr[3], protocol[DCC128]") })
        assertTrue("set(1001, func[2, 1])" in ecos.received)
        assertEquals(setOf(2), (events.last() as StationEvent.LocoChanged).state.functions)
    }

    @Test
    fun switchesTurnoutsAndStops() = runTest {
        val (ecos, station, events) = setUp()
        station.connect()

        station.setTurnout(7, thrown = true)
        station.emergencyStop()
        advanceUntilIdle()

        assertTrue("set(11, switch[DCC7r])" in ecos.received)
        assertEquals(StationEvent.TrackPowerChanged(false), events.last())
    }
}
