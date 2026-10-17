package de.moba.modules.actions

import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.model.InMemoryLayoutRepository
import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.ModuleHost
import de.moba.core.station.Direction
import de.moba.core.station.LocoState
import de.moba.modules.actions.api.ActionBook
import de.moba.modules.actions.api.ActionLicenseException
import de.moba.modules.actions.api.ActionRule
import de.moba.modules.actions.api.ActionService
import de.moba.modules.actions.api.ActionStore
import de.moba.modules.actions.api.Command
import de.moba.modules.actions.api.CommandType
import de.moba.modules.actions.api.Condition
import de.moba.modules.actions.api.ConditionType
import de.moba.modules.driving.DrivingModule
import de.moba.modules.trackplan.TrackPlanModule
import de.moba.modules.trackplan.TrackPlanRepository
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.vehicles.VehicleModule
import de.moba.modules.vehicles.VehicleRepository
import de.moba.modules.vehicles.api.Fleet
import de.moba.modules.vehicles.api.Gauge
import de.moba.modules.vehicles.api.LocoRecord
import de.moba.modules.vehicles.api.Traction
import de.moba.modules.vehicles.api.VehicleGroup
import de.moba.modules.vehicles.api.VehicleService
import de.moba.modules.vehicles.api.WagonCategory
import de.moba.modules.vehicles.api.WagonClass
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionModuleTest {
    private class MemoryStore(var saved: ActionBook? = null) : ActionStore {
        override fun load() = saved
        override fun save(book: ActionBook) {
            saved = book
        }
    }

    private class Setup(val sim: SimulatorStation, val actions: ActionService, val plan: TrackPlanService, val vehicles: VehicleService)

    private suspend fun TestScope.start(license: LicenseInfo, store: MemoryStore = MemoryStore(), random: Random = Random(1)): Setup {
        val sim = SimulatorStation()
        val host = ModuleHost(sim, InMemoryLayoutRepository(), license, backgroundScope)
        val clock = { testScheduler.currentTime }
        host.register(
            DrivingModule(),
            TrackPlanModule(TrackPlanRepository(null), clock),
            VehicleModule(VehicleRepository(null)),
            ActionModule(ActionRepository(store), clock, { 12 * 60 }, random),
        )
        sim.connect()
        host.startAll()
        runCurrent()
        return Setup(sim, host.service(ActionService::class)!!, host.service(TrackPlanService::class)!!, host.service(VehicleService::class)!!)
    }

    private fun TestScope.wait(millis: Long) {
        testScheduler.advanceTimeBy(millis)
        runCurrent()
    }

    private val occupied = Condition.of(ConditionType.BLOCK_OCCUPIED, "block" to "1.3")
    private val switch = Command.of(CommandType.ACCESSORY, "adresse" to "7", "stellung" to "abzweigend")

    @Test
    fun firesOnceWhenTheConditionBecomesTrue() = runTest {
        val s = start(LicenseInfo.of("pro"))
        s.actions.save(ActionRule("ak-1", "Einfahrt", condition = occupied, commands = listOf(switch, Command.of(CommandType.MESSAGE, "text" to "Zug da"))))
        wait(500)
        assertFalse(s.sim.turnout(7))
        s.sim.simulateFeedback(1, 3, true)
        wait(500)
        assertTrue(s.sim.turnout(7))
        assertEquals(setOf("ak-1"), s.actions.satisfied.value)
        assertEquals("Zug da", s.actions.log.value.first().text)

        // Bleibt erfuellt: kein zweites Mal
        s.sim.setTurnout(7, false)
        wait(2000)
        assertFalse(s.sim.turnout(7))

        // Erst frei, dann wieder belegt: noch einmal
        s.sim.simulateFeedback(1, 3, false)
        wait(500)
        assertTrue(s.actions.satisfied.value.isEmpty())
        s.sim.simulateFeedback(1, 3, true)
        wait(500)
        assertTrue(s.sim.turnout(7))
    }

    @Test
    fun editingIsFreeButRunningNeedsPro() = runTest {
        val store = MemoryStore()
        val s = start(LicenseInfo.FREE, store)
        s.actions.save(ActionRule(s.actions.newId(), "A", condition = occupied, commands = listOf(switch)))
        assertEquals("ak-2", s.actions.newId())
        assertEquals(1, store.saved!!.rules.size)
        s.sim.simulateFeedback(1, 3, true)
        wait(500)
        // Die Bedingung wird angezeigt, aber nichts ausgefuehrt
        assertEquals(setOf("ak-1"), s.actions.satisfied.value)
        assertFalse(s.sim.turnout(7))
        assertFailsWith<ActionLicenseException> { s.actions.run("ak-1") }
        s.actions.setEnabled("ak-1", false)
        wait(500)
        assertTrue(s.actions.satisfied.value.isEmpty())
        assertFalse(store.saved!!.rules.single().enabled)
    }

    @Test
    fun commandsRunInOrderWithWaiting() = runTest {
        val s = start(LicenseInfo.of("pro"))
        s.actions.save(
            ActionRule(
                "ak-1", "Abfahrt",
                commands = listOf(
                    Command.of(CommandType.LOCO_FUNCTION, "lok" to "3", "funktion" to "2", "zustand" to "an"),
                    Command.of(CommandType.WAIT, "sekunden" to "3"),
                    Command.of(CommandType.LOCO_SPEED, "lok" to "3", "fahrstufe" to "50", "richtung" to "rueck"),
                ),
                enabled = false,
            ),
        )
        s.actions.run("ak-1")
        wait(100)
        assertEquals(setOf(2), s.sim.loco(3).functions)
        assertEquals(0, s.sim.loco(3).speed)
        assertEquals(setOf("ak-1"), s.actions.running.value)
        wait(3000)
        assertEquals(LocoState(3, 50, Direction.REVERSE, setOf(2)), s.sim.loco(3))
        assertTrue(s.actions.running.value.isEmpty())
    }

    @Test
    fun randomIsRolledOncePerChange() = runTest {
        // Mit 0 % nie, mit 100 % immer
        val s = start(LicenseInfo.of("pro"))
        s.actions.save(ActionRule("ak-1", "Nie", condition = Condition.and(occupied, Condition.of(ConditionType.RANDOM, "prozent" to "0")), commands = listOf(switch)))
        s.actions.save(
            ActionRule(
                "ak-2", "Immer",
                condition = Condition.and(occupied, Condition.of(ConditionType.RANDOM, "prozent" to "100")),
                commands = listOf(Command.of(CommandType.ACCESSORY, "adresse" to "8", "stellung" to "abzweigend")),
            ),
        )
        s.sim.simulateFeedback(1, 3, true)
        wait(3000)
        assertFalse(s.sim.turnout(7))
        assertTrue(s.sim.turnout(8))
        assertEquals(setOf("ak-1", "ak-2"), s.actions.satisfied.value)
    }

    @Test
    fun trainConditionsUseBlocksAndVehicleData() = runTest {
        val s = start(LicenseInfo.of("pro"))
        val wagon = s.vehicles.saveWagon(WagonClass("gw-1", VehicleGroup.FREIGHT, "Zans", category = WagonCategory.TANK))
        s.vehicles.saveLoco(LocoRecord("lok-5", "BR 50", 5, traction = Traction.STEAM, gauge = Gauge.H0, consist = listOf(wagon.id)))
        s.plan.placeTrain(1 to 3, 5)
        val rule = ActionRule(
            "ak-1", "Dampf mit Kesselwagen wartet",
            condition = Condition.and(
                Condition.of(ConditionType.CATEGORY, "block" to "1.3", "gattung" to "STEAM"),
                Condition.of(ConditionType.GAUGE, "block" to "1.3", "spur" to "H0"),
                Condition.of(ConditionType.WAGON, "block" to "1.3", "bauart" to "TANK"),
                Condition.of(ConditionType.TRAIN, "lok" to "5"),
                Condition.of(ConditionType.BLOCK_WAIT, "block" to "1.3", "sekunden" to "5"),
            ),
            commands = listOf(switch),
        )
        s.actions.save(rule)
        wait(3000)
        assertFalse(s.sim.turnout(7))
        wait(3000)
        assertTrue(s.sim.turnout(7))
        s.actions.save(rule.copy(id = "ak-2", condition = Condition.of(ConditionType.GAUGE, "block" to "1.3", "spur" to "N")))
        wait(500)
        assertEquals(setOf("ak-1"), s.actions.satisfied.value)
    }

    @Test
    fun steamBurnsFuelAndRefuelFillsUp() = runTest {
        val store = MemoryStore()
        val s = start(LicenseInfo.of("pro"), store)
        s.vehicles.saveLoco(LocoRecord("lok-5", "BR 50", 5, traction = Traction.STEAM))
        s.vehicles.saveLoco(LocoRecord("lok-6", "E 10", 6, traction = Traction.ELECTRIC))
        s.sim.setLocoSpeed(5, 126, Direction.FORWARD)
        s.sim.setLocoSpeed(6, 126, Direction.FORWARD)
        s.actions.save(
            ActionRule(
                "ak-1", "Wasser fassen",
                condition = Condition.of(ConditionType.FUEL, "lok" to "5", "vergleich" to "unter", "prozent" to "50"),
                commands = listOf(Command.of(CommandType.LOCO_STOP, "lok" to "5"), Command.of(CommandType.REFUEL, "lok" to "5")),
            ),
        )
        // Dampf: 20 Minuten volle Fahrt, nach 5 Minuten also noch 75 %
        wait(5 * 60_000)
        assertEquals(75.0, s.actions.fuel(5), 0.5)
        assertEquals(100.0, s.actions.fuel(6))
        assertTrue(store.saved!!.fuel.getValue(5) < 100.0)
        wait(6 * 60_000)
        assertEquals(0, s.sim.loco(5).speed)
        assertEquals(100.0, s.actions.fuel(5))
    }
}
