package de.moba.modules.trackplan.api

import kotlinx.coroutines.flow.SharedFlow
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

    /** Aktuelles Signalbild je Signal-Grundadresse (Index in der Bildliste, 0 = Halt). */
    val signalAspects: StateFlow<Map<Int, Int>>

    /** Schaltet die Weiche oder das Signal einer Kachel in die naechste Stellung. */
    suspend fun operate(tile: TrackTile)

    /** Stellt ein Signal oder ein Zubehoer mit Bildern auf ein bestimmtes Bild, Adressen nach [SignalAddressing]. */
    suspend fun setAspect(tile: TrackTile, aspect: Int)

    /**
     * Schaltet Relais, Licht, Sound oder Bahnuebergang ein oder aus. Mit Impulsdauer (Reiter Steuerung)
     * schaltet es danach von selbst wieder aus. Ein Sound spielt beim Einschalten seine Datei ab.
     */
    suspend fun setOn(tile: TrackTile, on: Boolean)

    /** Klangdateien, die jetzt abgespielt werden sollen (Kennung aus [AccessoryProps.FILE]). */
    val sounds: SharedFlow<String>

    /** Position der Buehne je Grundadresse einer Drehscheibe/Schiebebuehne, soweit bekannt. */
    val tablePositions: StateFlow<Map<Int, Int>>

    /** Grundadressen der Buehnen, die gerade drehen bzw. fahren. */
    val tablesMoving: StateFlow<Set<Int>>

    /**
     * Faehrt die Buehne an Position [position].
     * @throws IllegalStateException, wenn die Buehne belegt und im Reiter Block gesperrt ist
     */
    suspend fun turnTo(tile: TrackTile, position: Int)

    /** Befehl aus dem Reiter Steuern: um 180 Grad drehen oder einen Schritt weiter. */
    suspend fun tableCommand(tile: TrackTile, command: TableCommand)
}

enum class TableCommand(val label: String) {
    TURN_180("180° drehen"),
    STEP_CW("Schritt rechts"),
    STEP_CCW("Schritt links"),
}

class LicenseRequiredException(message: String) : Exception(message)
