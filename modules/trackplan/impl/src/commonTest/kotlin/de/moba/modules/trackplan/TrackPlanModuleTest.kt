package de.moba.modules.trackplan

import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.model.InMemoryLayoutRepository
import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.ModuleHost
import de.moba.modules.trackplan.api.LicenseRequiredException
import de.moba.modules.trackplan.api.TrackPlan
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.api.TrackPlanStore
import de.moba.modules.trackplan.api.TrackShape
import de.moba.modules.trackplan.api.TrackTile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class MemoryStore : TrackPlanStore {
    var saved: List<TrackPlan>? = null
    override fun load() = saved ?: emptyList()
    override fun save(plans: List<TrackPlan>) {
        saved = plans
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TrackPlanModuleTest {
    private suspend fun TestScope.start(
        license: LicenseInfo,
        store: TrackPlanStore = MemoryStore(),
        station: SimulatorStation = SimulatorStation(),
    ): TrackPlanService {
        val host = ModuleHost(
            station,
            InMemoryLayoutRepository(),
            license,
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        )
        host.register(TrackPlanModule(TrackPlanRepository(store)))
        station.connect()
        host.startAll()
        advanceUntilIdle()
        return host.service(TrackPlanService::class)!!
    }

    @Test
    fun startsWithExampleLayout() = runTest {
        val service = start(LicenseInfo.FREE)
        assertEquals("Beispielanlage", service.plans.value.single().name)
        assertEquals(listOf("beispiel"), service.visiblePlans.value)
    }

    @Test
    fun withoutLicenseEditingWorksButSavingDoesNot() = runTest {
        val store = MemoryStore()
        val service = start(LicenseInfo.FREE, store)
        val plan = service.addPlan("Neu")
        service.update(plan.put(TrackTile(0, 0, TrackShape.STRAIGHT)))

        assertTrue(service.unsaved.value)
        assertFalse(service.canSave)
        assertFailsWith<LicenseRequiredException> { service.save() }
        assertEquals(null, store.saved)
    }

    @Test
    fun basisSavesAndLoadsAgain() = runTest {
        val store = MemoryStore()
        val service = start(LicenseInfo.of("basis"), store)
        service.addPlan("Neu")
        service.save()

        assertFalse(service.unsaved.value)
        assertEquals(listOf("Beispielanlage", "Neu"), store.saved!!.map { it.name })
        assertEquals(listOf("Beispielanlage", "Neu"), TrackPlanRepository(store).plans.value.map { it.name })
    }

    @Test
    fun freeAndBasisShowOnePlanProShowsThree() = runTest {
        val basis = start(LicenseInfo.of("basis"))
        val second = basis.addPlan("Zwei")
        basis.toggleVisible(second.id)
        assertEquals(1, basis.maxVisiblePlans)
        assertEquals(listOf(second.id), basis.visiblePlans.value)

        val pro = start(LicenseInfo.of("pro"))
        val ids = listOf(pro.addPlan("2"), pro.addPlan("3"), pro.addPlan("4")).map { it.id }
        pro.toggleVisible(ids[0])
        pro.toggleVisible(ids[1])
        assertEquals(3, pro.visiblePlans.value.size)
        assertFailsWith<LicenseRequiredException> { pro.toggleVisible(ids[2]) }
        pro.toggleVisible(ids[0])
        assertEquals(2, pro.visiblePlans.value.size)
    }

    @Test
    fun operatesTurnoutsAndShowsFeedback() = runTest {
        val station = SimulatorStation()
        val service = start(LicenseInfo.FREE, station = station)
        val turnout = TrackTile(0, 0, TrackShape.TURNOUT_LEFT, address = 12)

        service.operate(turnout)
        advanceUntilIdle()
        assertEquals(true, service.turnouts.value[12])
        service.operate(turnout)
        advanceUntilIdle()
        assertEquals(false, service.turnouts.value[12])

        station.simulateFeedback(2, 5, occupied = true)
        advanceUntilIdle()
        assertEquals(setOf(2 to 5), service.occupied.value)
    }
}
