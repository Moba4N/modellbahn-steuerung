package de.moba.modules.actions

import de.moba.core.station.Direction
import de.moba.core.station.LocoState
import de.moba.modules.actions.api.Condition
import de.moba.modules.actions.api.ConditionType
import de.moba.modules.vehicles.api.Fleet
import de.moba.modules.vehicles.api.LocoRecord

/** Ein s88-Melder bzw. Block als (Modul, Kontakt). */
internal typealias Block = Pair<Int, Int>

/** Alles, was Bedingungen in einem Augenblick pruefen koennen. */
internal data class World(
    val now: Long = 0,
    /** Modellzeit in Minuten seit Mitternacht; `null` ohne Gleisbild-Modul. */
    val modelMinutes: Double? = null,
    val wallMinutes: Int = 0,
    val connected: Boolean = true,
    val trackPower: Boolean = false,
    val feedback: Set<Block> = emptySet(),
    val turnouts: Map<Int, Boolean> = emptyMap(),
    /** Adressen in gestellten Fahrstrassen. */
    val reservedAddresses: Set<Int> = emptySet(),
    val reservedBlocks: Set<Block> = emptySet(),
    val trains: Map<Block, Int> = emptyMap(),
    val locos: Map<Int, LocoState> = emptyMap(),
    /** Seit wann eine Lok in einem Block steht (Fahrstufe 0). */
    val stoppedSince: Map<Int, Long> = emptyMap(),
    /** Loks laufender Pendelzuege und Zugrouten. */
    val automatedLocos: Set<Int> = emptySet(),
    /** Ids laufender Pendelzuege und Zugrouten. */
    val runningAutomations: Set<String> = emptySet(),
    val fleet: Fleet = Fleet(),
    val fuel: Map<Int, Double> = emptyMap(),
    /** Booster nach Id und Name (klein geschrieben): an oder aus. */
    val boosters: Map<String, Boolean> = emptyMap(),
) {
    fun speed(address: Int) = locos[address]?.speed ?: 0
    fun record(address: Int): LocoRecord? = fleet.locos.firstOrNull { it.address == address }
}

internal object Conditions {
    /**
     * Wertet [c] aus. [dice] wuerfelt eine Bedingung "Zufällig" aus; `null` = Zufall gilt als erfuellt
     * (fuer die Anzeige und um den Wechsel zu "erfuellt" zu erkennen).
     */
    fun eval(c: Condition, w: World, dice: ((Int) -> Boolean)? = null): Boolean {
        if (c.type == ConditionType.RANDOM && dice == null) return true
        return raw(c, w, dice) != c.negate
    }

    private fun block(c: Condition): Block? = parseBlock(c.param("block"))

    fun parseBlock(text: String): Block? {
        val p = text.trim().split('.', ':', '/')
        if (p.size != 2) return null
        return (p[0].toIntOrNull() ?: return null) to (p[1].toIntOrNull() ?: return null)
    }

    private fun minutes(text: String): Int? {
        val p = text.trim().split(':')
        val h = p.getOrNull(0)?.toIntOrNull() ?: return null
        val m = p.getOrNull(1)?.toIntOrNull() ?: 0
        return (h * 60 + m).takeIf { h in 0..24 && m in 0..59 }
    }

    private fun Condition.int(key: String) = param(key).trim().toIntOrNull()

