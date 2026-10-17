package de.moba.modules.throttles

import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.model.InMemoryLayoutRepository
import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.ModuleHost
import de.moba.core.runtime.ModuleState
import de.moba.modules.driving.DrivingModule
import de.moba.modules.throttles.api.THROTTLES_MODULE_ID
import de.moba.modules.throttles.api.ThrottleBarService
import de.moba.modules.throttles.api.ThrottleBarStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ThrottleBarModuleTest {
    private class MemoryStore(var saved: List<Int> = emptyList()) : ThrottleBarStore {
        override fun load() = saved
        override fun save(addresses: List<Int>) {
            saved = addresses
        }
    }

    private fun host(license: LicenseInfo, store: ThrottleBarStore) = ModuleHost(
        SimulatorStation(),
        InMemoryLayoutRepository(),
        license,
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
    ).apply { register(DrivingModule(), ThrottleBarModule(ThrottleBarRepository(store))) }

    @Test
    fun lockedWithoutPro() = runTest {
        val host = host(LicenseInfo.of("basis"), MemoryStore())
        host.startAll()
        assertEquals(ModuleState.LOCKED, host.status.value[THROTTLES_MODULE_ID]?.state)
        assertNull(host.service(ThrottleBarService::class))
    }

    @Test
    fun opensAndClosesThrottlesAndRemembersThem() = runTest {
        val store = MemoryStore(listOf(3, 3, 12))
        val host = host(LicenseInfo.of("pro"), store)
        host.startAll()
        val bar = host.service(ThrottleBarService::class)!!
        assertEquals(listOf(3, 12), bar.open.value)
        bar.open(5)
        bar.open(3)
        assertEquals(listOf(3, 12, 5), store.saved)
        bar.close(12)
        assertEquals(listOf(3, 5), bar.open.value)
        (6..9).forEach(bar::open)
        assertFailsWith<IllegalStateException> { bar.open(10) }
        assertFailsWith<IllegalArgumentException> { bar.open(0) }
    }
}
