package de.moba.modules.vehicles

import de.moba.adapters.simulator.SimulatorStation
import de.moba.core.model.InMemoryLayoutRepository
import de.moba.core.model.Loco
import de.moba.core.model.Layout
import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.ModuleHost
import de.moba.modules.vehicles.api.Fleet
import de.moba.modules.vehicles.api.LocoRecord
import de.moba.modules.vehicles.api.TrainCategory
import de.moba.modules.vehicles.api.TrainSet
import de.moba.modules.vehicles.api.VehicleGroup
import de.moba.modules.vehicles.api.VehicleLimitException
import de.moba.modules.vehicles.api.VehicleService
import de.moba.modules.vehicles.api.VehicleStore
import de.moba.modules.vehicles.api.WagonClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MemoryStore(var saved: Fleet? = null) : VehicleStore {
    override fun load() = saved
    override fun save(fleet: Fleet) {
        saved = fleet
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleModuleTest {
    private val layout = InMemoryLayoutRepository(Layout(locos = listOf(Loco("lok-7", "Von Hand", 7))))

    private suspend fun TestScope.start(license: LicenseInfo, store: VehicleStore = MemoryStore()): VehicleService {
        val station = SimulatorStation()
        val host = ModuleHost(
            station,
            layout,
            license,
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        )
        host.register(VehicleModule(VehicleRepository(store)))
        station.connect()
        host.startAll()
        advanceUntilIdle()
        return host.service(VehicleService::class)!!
    }

    private fun VehicleService.addLoco(name: String, address: Int) =
        saveLoco(LocoRecord(newId(VehicleGroup.LOCOS), name, address))

    private fun VehicleService.addWagon(group: VehicleGroup, code: String) =
        saveWagon(WagonClass(newId(group), group, code))

    @Test
    fun freeVersionStoresThreePerList() = runTest {
        val store = MemoryStore()
        val service = start(LicenseInfo.FREE, store)
        assertEquals(3, service.limit)
        repeat(3) { service.addLoco("Lok $it", it + 1) }
        assertFalse(service.canAdd(VehicleGroup.LOCOS))
        assertFailsWith<VehicleLimitException> { service.addLoco("Vierte", 4) }

        // Die Grenze gilt je Liste: Wagen gehen weiterhin.
        repeat(3) { service.addWagon(VehicleGroup.FREIGHT, "Eaos") }
        repeat(3) { service.addWagon(VehicleGroup.PASSENGER, "Bm 235") }
        assertFailsWith<VehicleLimitException> { service.addWagon(VehicleGroup.FREIGHT, "Fals") }
        assertFailsWith<VehicleLimitException> { service.addWagon(VehicleGroup.PASSENGER, "Avmz") }

        assertEquals(3, store.saved!!.locos.size)
        assertEquals(6, store.saved!!.wagons.size)
    }

    @Test
    fun editingWorksAtTheLimit() = runTest {
        val service = start(LicenseInfo.FREE)
        repeat(3) { service.addLoco("Lok $it", it + 1) }
        val first = service.fleet.value.locos.first()
        service.saveLoco(first.copy(name = "BR 218"))
        assertEquals("BR 218", service.fleet.value.locos.first().name)

        service.deleteLoco(first.id)
        assertTrue(service.canAdd(VehicleGroup.LOCOS))
    }

    @Test
    fun proIsUnlimited() = runTest {
        val service = start(LicenseInfo.of("pro"))
        assertNull(service.limit)
        repeat(10) { service.addLoco("Lok $it", it + 1) }
        repeat(10) { service.addWagon(VehicleGroup.FREIGHT, "Eaos") }
        assertEquals(10, service.fleet.value.locos.size)
        assertEquals(10, service.fleet.value.count(VehicleGroup.FREIGHT))
    }

    @Test
    fun basisKeepsTheFreeLimit() = runTest {
        assertEquals(3, start(LicenseInfo.of("basis")).limit)
    }

    @Test
    fun locosAppearInTheLayoutForDriving() = runTest {
        val service = start(LicenseInfo.FREE)
        val loco = service.addLoco("BR 218", 3)
        assertEquals(setOf("Von Hand", "BR 218"), layout.layout.value.locos.map { it.name }.toSet())

        service.deleteLoco(loco.id)
        assertEquals(listOf("Von Hand"), layout.layout.value.locos.map { it.name })
    }

    @Test
    fun loadsSavedVehiclesAndRejectsInvalidInput() = runTest {
        val store = MemoryStore(Fleet(locos = listOf(LocoRecord("fz-lok-1", "V 100", 5))))
        val service = start(LicenseInfo.FREE, store)
        assertEquals("V 100", service.fleet.value.locos.single().name)
        assertEquals("fz-lok-2", service.newId(VehicleGroup.LOCOS))
        assertFailsWith<IllegalArgumentException> { service.addLoco("", 3) }
        assertFailsWith<IllegalArgumentException> { service.addLoco("Null", 0) }
        assertFailsWith<IllegalArgumentException> { service.addWagon(VehicleGroup.FREIGHT, " ") }
    }

    @Test
    fun photosAreStoredAndRemovedWithTheVehicle() = runTest {
        val service = start(LicenseInfo.FREE)
        val loco = service.saveLoco(LocoRecord(service.newId(VehicleGroup.LOCOS), "BR 218", 3))
        assertNull(service.image(loco.id))
        val version = service.imageVersion.value
        service.setImage(loco.id, byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(1, 2, 3), service.image(loco.id))
        assertTrue(service.imageVersion.value > version)
        assertFailsWith<IllegalArgumentException> { service.setImage(loco.id, ByteArray(VehicleService.MAX_IMAGE_BYTES + 1)) }
        service.deleteLoco(loco.id)
        assertNull(service.image(loco.id))
    }

    @Test
    fun trainsAndCategoriesAreFreeAndALocoRunsInOneTrain() = runTest {
        val store = MemoryStore()
        val service = start(LicenseInfo.FREE, store)
        val loco = service.addLoco("BR 218", 3)
        val wagon = service.addWagon(VehicleGroup.PASSENGER, "Bnrz")
        val re = service.saveCategory(TrainCategory(service.newObjectId("zg-"), "RE", "Regional-Express"))
        assertEquals("zg-2", service.newObjectId("zg-"))
        service.saveTrain(TrainSet("zug-1", "RE 1", re.id, listOf(loco.id), listOf(wagon.id, wagon.id)))
        service.saveTrain(TrainSet("zug-2", "RE 2", re.id, listOf(loco.id)))
        assertEquals(emptyList(), service.fleet.value.trains.first { it.id == "zug-1" }.locos)
        assertEquals("zug-2", service.fleet.value.trainOf(3)?.id)
        service.deleteCategory(re.id)
        assertTrue(store.saved!!.trains.all { it.categoryId.isEmpty() })
        service.deleteTrain("zug-1")
        assertEquals(listOf("zug-2"), store.saved!!.trains.map { it.id })
        assertFailsWith<IllegalArgumentException> { service.saveCategory(TrainCategory("zg-9", " ")) }
    }
}
