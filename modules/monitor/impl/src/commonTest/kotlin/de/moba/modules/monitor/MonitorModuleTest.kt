package de.moba.modules.monitor

import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.model.InMemoryLayoutRepository
import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.ModuleHost
import de.moba.core.station.Direction
import de.moba.modules.monitor.api.MonitorKind
import de.moba.modules.monitor.api.MonitorService
import de.moba.modules.monitor.api.formatMonitorTime
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonitorModuleTest {
    private suspend fun TestScope.start(): Pair<SimulatorStation, MonitorService> {
        val sim = SimulatorStation()
        val host = ModuleHost(sim, InMemoryLayoutRepository(), LicenseInfo.FREE, backgroundScope)
        host.register(MonitorModule(testScheduler.timeSource))
        sim.connect()
        host.startAll()
        runCurrent()
        return sim to host.service(MonitorService::class)!!
    }

    @Test
    fun recordsWhatIsSentAndWhatComesBack() = runTest {
        val (sim, monitor) = start()
        assertTrue(monitor.rawAvailable)
        sim.setLocoSpeed(3, 40, Direction.FORWARD)
        sim.setTurnout(12, true)
        runCurrent()
        val e = monitor.entries.value
        assertEquals("Lok 3: Fahrstufe 40 vorwärts", e.first { it.kind == MonitorKind.SENT }.text)
        assertTrue(e.any { it.kind == MonitorKind.RECEIVED && it.text == "Weiche 12 abzweigend" })
        assertTrue(e.any { it.kind == MonitorKind.EVENT && it.text == "Lok 3: Fahrstufe 40 vorwärts" })
        assertTrue(monitor.export(setOf(MonitorKind.SENT)).contains("Weiche 12 abzweigend"))
    }

    @Test
    fun pauseClearAndLimit() = runTest {
        val (sim, monitor) = start()
        monitor.setPaused(true)
        sim.setTrackPower(true)
        runCurrent()
        assertTrue(monitor.entries.value.isEmpty())
        monitor.setPaused(false)
        repeat(MonitorService.MAX_ENTRIES) { sim.setLocoFunction(3, it % 32, true) }
        runCurrent()
        assertEquals(MonitorService.MAX_ENTRIES, monitor.entries.value.size)
        monitor.clear()
        assertTrue(monitor.entries.value.isEmpty())
    }

    @Test
    fun timeFormat() {
        assertEquals("1.005 s", formatMonitorTime(1005))
        assertEquals("1:02.345", formatMonitorTime(62_345))
    }
}
