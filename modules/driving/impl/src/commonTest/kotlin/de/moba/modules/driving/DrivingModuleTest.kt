@file:OptIn(ExperimentalCoroutinesApi::class)

package de.moba.modules.driving

import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.model.InMemoryLayoutRepository
import de.moba.core.model.Loco
import de.moba.core.model.LocoDynamics
import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.ModuleHost
import de.moba.core.runtime.ModuleState
import de.moba.core.station.Direction
import de.moba.modules.driving.api.DRIVING_MODULE_ID
import de.moba.modules.driving.api.DrivingService
import de.moba.modules.driving.api.LocoLimitReachedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrivingModuleTest {
    private val sim = SimulatorStation()

    private suspend fun TestScope.service(
        license: LicenseInfo = LicenseInfo.FREE,
        layout: InMemoryLayoutRepository = InMemoryLayoutRepository(),
    ): DrivingService {
        val host = ModuleHost(
            sim,
            layout,
            license,
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        )
        host.register(DrivingModule())
        host.startAll()
        assertEquals(ModuleState.ACTIVE, host.status.value.getValue(DRIVING_MODULE_ID).state)
        return host.service(DrivingService::class)!!
    }

    @Test
    fun drivesLocoAndShowsConfirmedState() = runTest {
        val driving = service()
        driving.drive(3, 40, Direction.FORWARD)
        driving.setFunction(3, 0, on = true)
        assertEquals(40, driving.locos.value.getValue(3).speed)
        assertEquals(setOf(0), driving.locos.value.getValue(3).functions)

        driving.stop(3)
        assertEquals(0, driving.locos.value.getValue(3).speed)
    }

    @Test
    fun freeVersionControlsThreeLocos() = runTest {
        val driving = service()
        assertEquals(3, driving.locoLimit)
        driving.drive(1, 10, Direction.FORWARD)
        driving.drive(2, 10, Direction.FORWARD)
        driving.drive(3, 10, Direction.FORWARD)
        driving.drive(1, 20, Direction.REVERSE) // bekannte Lok geht weiter
        assertFailsWith<LocoLimitReachedException> { driving.drive(4, 10, Direction.FORWARD) }
        driving.stop(4) // Anhalten ist nie gesperrt
    }

    @Test
    fun basisSubscriptionRemovesLimit() = runTest {
        val driving = service(LicenseInfo.of("basis"))
        assertNull(driving.locoLimit)
        for (address in 1..10) driving.drive(address, 5, Direction.FORWARD)
        assertEquals(10, driving.locos.value.size)
    }

    @Test
    fun showsLocosDrivenElsewhere() = runTest {
        val driving = service()
        sim.setLocoSpeed(7, 30, Direction.REVERSE) // z. B. vom Handregler an der Zentrale
        assertEquals(30, driving.locos.value.getValue(7).speed)
    }

    private fun heavyLoco() = InMemoryLayoutRepository().apply {
        update {
            it.copy(
                locos = listOf(
                    Loco("g", "Güterzug", 3, dynamics = LocoDynamics(inertia = true, accelerationSeconds = 1.0, brakingSeconds = 2.0, periodMs = 250)),
                ),
            )
        }
    }

    @Test
    fun inertiaRampsUpAndBrakesGently() = runTest {
        val driving = service(layout = heavyLoco())
        driving.drive(3, 126, Direction.FORWARD)
        // 0 bis 126 in 1 s bei 250 ms Takt: 32 Fahrstufen je Schritt
        assertEquals(32, sim.loco(3).speed)
        assertEquals(126, driving.targets.value[3])
        advanceTimeBy(1_100)
        assertEquals(126, sim.loco(3).speed)
        assertNull(driving.targets.value[3])

        driving.stop(3) // Betriebsbremse in 2 s: 16 Fahrstufen je Schritt
        assertEquals(110, sim.loco(3).speed)
        advanceTimeBy(1_000)
        assertTrue(sim.loco(3).speed in 1..109)
        advanceTimeBy(1_200)
        assertEquals(0, sim.loco(3).speed)
    }

    @Test
    fun emergencyBrakeStopsAtOnceDespiteInertia() = runTest {
        val driving = service(layout = heavyLoco())
        driving.drive(3, 126, Direction.FORWARD)
        advanceTimeBy(600)
        driving.emergencyBrake(3)
        assertEquals(0, sim.loco(3).speed)
        assertNull(driving.targets.value[3])
        advanceTimeBy(2_000)
        assertEquals(0, sim.loco(3).speed)
    }

    @Test
    fun reversingWithInertiaBrakesFirst() = runTest {
        val driving = service(layout = heavyLoco())
        driving.drive(3, 64, Direction.FORWARD)
        advanceTimeBy(1_000)
        assertEquals(64, sim.loco(3).speed)
        driving.drive(3, 32, Direction.REVERSE)
        assertEquals(Direction.FORWARD, sim.loco(3).direction)
        advanceTimeBy(5_000)
        assertEquals(Direction.REVERSE, sim.loco(3).direction)
        assertEquals(32, sim.loco(3).speed)
    }

    @Test
    fun withoutInertiaSpeedIsSetAtOnce() = runTest {
        val driving = service()
        driving.drive(3, 126, Direction.FORWARD)
        assertEquals(126, sim.loco(3).speed)
        assertTrue(driving.targets.value.isEmpty())
    }
}
