package de.moba.modules.vehicles.api

import de.moba.core.model.LocoDynamics
import kotlinx.coroutines.flow.StateFlow

const val VEHICLES_MODULE_ID = "moba.fahrzeuge"

/** Die drei Listen der Fahrzeugverwaltung. Die Speichergrenze ohne Pro gilt je Liste. */
enum class VehicleGroup(val label: String, val singular: String) {
    LOCOS("Lokomotiven", "Lok"),
    FREIGHT("Güterwagen", "Güterwagengattung"),
    PASSENGER("Personenwagen", "Personenwagengattung"),
}

enum class Epoch(val label: String) {
    I("I (bis 1920)"),
    II("II (1920–1950)"),
    III("III (1949–1970)"),
    IV("IV (1970–1990)"),
    V("V (1990–2006)"),
    VI("VI (ab 2006)"),
}

enum class Traction(val label: String) {
    STEAM("Dampf"),
    DIESEL("Diesel"),
    ELECTRIC("Elektro"),
    RAILCAR("Triebwagen"),
    OTHER("Sonstige"),
}

/** Spurweite wie in iTrain; wichtig fuer Bedingungen in Aktionen (z. B. Dreischienengleis H0/H0m). */
enum class Gauge(val label: String) {
    Z("Z (6,5 mm)"),
    N("N (9 mm)"),
    TT("TT (12 mm)"),
    H0("H0 (16,5 mm)"),
    H0M("H0m (12 mm)"),
    H0E("H0e (9 mm)"),
    S("S (22,5 mm)"),
    ZERO("0 (32 mm)"),
    ONE("1 (45 mm)"),
    G("G / IIm (45 mm)"),
}

enum class DecoderProtocol(val label: String) {
    DCC("DCC"),
    MM("Motorola (MM)"),
    MFX("mfx"),
}

/** Eine Lok mit Digitaladresse und Stammdaten. */
data class LocoRecord(
    val id: String,
    val name: String,
    val address: Int,
    /** Baureihe, z. B. "BR 218". */
    val series: String = "",
    /** Bahnverwaltung, z. B. "DB". */
    val railway: String = "",
    val epoch: Epoch? = null,
    val traction: Traction? = null,
    val protocol: DecoderProtocol = DecoderProtocol.DCC,
    val manufacturer: String = "",
    val articleNumber: String = "",
    /** Hoechstgeschwindigkeit des Vorbilds in km/h. */
    val vmax: Int? = null,
    /** Laenge ueber Puffer in mm. */
    val lengthMm: Int? = null,
    /** Funktionsnamen, z. B. 0 = "Licht". */
    val functions: Map<Int, String> = emptyMap(),
    val notes: String = "",
    /** Fuehrerstand, Traegheitssimulation, Rueckmelde-Offset, Reaktionsverzoegerung und Zeitraum. */
    val dynamics: LocoDynamics = LocoDynamics(),
    val gauge: Gauge? = null,
    /** Wagen, die diese Lok als Zug zieht (Ids aus der Wagenliste), z. B. fuer Bedingungen in Aktionen. */
    val consist: List<String> = emptyList(),
)

/** Bauart einer Wagengattung; [code] ist der Kennbuchstabe nach UIC. */
enum class WagonCategory(val label: String, val code: String, val group: VehicleGroup) {
    OPEN("Offener Wagen", "E", VehicleGroup.FREIGHT),
    COVERED("Gedeckter Wagen", "G", VehicleGroup.FREIGHT),
    SLIDING_WALL("Schiebewandwagen", "H", VehicleGroup.FREIGHT),
    REFRIGERATED("Kühlwagen", "I", VehicleGroup.FREIGHT),
    FLAT("Flachwagen", "R", VehicleGroup.FREIGHT),
    FLAT_SPECIAL("Flachwagen, Sonderbauart", "S", VehicleGroup.FREIGHT),
    SLIDING_ROOF("Schiebedachwagen", "T", VehicleGroup.FREIGHT),
    HOPPER("Schüttgutwagen", "F", VehicleGroup.FREIGHT),
    TANK("Kesselwagen", "Z", VehicleGroup.FREIGHT),
    SPECIAL_FREIGHT("Sonderwagen", "U", VehicleGroup.FREIGHT),
    FIRST("Sitzwagen 1. Klasse", "A", VehicleGroup.PASSENGER),
    SECOND("Sitzwagen 2. Klasse", "B", VehicleGroup.PASSENGER),
    MIXED("Sitzwagen 1./2. Klasse", "AB", VehicleGroup.PASSENGER),
    CONTROL("Steuerwagen", "Bf", VehicleGroup.PASSENGER),
    DOUBLE_DECK("Doppelstockwagen", "DB", VehicleGroup.PASSENGER),
    DINING("Speisewagen", "WR", VehicleGroup.PASSENGER),
    SLEEPER("Schlafwagen", "WL", VehicleGroup.PASSENGER),
    COUCHETTE("Liegewagen", "Bc", VehicleGroup.PASSENGER),
    LUGGAGE("Gepäckwagen", "D", VehicleGroup.PASSENGER),
    ;

    companion object {
        fun of(group: VehicleGroup) = entries.filter { it.group == group }
    }
}

