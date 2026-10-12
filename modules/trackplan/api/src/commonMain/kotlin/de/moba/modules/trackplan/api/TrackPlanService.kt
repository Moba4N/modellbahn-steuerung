package de.moba.modules.trackplan.api

import kotlinx.coroutines.flow.StateFlow

const val TRACKPLAN_MODULE_ID = "moba.gleisbild"

/** Liest und schreibt alle Gleisbilder auf einmal (Datei auf Windows und Android). */
interface TrackPlanStore {
    fun load(): List<TrackPlan>
    fun save(plans: List<TrackPlan>)
}

/**
 * Oeffentliche Schnittstelle des Moduls "Gleisbild": Gleisplaene bearbeiten, anzeigen und
 * Weichen darin schalten. Abrufbar mit `ctx.service<TrackPlanService>()`.
 *
 * Bearbeiten geht immer; [save] braucht ein Abo. Ohne Pro ist [maxVisiblePlans] 1, mit Pro 3.
 */
interface TrackPlanService {
    val plans: StateFlow<List<TrackPlan>>

    /** Gibt es Aenderungen, die noch nicht gespeichert sind? */
    val unsaved: StateFlow<Boolean>

    /** Ids der gleichzeitig angezeigten Gleisbilder, hoechstens [maxVisiblePlans]. */
    val visiblePlans: StateFlow<List<String>>

    val canSave: Boolean
    val maxVisiblePlans: Int

    fun update(plan: TrackPlan)
    fun addPlan(name: String): TrackPlan
    fun removePlan(id: String)

    /** @throws LicenseRequiredException ohne Abo */
    fun save()

    /** Blendet ein Gleisbild ein oder aus. @throws LicenseRequiredException, wenn schon [maxVisiblePlans] sichtbar sind */
    fun toggleVisible(id: String)

    /** Von der Zentrale gemeldete Weichenstellungen (Adresse zu abzweigend). */
    val turnouts: StateFlow<Map<Int, Boolean>>

    /** Belegte Rueckmelder als (s88-Modul, Kontakt). */
    val occupied: StateFlow<Set<Pair<Int, Int>>>

    /** Schaltet die Weiche oder das Signal einer Kachel in die naechste Stellung. */
    suspend fun operate(tile: TrackTile)
}

class LicenseRequiredException(message: String) : Exception(message)
