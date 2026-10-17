package de.moba.modules.routes

import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.model.InMemoryLayoutRepository
import de.moba.core.model.Layout
import de.moba.core.model.Loco
import de.moba.core.model.LocoDynamics
import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.ModuleHost
import de.moba.core.station.Direction
import de.moba.modules.routes.api.Feedback
import de.moba.modules.routes.api.Route
import de.moba.modules.routes.api.RouteBook
import de.moba.modules.routes.api.RouteConflictException
import de.moba.modules.routes.api.RouteLicenseException
import de.moba.modules.routes.api.RouteService
import de.moba.modules.routes.api.RouteSetting
import de.moba.modules.routes.api.RouteStore
import de.moba.modules.routes.api.Shuttle
import de.moba.modules.routes.api.TrainRoute
import de.moba.modules.routes.api.TrainRouteStep
import de.moba.modules.trackplan.TrackPlanModule
import de.moba.modules.trackplan.TrackPlanRepository
import de.moba.modules.trackplan.api.BlockReservation
import de.moba.modules.trackplan.api.TrackPlanService
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteModuleTest {
    private class MemoryStore(var saved: RouteBook? = null) : RouteStore {
        override fun load() = saved
        override fun save(book: RouteBook) {
            saved = book
        }
    }

    private val west = Route("fs-1", "Gleis 1 → West", listOf(RouteSetting(1, false), RouteSetting(2, true)), Feedback(1, 1), Feedback(1, 2))
    private val east = Route("fs-2", "Gleis 2 → Ost", listOf(RouteSetting(2, false), RouteSetting(3, true)), Feedback(1, 3), Feedback(1, 4))
    private val other = Route("fs-3", "Abstellgleis", listOf(RouteSetting(5, true)), null, Feedback(2, 1))

    private suspend fun TestScope.start(
        license: LicenseInfo,
        store: MemoryStore = MemoryStore(),
        layout: InMemoryLayoutRepository = InMemoryLayoutRepository(),
    ): Pair<SimulatorStation, RouteService> {
        val sim = SimulatorStation()
        val host = ModuleHost(sim, layout, license, backgroundScope)
        host.register(RouteModule(RouteRepository(store)))
        sim.connect()
        host.startAll()
        val service = host.service(RouteService::class)!!
        listOf(west, east, other).forEach(service::saveRoute)
        return sim to service
    }

    private fun TestScope.wait(millis: Long) {
        testScheduler.advanceTimeBy(millis)
        runCurrent()
    }

    private suspend fun TestScope.hit(sim: SimulatorStation, f: Feedback) {
        sim.simulateFeedback(f.module, f.contact, occupied = true)
        runCurrent()
        sim.simulateFeedback(f.module, f.contact, occupied = false)
        runCurrent()
    }

    @Test
    fun editingIsFreeAndSurvivesARestart() = runTest {
        val store = MemoryStore()
        val (_, service) = start(LicenseInfo.FREE, store)
        assertFalse(service.canSetRoutes)
        assertEquals(3, store.saved!!.routes.size)
        assertEquals("fs-4", service.newId("fs-"))
        service.deleteRoute("fs-3")
        assertEquals(listOf("fs-1", "fs-2"), store.saved!!.routes.map { it.id })
        assertFailsWith<RouteLicenseException> { service.setRoute("fs-1") }
    }

    @Test
    fun settingARouteSwitchesTurnoutsAndBlocksCrossingRoutes() = runTest {
        val (sim, service) = start(LicenseInfo.of("basis"))
        service.setRoute("fs-1")
        assertFalse(sim.turnout(1))
        assertTrue(sim.turnout(2))
        assertEquals(setOf("fs-1"), service.activeRoutes.value)

        val error = assertFailsWith<RouteConflictException> { service.setRoute("fs-2") }
        assertTrue("Gleis 1" in error.message!!)
        service.setRoute("fs-3")
        assertEquals(setOf("fs-1", "fs-3"), service.activeRoutes.value)

        service.releaseRoute("fs-1")
        service.setRoute("fs-2")
        assertFalse(sim.turnout(2))
        assertTrue(sim.turnout(3))
    }

    @Test
    fun routeReleasesItselfWhenTheTrainArrives() = runTest {
        val (sim, service) = start(LicenseInfo.of("basis"))
        service.setRoute("fs-1")
        runCurrent()
        hit(sim, Feedback(1, 1))
        assertEquals(setOf("fs-1"), service.activeRoutes.value)
        hit(sim, Feedback(1, 2))
        assertEquals(emptySet(), service.activeRoutes.value)
    }

    @Test
    fun automationNeedsPro() = runTest {
        val (_, service) = start(LicenseInfo.of("basis"))
        service.saveShuttle(Shuttle("pe-1", "Pendel", 3, endA = Feedback(1, 5), endB = Feedback(1, 6)))
        assertFalse(service.canAutomate)
        assertFailsWith<RouteLicenseException> { service.startShuttle("pe-1") }
    }

    @Test
    fun shuttleDrivesBackAndForthAndBrakes() = runTest {
        val (sim, service) = start(LicenseInfo.of("pro"))
        service.saveShuttle(
            Shuttle("pe-1", "Pendel", 3, speed = 80, endA = Feedback(1, 5), endB = Feedback(1, 6), brakeB = Feedback(1, 7), brakeSpeed = 25, waitSeconds = 5),
        )
        service.startShuttle("pe-1")
        runCurrent()
        assertEquals(80, sim.loco(3).speed)
        assertEquals(Direction.FORWARD, sim.loco(3).direction)

        hit(sim, Feedback(1, 7))
        assertEquals(25, sim.loco(3).speed)
        hit(sim, Feedback(1, 6))
        assertEquals(0, sim.loco(3).speed)
        assertTrue("Wartet" in service.automation.value["pe-1"]!!.text)

        wait(5_000)
        assertEquals(80, sim.loco(3).speed)
        assertEquals(Direction.REVERSE, sim.loco(3).direction)
        hit(sim, Feedback(1, 5))
        assertEquals(0, sim.loco(3).speed)

        wait(5_000)
        assertEquals(Direction.FORWARD, sim.loco(3).direction)
        assertEquals(80, sim.loco(3).speed)

        service.stopAutomation("pe-1")
        runCurrent()
        assertEquals(0, sim.loco(3).speed)
        assertFalse(service.automation.value["pe-1"]!!.running)
    }

    @Test
    fun trainRouteSetsEachRouteDrivesAndWaits() = runTest {
        val (sim, service) = start(LicenseInfo.of("pro"))
        service.saveTrainRoute(
            TrainRoute(
                "zr-1", "Rundfahrt", 5, speed = 70,
                steps = listOf(TrainRouteStep("fs-1", waitSeconds = 30), TrainRouteStep("fs-2", speed = 40)),
            ),
        )
        service.startTrainRoute("zr-1")
        runCurrent()
        wait(500)
        assertEquals(setOf("fs-1"), service.activeRoutes.value)
        assertTrue(sim.turnout(2))
        assertEquals(70, sim.loco(5).speed)

        hit(sim, Feedback(1, 2))
        assertEquals(0, sim.loco(5).speed)
        assertEquals(emptySet(), service.activeRoutes.value)

        wait(30_000)
        wait(500)
        assertEquals(setOf("fs-2"), service.activeRoutes.value)
        assertFalse(sim.turnout(2))
        assertEquals(40, sim.loco(5).speed)

        hit(sim, Feedback(1, 4))
        assertEquals(0, sim.loco(5).speed)
        val status = service.automation.value["zr-1"]!!
        assertFalse(status.running)
        assertEquals("Am Ziel angekommen", status.text)
    }

    @Test
    fun trainRouteWaitsForABlockedRoute() = runTest {
        val (sim, service) = start(LicenseInfo.of("pro"))
        service.saveTrainRoute(TrainRoute("zr-1", "Ost", 5, steps = listOf(TrainRouteStep("fs-2"))))
        service.setRoute("fs-1")
        service.startTrainRoute("zr-1")
        wait(3_000)
        assertEquals(0, sim.loco(5).speed)
        assertTrue("wartet" in service.automation.value["zr-1"]!!.text)

        service.releaseRoute("fs-1")
        wait(1_500)
        assertEquals(setOf("fs-2"), service.activeRoutes.value)
        assertEquals(60, sim.loco(5).speed)

        service.stopAutomation("zr-1")
        runCurrent()
        assertEquals(0, sim.loco(5).speed)
        assertEquals(emptySet(), service.activeRoutes.value)
    }

    @Test
    fun oneLocoRunsOnlyOneAutomation() = runTest {
        val (_, service) = start(LicenseInfo.of("pro"))
        service.saveShuttle(Shuttle("pe-1", "Pendel", 3, endA = Feedback(1, 5), endB = Feedback(1, 6)))
        service.saveTrainRoute(TrainRoute("zr-1", "Route", 3, steps = listOf(TrainRouteStep("fs-1"))))
        service.startShuttle("pe-1")
        runCurrent()
        assertFailsWith<IllegalArgumentException> { service.startTrainRoute("zr-1") }
    }

    @Test
    fun shuttleRunsOnByTheFeedbackOffsetMinusReactionDelay() = runTest {
        // 126 km/h bei Fahrstufe 126 sind in H0 gut 402 mm/s: 100 mm dauern 249 ms, abzueglich 50 ms Reaktion
        val layout = InMemoryLayoutRepository(
            Layout(locos = listOf(Loco("l", "Lok", 3, dynamics = LocoDynamics(feedbackOffsetMm = 100, reactionDelayMs = 50), vmaxKmh = 126))),
        )
        val (sim, service) = start(LicenseInfo.of("pro"), layout = layout)
        service.saveShuttle(Shuttle("pe-1", "Pendel", 3, speed = 126, endA = Feedback(1, 5), endB = Feedback(1, 6), waitSeconds = 5))
        service.startShuttle("pe-1")
        runCurrent()
        assertEquals(126, sim.loco(3).speed)
        hit(sim, Feedback(1, 6))
        wait(150)
        assertEquals(126, sim.loco(3).speed)
        wait(100)
        assertEquals(0, sim.loco(3).speed)
        service.stopAutomation("pe-1")
    }

    @Test
    fun stopTimingWithoutSpeedStopsAtOnce() {
        assertEquals(0, StopTiming(LocoDynamics(feedbackOffsetMm = 100), null).delayMillis())
        assertEquals(0, StopTiming(LocoDynamics(feedbackOffsetMm = 100, reactionDelayMs = 3000), 400.0).delayMillis())
        assertEquals(250, StopTiming(LocoDynamics(feedbackOffsetMm = 100), 400.0).delayMillis())
    }

    @Test
    fun routeReservesBlocksAndTakesTheTrainAlong() = runTest {
        val sim = SimulatorStation()
        val host = ModuleHost(sim, InMemoryLayoutRepository(), LicenseInfo.of("basis"), backgroundScope)
        host.register(TrackPlanModule(TrackPlanRepository(null)))
        host.register(RouteModule(RouteRepository(MemoryStore())))
        sim.connect()
        host.startAll()
        val routes = host.service(RouteService::class)!!
        val plans = host.service(TrackPlanService::class)!!
        listOf(west, east).forEach(routes::saveRoute)
        plans.placeTrain(1 to 1, 3)

        routes.setRoute("fs-1")
        runCurrent()
        assertEquals(setOf(1 to 1, 1 to 2), plans.reservations.value.keys)
        // Ein Block der Fahrstrasse ist schon anders reserviert: Konflikt
        plans.reserve(listOf(1 to 3), BlockReservation("hand", "von Hand"))
        assertFailsWith<RouteConflictException> { routes.setRoute("fs-2") }
        assertFalse("fs-2" in routes.activeRoutes.value)

        hit(sim, Feedback(1, 2))
        assertEquals(mapOf((1 to 2) to 3), plans.trains.value)
        assertEquals(setOf(1 to 3), plans.reservations.value.keys, "Fahrstraße hat ihre Blöcke freigegeben")
    }
}
