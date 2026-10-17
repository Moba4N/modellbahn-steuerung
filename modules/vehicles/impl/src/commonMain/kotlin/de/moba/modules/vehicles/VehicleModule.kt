package de.moba.modules.vehicles

import de.moba.core.model.Loco
import de.moba.core.model.LayoutRepository
import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.MobaModule
import de.moba.core.runtime.ModuleContext
import de.moba.core.runtime.SemVer
import de.moba.core.runtime.provide
import de.moba.modules.vehicles.api.Fleet
import de.moba.modules.vehicles.api.LocoRecord
import de.moba.modules.vehicles.api.VEHICLES_MODULE_ID
import de.moba.modules.vehicles.api.VehicleGroup
import de.moba.modules.vehicles.api.VehicleLimitException
import de.moba.modules.vehicles.api.TrainCategory
import de.moba.modules.vehicles.api.TrainSet
import de.moba.modules.vehicles.api.VehicleService
import de.moba.modules.vehicles.api.VehicleStore
import de.moba.modules.vehicles.api.WagonClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Haelt die Fahrzeuge ueber Zentralenwechsel hinweg. Die App erzeugt ihn einmal und reicht ihn
 * jedem neu gestarteten [VehicleModule] mit. Jede Aenderung wird sofort in [store] geschrieben.
 */
class VehicleRepository(private val store: VehicleStore?) {
    internal val fleet = MutableStateFlow(runCatching { store?.load() }.getOrNull() ?: Fleet())

    private val images = HashMap<String, ByteArray?>()
    internal val imageVersion = MutableStateFlow(0)

    internal fun image(id: String): ByteArray? {
        if (id !in images) images[id] = runCatching { store?.loadImage(id) }.getOrNull()
        return images[id]
    }

    internal fun setImage(id: String, bytes: ByteArray?) {
        store?.saveImage(id, bytes)
        images[id] = bytes
        imageVersion.value++
    }

    internal fun change(transform: (Fleet) -> Fleet) {
        val next = transform(fleet.value)
        store?.save(next)
        fleet.value = next
    }
}

/**
 * Modul "Fahrzeuge" (M2): Loks sowie Gueter- und Personenwagengattungen anlegen.
 * Kostenlos lassen sich je Liste [FREE_LIMIT] Eintraege speichern, mit Pro beliebig viele.
 * Die Loks erscheinen automatisch im Fahrpult.
 */
class VehicleModule(private val repository: VehicleRepository) : MobaModule {
    override val id = VEHICLES_MODULE_ID
    override val displayName = "Fahrzeuge"
    override val version = SemVer(1, 0)
    override val apiVersion = SemVer(1, 0)
    override val licenseKey: String? = null

    override suspend fun start(ctx: ModuleContext) {
        val service = VehicleServiceImpl(repository, ctx.license)
        ctx.scope.launch { repository.fleet.collect { syncLocos(ctx.layout, it) } }
        ctx.provide<VehicleService>(service)
    }

    companion object {
        const val FREE_LIMIT = 3

        /** Unbegrenzt viele Fahrzeuge gibt es ab Pro. */
        val UNLIMITED_LICENSE_KEYS = listOf("pro", "club")
        internal const val ID_PREFIX = "fz-"
    }
}

/** Uebernimmt die angelegten Loks ins Anlagenmodell, damit das Fahrpult sie mit Namen zeigt. */
internal fun syncLocos(layout: LayoutRepository, fleet: Fleet) = layout.update { current ->
    val own = fleet.locos.map { Loco(it.id, it.name, it.address, it.functions, it.dynamics, it.vmax) }
    current.copy(locos = current.locos.filterNot { it.id.startsWith(VehicleModule.ID_PREFIX) } + own)
}

