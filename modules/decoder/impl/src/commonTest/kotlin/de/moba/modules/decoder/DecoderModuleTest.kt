package de.moba.modules.decoder

import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.model.InMemoryLayoutRepository
import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.ModuleHost
import de.moba.core.station.CvException
import de.moba.modules.decoder.api.DecoderLicenseException
import de.moba.modules.decoder.api.DecoderProfile
import de.moba.modules.decoder.api.DecoderService
import de.moba.modules.decoder.api.DecoderStore
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

private class MemoryStore : DecoderStore {
    var saved: List<DecoderProfile> = emptyList()
    override fun load() = saved
    override fun save(profiles: List<DecoderProfile>) {
        saved = profiles
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DecoderModuleTest {
    private val station = SimulatorStation()

    private suspend fun TestScope.start(license: LicenseInfo, store: DecoderStore = MemoryStore()): DecoderService {
        val host = ModuleHost(
            station,
            InMemoryLayoutRepository(),
            license,
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        )
        host.register(DecoderModule(DecoderRepository(store)))
        station.connect()
        host.startAll()
        advanceUntilIdle()
        return host.service(DecoderService::class)!!
    }

    @Test
    fun programmingIsFree() = runTest {
        val service = start(LicenseInfo.FREE)
        assertTrue(service.canProgram)
        assertEquals(3, service.read(1))
        service.write(3, 20)
        assertEquals(20, service.read(3))
        service.write(4, 12, mainAddress = 5)
        assertEquals(12, station.mainTrackCvs[5]!![4])
    }

    @Test
    fun reportsMissingLoco() = runTest {
        val service = start(LicenseInfo.FREE)
        station.decoderOnProgrammingTrack = false
        assertFailsWith<CvException> { service.read(1) }
    }

    @Test
    fun databaseNeedsTheAddOnLicense() = runTest {
        val store = MemoryStore()
        val service = start(LicenseInfo.of("pro"), store)
        assertFalse(service.canUseDatabase)
        assertFailsWith<DecoderLicenseException> { service.saveProfile(DecoderProfile("p", "BR 218", cvs = mapOf(1 to 3))) }
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun databaseStoresAndUpdatesProfiles() = runTest {
        val store = MemoryStore()
        val service = start(LicenseInfo.of("decoder"), store)
        assertTrue(service.canUseDatabase)
        val id = service.newProfileId()
        service.saveProfile(DecoderProfile(id, "BR 218", locoAddress = 3, cvs = mapOf(1 to 3, 3 to 20)))
        service.saveProfile(service.profiles.value.single().copy(notes = "Sound leiser"))
        assertEquals("Sound leiser", store.saved.single().notes)
        assertEquals("decoder-2", service.newProfileId())
        service.deleteProfile(id)
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun clubIncludesTheDatabase() = runTest {
        assertTrue(start(LicenseInfo.of("club")).canUseDatabase)
    }
}
