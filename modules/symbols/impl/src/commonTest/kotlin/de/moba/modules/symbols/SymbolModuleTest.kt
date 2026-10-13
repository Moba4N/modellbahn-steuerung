package de.moba.modules.symbols

import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.model.InMemoryLayoutRepository
import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.ModuleHost
import de.moba.modules.symbols.api.PixelImage
import de.moba.modules.symbols.api.SymbolDef
import de.moba.modules.symbols.api.SymbolLicenseException
import de.moba.modules.symbols.api.SymbolService
import de.moba.modules.symbols.api.SymbolState
import de.moba.modules.symbols.api.SymbolStore
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MemoryStore : SymbolStore {
    var saved: List<SymbolDef>? = null
    override fun load() = saved
    override fun save(symbols: List<SymbolDef>) {
        saved = symbols
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SymbolModuleTest {
    private val station = SimulatorStation()

    private suspend fun TestScope.start(license: LicenseInfo, store: SymbolStore = MemoryStore()): SymbolService {
        val host = ModuleHost(
            station,
            InMemoryLayoutRepository(),
            license,
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        )
        host.register(SymbolModule(SymbolRepository(store)))
        station.connect()
        host.startAll()
        advanceUntilIdle()
        return host.service(SymbolService::class)!!
    }

    @Test
    fun startsWithExamples() = runTest {
        val service = start(LicenseInfo.FREE)
        assertEquals(listOf("sym-1", "sym-2"), service.symbols.value.map { it.id })
        assertEquals(2, service.symbol("sym-1")!!.states.size)
    }

    @Test
    fun drawingIsFreeButSavingNeedsPro() = runTest {
        val store = MemoryStore()
        val service = start(LicenseInfo.FREE, store)
        assertFalse(service.canSave)
        val id = service.newId()
        assertEquals("sym-3", id)
        service.update(SymbolDef(id, "Test", states = listOf(SymbolState("a", PixelImage(16).set(1, 1, -1)))))
        assertTrue(service.unsaved.value)
        assertEquals(3, service.symbols.value.size)
        assertFailsWith<SymbolLicenseException> { service.save() }
        assertNull(store.saved)
    }

    @Test
    fun proSavesAndReplaces() = runTest {
        val store = MemoryStore()
        val service = start(LicenseInfo.of("pro"), store)
        assertTrue(service.canSave)
        val lamp = service.symbol("sym-1")!!
        service.update(lamp.copy(name = "Lampe"))
        service.remove("sym-2")
        service.save()
        assertFalse(service.unsaved.value)
        assertEquals(listOf("Lampe"), store.saved!!.map { it.name })
    }

    @Test
    fun clubIncludesPro() = runTest {
        assertTrue(start(LicenseInfo.of("club")).canSave)
    }
}