/**
 * Eine Wagengattung, z. B. "Eaos" (offener Gueterwagen) oder "Bm 235" (Sitzwagen 2. Klasse).
 * [count] sagt, wie viele Wagen dieser Gattung auf der Anlage laufen.
 */
data class WagonClass(
    val id: String,
    val group: VehicleGroup,
    /** Gattungszeichen, z. B. "Eaos" oder "Bm 235". */
    val code: String,
    val name: String = "",
    val category: WagonCategory? = null,
    val railway: String = "",
    val epoch: Epoch? = null,
    val lengthMm: Int? = null,
    val count: Int = 1,
    val manufacturer: String = "",
    val articleNumber: String = "",
    val notes: String = "",
) {
    init {
        require(group != VehicleGroup.LOCOS) { "Wagengattungen sind Güter- oder Personenwagen" }
    }
}

/** Zuggattung wie in iTrain, z. B. "ICE", "RE" oder "Gag" (Ganzzug); [maxSpeed] in km/h, `null` = ohne Grenze. */
data class TrainCategory(
    val id: String,
    val code: String,
    val name: String = "",
    val passenger: Boolean = true,
    val maxSpeed: Int? = null,
)

/** Ein Zug: Lok(s) und Wagen (Ids der Wagengattungen, auch mehrfach) mit einer Zuggattung. */
data class TrainSet(
    val id: String,
    val name: String,
    val categoryId: String = "",
    val locos: List<String> = emptyList(),
    val wagons: List<String> = emptyList(),
)

/** Alle Fahrzeuge der Anlage. */
data class Fleet(
    val locos: List<LocoRecord> = emptyList(),
    val wagons: List<WagonClass> = emptyList(),
    val categories: List<TrainCategory> = emptyList(),
    val trains: List<TrainSet> = emptyList(),
) {
    /** Laenge eines Zugs aus den Laengen seiner Fahrzeuge in mm; fehlende Laengen zaehlen nicht. */
    fun lengthMm(train: TrainSet): Int =
        train.locos.sumOf { id -> locos.firstOrNull { it.id == id }?.lengthMm ?: 0 } +
            train.wagons.sumOf { id -> wagons.firstOrNull { it.id == id }?.lengthMm ?: 0 }

    /** Der Zug, in dem die Lok mit [address] faehrt, sonst `null`. */
    fun trainOf(address: Int): TrainSet? {
        val loco = locos.firstOrNull { it.address == address } ?: return null
        return trains.firstOrNull { loco.id in it.locos }
    }

    fun count(group: VehicleGroup) = when (group) {
        VehicleGroup.LOCOS -> locos.size
        else -> wagons.count { it.group == group }
    }

    fun wagons(group: VehicleGroup) = wagons.filter { it.group == group }
}

/** Liest und schreibt alle Fahrzeuge auf einmal (Datei auf Windows und Android). */
interface VehicleStore {
    fun load(): Fleet?
    fun save(fleet: Fleet)

    /** Foto eines Fahrzeugs (PNG, JPEG ...), `null` = keins. */
    fun loadImage(id: String): ByteArray? = null

    /** `null` loescht das Foto. */
    fun saveImage(id: String, bytes: ByteArray?) = Unit
}

/**
 * Oeffentliche Schnittstelle des Moduls "Fahrzeuge". Jede Aenderung wird sofort gespeichert.
 * Ohne Pro lassen sich je Liste [limit] Eintraege speichern, mit Pro beliebig viele.
 */
interface VehicleService {
    val fleet: StateFlow<Fleet>

    /** Hoechstzahl je Liste, `null` = unbegrenzt. */
    val limit: Int?

    fun canAdd(group: VehicleGroup): Boolean

    /** Neue Lok anlegen oder bestehende aendern (gleiche [LocoRecord.id]). @throws VehicleLimitException */
    fun saveLoco(loco: LocoRecord): LocoRecord
    fun deleteLoco(id: String)

    /** Neue Wagengattung anlegen oder bestehende aendern. @throws VehicleLimitException */
    fun saveWagon(wagon: WagonClass): WagonClass
    fun deleteWagon(id: String)

    /** Eine freie Id fuer einen neuen Eintrag. */
    fun newId(group: VehicleGroup): String

    /** Zuggattung anlegen oder aendern; frei, ohne Grenze. */
    fun saveCategory(category: TrainCategory): TrainCategory

    /** Loescht die Gattung; Zuege behalten sie nicht. */
    fun deleteCategory(id: String)

    /** Zug anlegen oder aendern; frei, ohne Grenze. */
    fun saveTrain(train: TrainSet): TrainSet
    fun deleteTrain(id: String)

    /** Freie Id fuer eine Gattung ("zg-") oder einen Zug ("zug-"). */
    fun newObjectId(prefix: String): String

    /** Foto eines Fahrzeugs, `null` = keins. */
    fun image(id: String): ByteArray?

    /** Setzt oder loescht (`null`) das Foto; wird sofort gespeichert. @throws IllegalArgumentException wenn zu gross */
    fun setImage(id: String, bytes: ByteArray?)

    /** Zaehlt bei jeder Bildaenderung hoch, damit Ansichten neu laden. */
    val imageVersion: StateFlow<Int>

    companion object {
        /** Groesste erlaubte Bilddatei. */
        const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    }
}

class VehicleLimitException(message: String) : Exception(message)
