@file:OptIn(ExperimentalCoroutinesApi::class)

package de.moba.core.runtime

import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.model.InMemoryLayoutRepository
import de.moba.core.station.StationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class TestModule(
    override val id: String,
    override val licenseKey: String? = null,
    override val requires: List<Dependency> = emptyList(),
    override val apiVersion: SemVer = SemVer(1),
    val log: MutableList<String> = mutableListOf(),
    val onStart: suspend (ModuleContext) -> Unit = {},
) : MobaModule {
    override val version = SemVer(1)
    override suspend fun start(ctx: ModuleContext) {
        log += "start $id"
        onStart(ctx)
    }
    override suspend fun stop() {
        log += "stop $id"
    }
}

class ModuleHostTest {
    private val sim = SimulatorStation()

    private fun TestScope.host(license: LicenseInfo = LicenseInfo.FREE) =
        ModuleHost(sim, InMemoryLayoutRepository(), license, CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun startsModulesInDependencyOrder() = runTest {
        val log = mutableListOf<String>()
        val host = host()
        // Absichtlich in falscher Reihenfolge registriert.
        host.register(
            TestModule("auto", requires = listOf(Dependency("melder", SemVer(1))), log = log),
            TestModule("melder", requires = listOf(Dependency("fahren", SemVer(1))), log = log),
            TestModule("fahren", log = log),
        )
        host.startAll()
        assertEquals(listOf("start fahren", "start melder", "start auto"), log)
        host.stopAll()
        assertEquals(listOf("stop auto", "stop melder", "stop fahren"), log.drop(3))
    }

    @Test
    fun locksModulesWithoutLicenseAndTheirDependents() = runTest {
        val host = host(LicenseInfo.of("basis"))
        host.register(
            TestModule("stellpult", licenseKey = "basis"),
            TestModule("fahrstrassen", licenseKey = "pro", requires = listOf(Dependency("stellpult", SemVer(1)))),
            TestModule("automatik", licenseKey = "pro", requires = listOf(Dependency("fahrstrassen", SemVer(1)))),
        )
        host.startAll()
        val status = host.status.value
        assertEquals(ModuleState.ACTIVE, status.getValue("stellpult").state)
        assertEquals(ModuleState.LOCKED, status.getValue("fahrstrassen").state)
        assertEquals(ModuleState.LOCKED, status.getValue("automatik").state)
    }

    @Test
    fun rejectsMissingOrIncompatibleDependencies() = runTest {
        val host = host()
        host.register(
            TestModule("alt", apiVersion = SemVer(1, 2)),
            TestModule("braucht-neu", requires = listOf(Dependency("alt", SemVer(2)))),
            TestModule("braucht-fehlendes", requires = listOf(Dependency("gibt-es-nicht", SemVer(1)))),
        )
        host.startAll()
        val status = host.status.value
        assertEquals(ModuleState.ACTIVE, status.getValue("alt").state)
        assertEquals(ModuleState.FAILED, status.getValue("braucht-neu").state)
        assertEquals(ModuleState.FAILED, status.getValue("braucht-fehlendes").state)
    }

    @Test
    fun failingModuleDoesNotStopOthers() = runTest {
        val host = host()
        host.register(
            TestModule("kaputt", onStart = { error("Testfehler") }),
            TestModule("gesund"),
        )
        host.startAll()
        assertEquals(ModuleStatus(ModuleState.FAILED, "Testfehler"), host.status.value["kaputt"])
        assertEquals(ModuleState.ACTIVE, host.status.value.getValue("gesund").state)
    }

    @Test
    fun modulesShareServicesAndReceiveStationEvents() = runTest {
        val received = mutableListOf<StationEvent.FeedbackChanged>()
        val host = host()
        host.register(
            TestModule("anbieter", onStart = { it.provide<String>("Hallo von anbieter") }),
            TestModule(
                "nutzer",
                requires = listOf(Dependency("anbieter", SemVer(1))),
                onStart = { ctx ->
                    assertEquals("Hallo von anbieter", ctx.service<String>())
                    ctx.events.on<StationEvent.FeedbackChanged>(ctx.scope) { received += it }
                },
            ),
        )
        host.startAll()
        sim.simulateFeedback(module = 2, contact = 5, occupied = true)
        assertEquals(listOf(StationEvent.FeedbackChanged(2, 5, true)), received)

        host.stopAll()
        sim.simulateFeedback(module = 2, contact = 5, occupied = false)
        assertTrue(received.size == 1, "Nach dem Stoppen kommen keine Ereignisse mehr an")
    }
}