    private fun raw(c: Condition, w: World, dice: ((Int) -> Boolean)?): Boolean {
        fun trainIn(): Int? = block(c)?.let { w.trains[it] }
        return when (c.type) {
            ConditionType.OPERATOR ->
                if (c.param("op") == "oder") c.children.any { eval(it, w, dice) } else c.children.all { eval(it, w, dice) }
            ConditionType.RANDOM -> dice!!((c.int("prozent") ?: 50).coerceIn(0, 100))
            ConditionType.TIME -> {
                val now = if (c.param("uhr") == "echt") w.wallMinutes.toDouble() else w.modelMinutes ?: return false
                val from = minutes(c.param("von")) ?: return false
                val to = minutes(c.param("bis")) ?: return false
                if (from <= to) now >= from && now < to else now >= from || now < to
            }
            ConditionType.INTERFACE -> w.connected == (c.param("zustand") == "verbunden")
            ConditionType.FEEDBACK -> {
                val f = parseBlock(c.param("melder")) ?: return false
                (f in w.feedback) == (c.param("zustand") == "belegt")
            }
            ConditionType.ACCESSORY -> {
                val thrown = w.turnouts[c.int("adresse") ?: return false] ?: return false
                thrown == (c.param("stellung") == "abzweigend")
            }
            ConditionType.ACCESSORY_RESERVED -> (c.int("adresse") ?: return false) in w.reservedAddresses
            ConditionType.BOOSTER -> {
                val name = c.param("booster").trim().lowercase()
                val on = w.trackPower && (name.isEmpty() || (w.boosters[name] ?: return false))
                on == (c.param("zustand") == "an")
            }
            ConditionType.BLOCK_FREE -> block(c)?.let { free(it, w) } ?: false
            ConditionType.BLOCK_OCCUPIED -> block(c)?.let { it in w.feedback } ?: false
            ConditionType.BLOCK_STATE -> {
                val b = block(c) ?: return false
                when (c.param("zustand")) {
                    "belegt" -> b in w.feedback
                    "reserviert" -> b in w.reservedBlocks
                    "zug" -> b in w.trains
                    else -> free(b, w)
                }
            }
            ConditionType.BLOCK_WAIT -> {
                val loco = trainIn() ?: return false
                val since = w.stoppedSince[loco] ?: return false
                w.now - since >= (c.int("sekunden") ?: 0) * 1000L
            }
            ConditionType.BLOCK_POSITION -> {
                val loco = trainIn() ?: return false
                (w.speed(loco) == 0) == (c.param("position") == "halt")
            }
            ConditionType.TRAIN -> {
                val loco = c.int("lok")
                val b = block(c)
                when {
                    b != null -> w.trains[b]?.let { loco == null || it == loco } ?: false
                    loco != null -> loco in w.trains.values
                    else -> w.trains.isNotEmpty()
                }
            }
            ConditionType.TRAIN_DIRECTION -> {
                val loco = c.int("lok") ?: return false
                val dir = w.locos[loco]?.direction ?: Direction.FORWARD
                (dir == Direction.FORWARD) == (c.param("richtung") == "vor")
            }
            ConditionType.TRAIN_STATE -> {
                val loco = c.int("lok") ?: return false
                when (c.param("zustand")) {
                    "automatik" -> loco in w.automatedLocos
                    "steht" -> w.speed(loco) == 0
                    else -> w.speed(loco) > 0
                }
            }
            ConditionType.CATEGORY -> w.record(trainIn() ?: return false)?.traction?.name == c.param("gattung")
            ConditionType.TRAIN_ROUTE -> {
                val id = c.param("zugroute").ifBlank { return false }
                (id in w.runningAutomations) == (c.param("zustand") == "laeuft")
            }
            ConditionType.GAUGE -> w.record(trainIn() ?: return false)?.gauge?.name == c.param("spur")
            ConditionType.LOCO -> {
                val loco = c.int("lok") ?: return false
                val value = c.int("wert") ?: return false
                val state = w.locos[loco]
                when (c.param("pruefen")) {
                    "unter" -> w.speed(loco) < value
                    "fn_an" -> state?.functions?.contains(value) ?: false
                    "fn_aus" -> state?.functions?.contains(value) != true
                    else -> w.speed(loco) >= value
                }
            }
            ConditionType.WAGON -> {
                val record = w.record(trainIn() ?: return false) ?: return false
                val wagons = record.consist + w.fleet.trains.filter { record.id in it.locos }.flatMap { it.wagons }
                wagons.any { id -> w.fleet.wagons.firstOrNull { it.id == id }?.category?.name == c.param("bauart") }
            }
            ConditionType.FUEL -> {
                val loco = c.int("lok") ?: return false
                val percent = c.int("prozent") ?: return false
                val fuel = w.fuel[loco] ?: 100.0
                if (c.param("vergleich") == "ab") fuel >= percent else fuel < percent
            }
        }
    }

    private fun free(b: Block, w: World) = b !in w.feedback && b !in w.reservedBlocks && b !in w.trains
}
