@file:OptIn(ExperimentalCoroutinesApi::class)

package de.moba.modules.driving

import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.model.InMemoryLayoutRepository
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DrivingModuleTest {
    private val sim = SimulatorStation()

    private suspend fun TestScope.service(license: LicenseInfo = LicenseInfo.FREE): DrivingService {
        val host = ModuleHost(
            sim,
            InMemoryLayoutRepository(),
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
}
