package de.moba.modules.trackplan

import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.model.InMemoryLayoutRepository
import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.ModuleHost
import de.moba.modules.trackplan.api.AccessoryProps
import de.moba.modules.trackplan.api.BlockException
import de.moba.modules.trackplan.api.BlockReservation
import de.moba.modules.trackplan.api.LicenseRequiredException
import de.moba.modules.trackplan.api.ModelClockState
import de.moba.modules.trackplan.api.TableCommand
import de.moba.modules.trackplan.api.measureKey
import de.moba.modules.trackplan.api.isOn
import de.moba.modules.trackplan.api.TrackPlan
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.api.TrackPlanStore
import de.moba.modules.trackplan.api.TrackShape
import de.moba.modules.trackplan.api.TrackTile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
        millis: () -> Long = { testScheduler.currentTime },
    ): TrackPlanService {
        val host = ModuleHost(
            station,
            InMemoryLayoutRepository(),
            license,
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        )
        host.register(TrackPlanModule(TrackPlanRepository(store), millis))
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

    @Test
    fun signalsSwitchAspectsWithAddressPairs() = runTest {
        val station = SimulatorStation()
        val service = start(LicenseInfo.FREE, station = station)
        val signal = TrackTile(4, 2, TrackShape.SIGNAL, address = 40, symbol = "signal:de-ks")
        service.update(service.plans.value.first().put(signal))

        service.setAspect(signal, 3)
        advanceUntilIdle()
        assertEquals(false, service.turnouts.value[41])
        assertEquals(3, service.signalAspects.value[40])

        // Meldung von der Zentrale (z. B. Handregler): Adresse 40 rot = Halt
        station.setTurnout(40, thrown = true)
        advanceUntilIdle()
        assertEquals(0, service.signalAspects.value[40])

        service.operate(signal)
        advanceUntilIdle()
        assertEquals(1, service.signalAspects.value[40])
        assertEquals(false, service.turnouts.value[40])
    }

    @Test
    fun levelCrossingClosesAndOpensWithFeedback() = runTest {
        val station = SimulatorStation()
        val service = start(LicenseInfo.FREE, station = station)
        val crossing = TrackTile(5, 5, TrackShape.LEVEL_CROSSING, address = 30)
            .withProp(AccessoryProps.ON_FEEDBACK, "1.3, 1.4")
            .withProp(AccessoryProps.OFF_FEEDBACK, "1.8")
        service.update(service.plans.value.first().put(crossing))

        station.simulateFeedback(1, 3, occupied = true)
        advanceUntilIdle()
        assertTrue(crossing.isOn(service.turnouts.value))
        station.simulateFeedback(1, 3, occupied = false)
        station.simulateFeedback(1, 8, occupied = true)
        advanceUntilIdle()
        assertTrue(crossing.isOn(service.turnouts.value), "erst oeffnen, wenn der Zug den Ausschalt-Melder verlassen hat")
        station.simulateFeedback(1, 8, occupied = false)
        advanceUntilIdle()
        assertFalse(crossing.isOn(service.turnouts.value))

        // Von Hand schalten
        service.operate(crossing)
        advanceUntilIdle()
        assertTrue(crossing.isOn(service.turnouts.value))
    }

    @Test
    fun relayPulseAndSoundFile() = runTest {
        val station = SimulatorStation()
        val service = start(LicenseInfo.FREE, station = station)
        val relay = TrackTile(0, 0, TrackShape.RELAY, address = 7).withProp(AccessoryProps.PULSE, "300")
        service.setOn(relay, true)
        advanceUntilIdle()
        assertEquals(false, service.turnouts.value[7], "nach dem Impuls wieder aus")

        val played = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.sounds.collect { played += it } }
        val sound = TrackTile(1, 0, TrackShape.SOUND).withProp(AccessoryProps.FILE, "pfiff.wav")
            .withProp(AccessoryProps.ON_FEEDBACK, "2.1")
        service.update(service.plans.value.first().put(sound))
        service.operate(sound)
        station.simulateFeedback(2, 1, occupied = true)
        advanceUntilIdle()
        assertEquals(listOf("pfiff.wav", "pfiff.wav"), played)
    }

    @Test
    fun aspectAccessoryCyclesThroughItsAspects() = runTest {
        val station = SimulatorStation()
        val service = start(LicenseInfo.FREE, station = station)
        val lamp = TrackTile(0, 0, TrackShape.ACCESSORY_ASPECT, address = 60)
            .withProp(AccessoryProps.ASPECTS, "Aus|Blinken|Dauerlicht")
        service.update(service.plans.value.first().put(lamp))
        repeat(2) { service.operate(lamp) }
        advanceUntilIdle()
        assertEquals(2, service.signalAspects.value[60])
        assertEquals(true, service.turnouts.value[61])
        service.operate(lamp)
        advanceUntilIdle()
        assertEquals(0, service.signalAspects.value[60])
    }

    @Test
    fun turntableTurnsStepsAndLocksWhenOccupied() = runTest {
        val station = SimulatorStation()
        val service = start(LicenseInfo.FREE, station = station)
        val table = TrackTile(10, 10, TrackShape.TURNTABLE, address = 225)
            .withProp(AccessoryProps.POSITIONS, "24")
            .withProp(AccessoryProps.TRACKS, "0, 3, 6, 12")
            .withProp(AccessoryProps.CMD_TURN, "241:r")
            .withProp(AccessoryProps.READY_FEEDBACK, "4.1")
            .withProp(AccessoryProps.BLOCK_FEEDBACK, "4.2")
            .withProp(AccessoryProps.BLOCK_LOCK, "1")
        service.update(service.plans.value.first().put(table))

        service.turnTo(table, 3)
        advanceUntilIdle()
        assertEquals(false, service.turnouts.value[226])
        assertEquals(3, service.tablePositions.value[225])
        assertEquals(setOf(225), service.tablesMoving.value)
        station.simulateFeedback(4, 1, occupied = true)
        advanceUntilIdle()
        assertEquals(emptySet(), service.tablesMoving.value)

        service.tableCommand(table, TableCommand.STEP_CW)
        advanceUntilIdle()
        assertEquals(6, service.tablePositions.value[225])
        assertEquals(true, service.turnouts.value[228])
        service.tableCommand(table, TableCommand.TURN_180)
        advanceUntilIdle()
        assertEquals(true, service.turnouts.value[241])
        assertEquals(18, service.tablePositions.value[225])

        // Meldung einer Gleisadresse von aussen (z. B. Handregler)
        station.setTurnout(225, thrown = true)
        advanceUntilIdle()
        assertEquals(0, service.tablePositions.value[225])

        station.simulateFeedback(4, 2, occupied = true)
        advanceUntilIdle()
        assertFailsWith<IllegalStateException> { service.turnTo(table, 12) }
    }

    @Test
    fun trainsStandInBlocksAndMove() = runTest {
        val service = start(LicenseInfo.FREE)
        service.placeTrain(1 to 3, 12)
        service.placeTrain(1 to 4, 7)
        assertFailsWith<BlockException> { service.placeTrain(1 to 3, 7) }
        assertFailsWith<BlockException> { service.moveTrain(1 to 3, 1 to 4) }
        service.moveTrain(1 to 3, 2 to 1)
        assertEquals(mapOf((2 to 1) to 12, (1 to 4) to 7), service.trains.value)
        // Dieselbe Lok in einen anderen Block setzen: sie wechselt dorthin
        service.placeTrain(2 to 5, 7)
        assertEquals(mapOf((2 to 1) to 12, (2 to 5) to 7), service.trains.value)
        service.removeTrain(2 to 1)
        assertEquals(mapOf((2 to 5) to 7), service.trains.value)
    }

    @Test
    fun reservationsBelongToTheirOwner() = runTest {
        val service = start(LicenseInfo.FREE)
        val a = BlockReservation("fs:a", "Fahrstraße A")
        service.reserve(listOf(1 to 1, 1 to 2), a)
        assertFailsWith<BlockException> { service.reserve(listOf(1 to 2, 1 to 3), BlockReservation("fs:b", "Fahrstraße B")) }
        assertEquals(setOf(1 to 1, 1 to 2), service.reservations.value.keys, "nichts halb reserviert")
        service.reserve(listOf(1 to 2), a)
        service.release(1 to 1)
        assertEquals(setOf(1 to 2), service.reservations.value.keys)
        service.releaseOwner("fs:a")
        assertTrue(service.reservations.value.isEmpty())
        service.reserve(listOf(3 to 3), a)
        service.releaseAll()
        assertTrue(service.reservations.value.isEmpty())
    }

    @Test
    fun measurementGivesSpeedInBothDirections() = runTest {
        val station = SimulatorStation()
        var now = 0L
        val service = start(LicenseInfo.FREE, station = station, millis = { now })
        val measure = TrackTile(3, 3, TrackShape.MEASUREMENT, 2)
            .withProp(AccessoryProps.MEASURE_START, "3.1").withProp(AccessoryProps.MEASURE_END, "3.2")
            .withProp(AccessoryProps.MEASURE_LENGTH, "1000")
        val plan = service.plans.value.first().put(measure)
        service.update(plan)
        service.placeTrain(3 to 1, 12)
        val key = measureKey(plan.id, measure)

        station.simulateFeedback(3, 1, occupied = true)
        now = 4_000
        station.simulateFeedback(3, 2, occupied = true)
        advanceUntilIdle()
        val m = service.measurements.value.getValue(key)
        // 1000 mm in 4 s = 250 mm/s, in H0 also 250 * 87 * 3,6 / 1000 = 78,3 km/h
        assertEquals(250.0, m.modelMmPerSecond, 0.01)
        assertEquals(78.3, m.kmh, 0.01)
        assertTrue(m.forward)
        assertEquals(12, m.address)

        station.simulateFeedback(3, 1, occupied = false)
        station.simulateFeedback(3, 2, occupied = false)
        station.simulateFeedback(3, 2, occupied = true)
        now = 6_000
        station.simulateFeedback(3, 1, occupied = true)
        advanceUntilIdle()
        assertFalse(service.measurements.value.getValue(key).forward)
        assertEquals(2_000, service.measurements.value.getValue(key).millis)
    }

    @Test
    fun modelClockRunsFasterAndStops() = runTest {
        var now = 0L
        val service = start(LicenseInfo.FREE, millis = { now })
        service.setClock(7, 30)
        service.setClockFactor(6)
        service.startClock()
        now = 10 * 60_000L
        assertEquals("08:30", ModelClockState.format(service.clock.value.minutesAt(now)))
        service.stopClock()
        now += 60 * 60_000L
        assertEquals("08:30", ModelClockState.format(service.clock.value.minutesAt(now)))
        service.setClock(23, 50)
        service.startClock()
        now += 5 * 60_000L
        assertEquals("00:20", ModelClockState.format(service.clock.value.minutesAt(now)))
    }

    @Test
    fun plansAreDuplicatedAndReordered() = runTest {
        val service = start(LicenseInfo.FREE)
        val first = service.plans.value.first()
        val copy = service.duplicatePlan(first.id)
        assertEquals("${first.name} (Kopie)", copy.name)
        assertEquals(first.tiles, copy.tiles)
        assertEquals("${first.name} (Kopie 2)", service.duplicatePlan(first.id).name)
        service.movePlan(copy.id, -5)
        assertEquals(copy.id, service.plans.value.first().id)
    }
}
