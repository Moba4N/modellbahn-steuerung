@file:OptIn(ExperimentalCoroutinesApi::class)

package de.moba.adapters.simulator

import de.moba.core.station.Direction
import de.moba.core.station.StationEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SimulatorStationTest {
    @Test
    fun confirmsLocoCommandsAsEvents() = runTest(UnconfinedTestDispatcher()) {
        val sim = SimulatorStation()
        val events = async { sim.events.take(2).toList() }
        sim.setLocoSpeed(3, 40, Direction.FORWARD)
        sim.setLocoFunction(3, 0, on = true)

        val last = events.await().last() as StationEvent.LocoChanged
        assertEquals(40, last.state.speed)
        assertEquals(setOf(0), last.state.functions)
    }

    @Test
    fun emergencyStopHaltsAllLocos() = runTest {
        val sim = SimulatorStation()
        sim.setLocoSpeed(3, 80, Direction.FORWARD)
        sim.setLocoSpeed(5, 20, Direction.REVERSE)
        sim.emergencyStop()
        assertEquals(0, sim.loco(3).speed)
        assertEquals(0, sim.loco(5).speed)
        assertEquals(Direction.REVERSE, sim.loco(5).direction)
    }

    @Test
    fun simulatesS88Feedback() = runTest {
        val sim = SimulatorStation(s88Modules = 2)
        sim.simulateFeedback(2, 5, occupied = true)
        assertTrue(sim.isOccupied(2, 5))
        assertFailsWith<IllegalArgumentException> { sim.simulateFeedback(3, 1, occupied = true) }
    }
}
