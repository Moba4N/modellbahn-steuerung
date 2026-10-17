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

    /** Legt eine Kopie des Gleisbilds [id] an ("Name (Kopie)") und haengt sie hinten an. */
    fun duplicatePlan(id: String): TrackPlan

    /** Verschiebt das Gleisbild [id] in der Reihenfolge um [delta] Plaetze (negativ = nach vorn). */
    fun movePlan(id: String, delta: Int)

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

    /** Stellt eine Weiche oder einen Ausgang direkt (true = abzweigend/rot), z. B. aus dem Kontextmenue. */
    suspend fun setTurnout(address: Int, thrown: Boolean)

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

    // ---------------------------------------------------------- Bloecke und Zuege (wie in iTrain)

    /** Welcher Zug (Lokadresse) in welchem Block steht. Ein Block ist ein Rueckmelder, siehe [TrackTile.block]. */
    val trains: StateFlow<Map<FeedbackRef, Int>>

    /** Reservierte Bloecke, z. B. von einer gestellten Fahrstrasse. */
    val reservations: StateFlow<Map<FeedbackRef, BlockReservation>>

    /** Setzt einen Zug in einen Block; steht er schon woanders, wechselt er hierher. @throws BlockException, wenn dort ein anderer Zug steht */
    fun placeTrain(block: FeedbackRef, address: Int)

    /** Nimmt den Zug aus dem Block (die Lok bleibt, wo sie ist, das Gleisbild vergisst sie nur). */
    fun removeTrain(block: FeedbackRef)

    /** Verschiebt den Zug von [from] nach [to]. @throws BlockException, wenn in [to] ein anderer Zug steht */
    fun moveTrain(from: FeedbackRef, to: FeedbackRef)

    /** Reserviert alle [blocks] auf einmal oder keinen. @throws BlockException, wenn einer schon anders reserviert ist */
    fun reserve(blocks: Collection<FeedbackRef>, reservation: BlockReservation)

    /** Gibt einen Block frei. */
    fun release(block: FeedbackRef)

    /** Gibt alle Bloecke frei, die [owner] reserviert hat, z. B. beim Aufloesen einer Fahrstrasse. */
    fun releaseOwner(owner: String)

    /** Gibt alle Bloecke frei. */
    fun releaseAll()

    // ---------------------------------------------------------- Messung und Modellzeit

    /** Letzte Messung je Messstrecke, Schluessel "<gleisbild-id>/<x>,<y>" (siehe [measureKey]). */
    val measurements: StateFlow<Map<String, SpeedMeasurement>>

    val clock: StateFlow<ModelClockState>

    /** Stellt die Modellzeit auf [hours]:[minutes]. */
    fun setClock(hours: Int, minutes: Int)

    /** Zeitraffer, z. B. 6 = sechsmal so schnell wie echt (1 bis [ModelClockState.MAX_FACTOR]). */
    fun setClockFactor(factor: Int)

    fun startClock()
    fun stopClock()
}

fun measureKey(planId: String, tile: TrackTile) = "$planId/${tile.x},${tile.y}"

/** Wer einen Block reserviert hat: [owner] als Kennung (z. B. "fs:<id>"), [label] fuer die Anzeige. */
data class BlockReservation(val owner: String, val label: String)

class BlockException(message: String) : Exception(message)

enum class TableCommand(val label: String) {
    TURN_180("180° drehen"),
    STEP_CW("Schritt rechts"),
    STEP_CCW("Schritt links"),
}

class LicenseRequiredException(message: String) : Exception(message)