internal class VehicleServiceImpl(
    private val repository: VehicleRepository,
    license: LicenseInfo,
) : VehicleService {
    override val fleet: StateFlow<Fleet> = repository.fleet

    override val limit: Int? =
        if (VehicleModule.UNLIMITED_LICENSE_KEYS.any(license::isUnlocked)) null else VehicleModule.FREE_LIMIT

    override fun canAdd(group: VehicleGroup): Boolean {
        val l = limit ?: return true
        return fleet.value.count(group) < l
    }

    private fun checkLimit(group: VehicleGroup) {
        if (!canAdd(group)) {
            throw VehicleLimitException(
                "Kostenlos lassen sich $limit ${group.label} speichern. Mit Pro sind es beliebig viele.",
            )
        }
    }

    override fun saveLoco(loco: LocoRecord): LocoRecord {
        require(loco.name.isNotBlank()) { "Bitte einen Namen eingeben." }
        require(loco.address in 1..10239) { "Die Adresse muss zwischen 1 und 10239 liegen." }
        val exists = fleet.value.locos.any { it.id == loco.id }
        if (!exists) checkLimit(VehicleGroup.LOCOS)
        repository.change { f ->
            f.copy(locos = if (exists) f.locos.map { if (it.id == loco.id) loco else it } else f.locos + loco)
        }
        return loco
    }

    override fun deleteLoco(id: String) {
        repository.change { f -> f.copy(locos = f.locos.filterNot { it.id == id }) }
        if (repository.image(id) != null) repository.setImage(id, null)
    }

    override fun saveWagon(wagon: WagonClass): WagonClass {
        require(wagon.code.isNotBlank()) { "Bitte ein Gattungszeichen eingeben, z. B. Eaos." }
        require(wagon.count >= 1) { "Die Anzahl muss mindestens 1 sein." }
        val exists = fleet.value.wagons.any { it.id == wagon.id }
        if (!exists) checkLimit(wagon.group)
        repository.change { f ->
            f.copy(wagons = if (exists) f.wagons.map { if (it.id == wagon.id) wagon else it } else f.wagons + wagon)
        }
        return wagon
    }

    override fun deleteWagon(id: String) {
        repository.change { f -> f.copy(wagons = f.wagons.filterNot { it.id == id }) }
        if (repository.image(id) != null) repository.setImage(id, null)
    }

    override val imageVersion: StateFlow<Int> = repository.imageVersion

    override fun image(id: String): ByteArray? = repository.image(id)

    override fun setImage(id: String, bytes: ByteArray?) {
        require(bytes == null || bytes.size <= VehicleService.MAX_IMAGE_BYTES) { "Das Bild ist zu groß (höchstens 8 MB)." }
        repository.setImage(id, bytes)
    }

    override fun saveCategory(category: TrainCategory): TrainCategory {
        require(category.code.isNotBlank()) { "Bitte ein Kürzel eingeben, z. B. RE." }
        repository.change { f ->
            val exists = f.categories.any { it.id == category.id }
            f.copy(categories = if (exists) f.categories.map { if (it.id == category.id) category else it } else f.categories + category)
        }
        return category
    }

    override fun deleteCategory(id: String) = repository.change { f ->
        f.copy(
            categories = f.categories.filterNot { it.id == id },
            trains = f.trains.map { if (it.categoryId == id) it.copy(categoryId = "") else it },
        )
    }

    override fun saveTrain(train: TrainSet): TrainSet {
        require(train.name.isNotBlank()) { "Bitte einen Namen eingeben." }
        repository.change { f ->
            // Eine Lok faehrt nur in einem Zug: aus anderen Zuegen herausnehmen
            val others = f.trains.filter { it.id != train.id }.map { t -> t.copy(locos = t.locos - train.locos.toSet()) }
            val exists = f.trains.any { it.id == train.id }
            f.copy(trains = if (exists) f.trains.map { t -> if (t.id == train.id) train else others.first { it.id == t.id } } else others + train)
        }
        return train
    }

    override fun deleteTrain(id: String) = repository.change { f -> f.copy(trains = f.trains.filterNot { it.id == id }) }

    override fun newObjectId(prefix: String): String {
        val used = fleet.value.categories.map { it.id } + fleet.value.trains.map { it.id }
        return generateSequence(1) { it + 1 }.map { "$prefix$it" }.first { it !in used }
    }

    override fun newId(group: VehicleGroup): String {
        val prefix = VehicleModule.ID_PREFIX + when (group) {
            VehicleGroup.LOCOS -> "lok-"
            VehicleGroup.FREIGHT -> "gw-"
            VehicleGroup.PASSENGER -> "pw-"
        }
        val used = fleet.value.locos.map { it.id } + fleet.value.wagons.map { it.id }
        return generateSequence(1) { it + 1 }.map { "$prefix$it" }.first { it !in used }
    }
}
