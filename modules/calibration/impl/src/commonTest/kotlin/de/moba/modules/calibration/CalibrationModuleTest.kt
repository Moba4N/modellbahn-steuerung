package de.moba.modules.calibration

import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.model.InMemoryLayoutRepository
import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.ModuleHost
import de.moba.core.runtime.ModuleState
import de.moba.core.station.StationEvent
import de.moba.modules.calibration.api.CALIBRATION_MODULE_ID
import de.moba.modules.calibration.api.CalibrationProgress
import de.moba.modules.calibration.api.CalibrationService
import de.moba.modules.calibration.api.CalibrationSetup
import de.moba.modules.calibration.api.CalibrationStore
import de.moba.modules.calibration.api.Contact
import de.moba.modules.calibration.api.MeasuringTrack
import de.moba.modules.calibration.api.Scale
import de.moba.modules.calibration.api.SpeedProfile
import de.moba.modules.calibration.api.SpeedStepMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalibrationModuleTest {
    private class MemoryStore : CalibrationStore {
        var saved: List<SpeedProfile> = emptyList()
        override fun load() = saved
        override fun save(profiles: List<SpeedProfile>) {
            saved = profiles
        }
    }

    private val track = MeasuringTrack(Contact(1, 1), Contact(1, 2), lengthMm = 1000, scale = Scale.H0)

    private suspend fun TestScope.start(license: LicenseInfo, store: MemoryStore): Pair<SimulatorStation, ModuleHost> {
        val sim = SimulatorStation()
        val host = ModuleHost(sim, InMemoryLayoutRepository(), license, backgroundScope)
        host.register(CalibrationModule(CalibrationRepository(store), testScheduler.timeSource) { "2026-10-14" })
        sim.connect()
        host.startAll()
        return sim to host
    }

    /**
     * Virtuelle Messstrecke: Nach jedem Fahrbefehl ueberfaehrt die Lok erst den Start-, dann den Endmelder.
     * Fahrzeit = 126 000 ms / Fahrstufe der Zentrale; Stufe 0 haelt an. Stufen unter [minSpeed] kommen nie an.
     */
    private fun TestScope.virtualTrack(sim: SimulatorStation, minSpeed: Int = 1) = backgroundScope.launch {
        sim.events.filterIsInstance<StationEvent.LocoChanged>().collect { e ->
            val speed = e.state.speed
            if (speed < minSpeed) return@collect
            launch {
                delay(7_000)
                sim.simulateFeedback(1, 1, occupied = true)
                sim.simulateFeedback(1, 1, occupied = false)
                delay(126_000L / speed)
                sim.simulateFeedback(1, 2, occupied = true)
                sim.simulateFeedback(1, 2, occupied = false)
            }
        }
    }

    /** Hintergrundarbeit zaehlt fuer advanceUntilIdle nicht, deshalb die Zeit ausdruecklich vorspulen. */
    private fun TestScope.finish() {
        testScheduler.advanceTimeBy(3_600_000)
        runCurrent()
    }

    @Test
    fun lockedWithoutPro() = runTest {
        val (_, host) = start(LicenseInfo.of("basis"), MemoryStore())
        assertEquals(ModuleState.LOCKED, host.status.value[CALIBRATION_MODULE_ID]?.state)
    }

    @Test
    fun measures14StepsAndStoresTheProfile() = runTest {
        val store = MemoryStore()
        val (sim, host) = start(LicenseInfo.of("pro"), store)
        virtualTrack(sim)
        val service = host.service(CalibrationService::class)!!
        service.start(CalibrationSetup(3, "BR 218", SpeedStepMode.STEPS_14, track))
        finish()

        val done = assertIs<CalibrationProgress.Finished>(service.progress.value)
        val points = done.profile.points
        assertEquals((1..14).toList(), points.map { it.step })
        // Stufe 14 = 126 auf der Zentrale = 1000 ms fuer 1 m = 1 m/s * 87 * 3,6 = 313,2 km/h
        assertEquals(313.2, points.last().kmh!!, 0.1)
        // Stufe 7 = 63: halb so schnell
        assertEquals(156.6, points[6].kmh!!, 0.1)
        assertEquals(listOf(done.profile), store.saved)
        assertEquals(0, sim.loco(3).speed)
    }

    @Test
    fun tooSlowStepsAreSkippedAndCancelStopsTheLoco() = runTest {
        val store = MemoryStore()
        val (sim, host) = start(LicenseInfo.of("pro"), store)
        virtualTrack(sim, minSpeed = 20)
        val service = host.service(CalibrationService::class)!!
        service.start(CalibrationSetup(5, "V 100", SpeedStepMode.STEPS_28, track, timeoutSeconds = 30))
        finish()
        val profile = assertIs<CalibrationProgress.Finished>(service.progress.value).profile
        // Stufe 4 = 18 auf der Zentrale: kommt nie an
        assertNull(profile.points[3].kmh)
        assertTrue(profile.points[4].kmh != null)

        service.start(CalibrationSetup(5, "V 100", SpeedStepMode.STEPS_128, track))
        testScheduler.advanceTimeBy(20_000)
        service.cancel()
        finish()
        assertIs<CalibrationProgress.Stopped>(service.progress.value)
        assertEquals(0, sim.loco(5).speed)
        // Das abgebrochene Einmessen ueberschreibt nichts
        assertEquals(listOf(SpeedStepMode.STEPS_28), store.saved.map { it.mode })
    }
}
