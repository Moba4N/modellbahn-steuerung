package de.moba.modules.actions.api

import de.moba.modules.vehicles.api.Gauge
import de.moba.modules.vehicles.api.Traction
import de.moba.modules.vehicles.api.WagonCategory
import kotlinx.coroutines.flow.StateFlow

const val ACTIONS_MODULE_ID = "moba.aktionen"

/** Wie ein Parameter einer Bedingung oder eines Befehls eingegeben wird. */
enum class ParamKind {
    /** Freier Text, z. B. eine Meldung. */
    TEXT,
    NUMBER,

    /** Uhrzeit "07:30". */
    TIME,

    /** s88-Melder bzw. Block "1.3". */
    FEEDBACK,

    /** Lokadresse; die Oberflaeche bietet die bekannten Loks an. */
    LOCO,

    /** Eine Fahrstrasse (Id). */
    ROUTE,

    /** Ein Pendelzug oder eine Zugroute (Id). */
    AUTOMATION,

    /** Auswahl aus [ParamSpec.options]. */
    CHOICE,
}

/** Ein Parameter: [key] im gespeicherten Text, [options] als Wert zu Anzeigetext. */
data class ParamSpec(
    val key: String,
    val label: String,
    val kind: ParamKind,
    val default: String = "",
    val options: List<Pair<String, String>> = emptyList(),
    /** Darf leer bleiben (z. B. "irgendeine Lok"). */
    val optional: Boolean = false,
)

private fun choice(key: String, label: String, vararg options: Pair<String, String>) =
    ParamSpec(key, label, ParamKind.CHOICE, options.first().first, options.toList())

private val BLOCK_PARAM = ParamSpec("block", "Block", ParamKind.FEEDBACK)
private val LOCO_PARAM = ParamSpec("lok", "Lok", ParamKind.LOCO)
private val COMPARE = choice("vergleich", "Vergleich", "ab" to "mindestens", "unter" to "unter")

/** Arten von Bedingungen wie in iTrain, in der Reihenfolge des Aktionen-Menues. */
enum class ConditionType(val label: String, val params: List<ParamSpec>) {
    /** Verknuepft die Unterbedingungen; ohne Unterbedingung ist "und" erfuellt, "oder" nicht. */
    OPERATOR("Operator", listOf(choice("op", "Verknüpfung", "und" to "und (alle)", "oder" to "oder (eine)"))),

    /** Wird einmal ausgewuerfelt, wenn alle anderen Bedingungen erfuellt sind. */
    RANDOM("Zufällig", listOf(ParamSpec("prozent", "Wahrscheinlichkeit (%)", ParamKind.NUMBER, "50"))),
    TIME(
        "Zeit",
        listOf(
            ParamSpec("von", "Von", ParamKind.TIME, "06:00"),
            ParamSpec("bis", "Bis", ParamKind.TIME, "22:00"),
            choice("uhr", "Uhr", "modell" to "Modellzeit", "echt" to "Echte Zeit"),
        ),
    ),
    INTERFACE("Schnittstelle", listOf(choice("zustand", "Zentrale", "verbunden" to "verbunden", "getrennt" to "getrennt"))),
    FEEDBACK(
        "Rückmeldung",
        listOf(ParamSpec("melder", "Melder", ParamKind.FEEDBACK), choice("zustand", "Zustand", "belegt" to "belegt", "frei" to "frei")),
    ),
    ACCESSORY(
        "Zubehör",
        listOf(
            ParamSpec("adresse", "Adresse", ParamKind.NUMBER),
            choice("stellung", "Stellung", "gerade" to "gerade / grün", "abzweigend" to "abzweigend / rot"),
        ),
    ),

    /** Die Adresse gehoert zu einer gestellten Fahrstrasse. */
    ACCESSORY_RESERVED("Zubehör-Reservierung", listOf(ParamSpec("adresse", "Adresse", ParamKind.NUMBER))),

    /** Gleisspannung (Booster) an oder aus. */
    BOOSTER("Booster", listOf(choice("zustand", "Gleisspannung", "an" to "an", "aus" to "aus"))),

    /** Nicht belegt, nicht reserviert und kein Zug darin. */
    BLOCK_FREE("Block frei", listOf(BLOCK_PARAM)),
    BLOCK_OCCUPIED("Block belegt", listOf(BLOCK_PARAM)),
    BLOCK_STATE(
        "Blockzustand",
        listOf(BLOCK_PARAM, choice("zustand", "Zustand", "frei" to "frei", "belegt" to "belegt", "reserviert" to "reserviert", "zug" to "Zug darin")),
    ),

    /** Ein Zug steht seit mindestens [sekunden] im Block. */
    BLOCK_WAIT("Block warten", listOf(BLOCK_PARAM, ParamSpec("sekunden", "Wartet mindestens (s)", ParamKind.NUMBER, "10"))),
    BLOCK_POSITION("Position im Block", listOf(BLOCK_PARAM, choice("position", "Position", "einfahrt" to "fährt ein", "halt" to "hält"))),

    /** Im Block steht ein Zug; mit Lok genau diese. Ohne Block: die Lok steht in irgendeinem Block. */
    TRAIN("Zug", listOf(BLOCK_PARAM.copy(optional = true), LOCO_PARAM.copy(optional = true))),
    TRAIN_DIRECTION("Zugrichtung", listOf(LOCO_PARAM, choice("richtung", "Richtung", "vor" to "vorwärts", "rueck" to "rückwärts"))),
    TRAIN_STATE(
        "Zugzustand",
        listOf(LOCO_PARAM, choice("zustand", "Zustand", "faehrt" to "fährt", "steht" to "steht", "automatik" to "fährt automatisch")),
    ),

    /** Antriebsart der Lok des Zugs im Block. */
    CATEGORY("Gattung", listOf(BLOCK_PARAM, ParamSpec("gattung", "Gattung", ParamKind.CHOICE, Traction.STEAM.name, Traction.entries.map { it.name to it.label }))),

    /** Ein Pendelzug oder eine Zugroute laeuft (oder nicht). */
    TRAIN_ROUTE(
        "Zugroute",
        listOf(ParamSpec("zugroute", "Zugroute", ParamKind.AUTOMATION), choice("zustand", "Zustand", "laeuft" to "läuft", "steht" to "läuft nicht")),
    ),
    GAUGE("Spurweite", listOf(BLOCK_PARAM, ParamSpec("spur", "Spurweite", ParamKind.CHOICE, Gauge.H0.name, Gauge.entries.map { it.name to it.label }))),
    LOCO(
        "Lokomotive",
        listOf(
            LOCO_PARAM,
            choice("pruefen", "Prüfen", "ab" to "Fahrstufe mindestens", "unter" to "Fahrstufe unter", "fn_an" to "Funktion an", "fn_aus" to "Funktion aus"),
            ParamSpec("wert", "Fahrstufe / Funktion", ParamKind.NUMBER, "1"),
        ),
    ),

    /** Der Zug im Block fuehrt einen Wagen dieser Bauart mit ("Wagen im Zug" bei der Lok). */
    WAGON(
        "Wagen",
        listOf(BLOCK_PARAM, ParamSpec("bauart", "Bauart", ParamKind.CHOICE, WagonCategory.OPEN.name, WagonCategory.entries.map { it.name to "${it.label} (${it.code})" })),
    ),
    FUEL("Lokomotive-Brennstoff", listOf(LOCO_PARAM, COMPARE.copy(default = "unter"), ParamSpec("prozent", "Vorrat (%)", ParamKind.NUMBER, "20"))),
    ;

    fun spec(key: String) = params.first { it.key == key }
}

/** Eine Bedingung; [OPERATOR][ConditionType.OPERATOR] hat [children]. [negate] kehrt das Ergebnis um ("nicht"). */
data class Condition(
    val type: ConditionType,
    val params: Map<String, String> = emptyMap(),
    val negate: Boolean = false,
    val children: List<Condition> = emptyList(),
) {
    /** Wert des Parameters oder seine Vorgabe. */
    fun param(key: String): String = params[key]?.takeIf { it.isNotBlank() } ?: type.params.firstOrNull { it.key == key }?.default.orEmpty()

    companion object {
        fun and(vararg children: Condition) = Condition(ConditionType.OPERATOR, mapOf("op" to "und"), children = children.toList())
        fun or(vararg children: Condition) = Condition(ConditionType.OPERATOR, mapOf("op" to "oder"), children = children.toList())
        fun of(type: ConditionType, vararg params: Pair<String, String>) = Condition(type, params.toMap())
    }
}

/** Was eine Aktion tun kann. */
enum class CommandType(val label: String, val params: List<ParamSpec>) {
    ACCESSORY(
        "Zubehör schalten",
        listOf(ParamSpec("adresse", "Adresse", ParamKind.NUMBER), choice("stellung", "Stellung", "gerade" to "gerade / grün", "abzweigend" to "abzweigend / rot")),
    ),
    ROUTE_SET("Fahrstraße stellen", listOf(ParamSpec("fahrstrasse", "Fahrstraße", ParamKind.ROUTE))),
    ROUTE_RELEASE("Fahrstraße auflösen", listOf(ParamSpec("fahrstrasse", "Fahrstraße", ParamKind.ROUTE))),
    AUTOMATION_START("Zugroute starten", listOf(ParamSpec("zugroute", "Zugroute", ParamKind.AUTOMATION))),
    AUTOMATION_STOP("Zugroute anhalten", listOf(ParamSpec("zugroute", "Zugroute", ParamKind.AUTOMATION))),
    LOCO_SPEED(
        "Lok fahren",
        listOf(LOCO_PARAM, ParamSpec("fahrstufe", "Fahrstufe (0–126)", ParamKind.NUMBER, "40"), choice("richtung", "Richtung", "vor" to "vorwärts", "rueck" to "rückwärts")),
    ),
    LOCO_STOP("Lok anhalten", listOf(LOCO_PARAM)),
    LOCO_FUNCTION(
        "Lokfunktion",
        listOf(LOCO_PARAM, ParamSpec("funktion", "Funktion", ParamKind.NUMBER, "0"), choice("zustand", "Schalten", "an" to "an", "aus" to "aus")),
    ),
    TRACK_POWER("Gleisspannung", listOf(choice("zustand", "Gleisspannung", "an" to "an", "aus" to "aus"))),
    EMERGENCY_STOP("Nothalt", emptyList()),
    WAIT("Warten", listOf(ParamSpec("sekunden", "Sekunden", ParamKind.NUMBER, "5"))),
    CLOCK("Modellzeit-Uhr", listOf(choice("zustand", "Uhr", "start" to "starten", "stop" to "anhalten"))),
    REFUEL("Auftanken", listOf(LOCO_PARAM)),
    BLOCK_RELEASE("Block freigeben", listOf(BLOCK_PARAM)),
    MESSAGE("Meldung", listOf(ParamSpec("text", "Text", ParamKind.TEXT, "Hallo"))),
}

data class Command(val type: CommandType, val params: Map<String, String> = emptyMap()) {
    fun param(key: String): String = params[key]?.takeIf { it.isNotBlank() } ?: type.params.firstOrNull { it.key == key }?.default.orEmpty()

    companion object {
        fun of(type: CommandType, vararg params: Pair<String, String>) = Command(type, params.toMap())
    }
}

/**
 * Eine Aktion: Sobald [condition] erfuellt wird (Wechsel von "nicht erfuellt" zu "erfuellt"), fuehrt die App
 * [commands] der Reihe nach aus. Von Hand laesst sie sich jederzeit starten.
 */
data class ActionRule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val condition: Condition = Condition.and(),
    val commands: List<Command> = emptyList(),
)

/** Alles, was das Modul speichert: Aktionen und der Brennstoffvorrat je Lokadresse in Prozent. */
data class ActionBook(
    val rules: List<ActionRule> = emptyList(),
    val fuel: Map<Int, Double> = emptyMap(),
)

interface ActionStore {
    fun load(): ActionBook?
    fun save(book: ActionBook)
}

/** Ein Eintrag im Protokoll der Aktionen. */
data class ActionLogEntry(val millis: Long, val text: String, val error: Boolean = false)

/**
 * Oeffentliche Schnittstelle des Moduls "Aktionen". Anlegen und Bearbeiten ist immer moeglich,
 * ausfuehren (von selbst oder von Hand) braucht Pro.
 */
interface ActionService {
    val book: StateFlow<ActionBook>

    /** Ids der Aktionen, deren Bedingung gerade erfuellt ist (Zufall zaehlt als erfuellt). */
    val satisfied: StateFlow<Set<String>>

    /** Ids der Aktionen, deren Befehle gerade laufen. */
    val running: StateFlow<Set<String>>

    /** Neueste Eintraege zuerst, hoechstens [MAX_LOG]. */
    val log: StateFlow<List<ActionLogEntry>>

    val canRun: Boolean

    fun save(rule: ActionRule)
    fun delete(id: String)
    fun setEnabled(id: String, enabled: Boolean)
    fun newId(): String

    /** Fuehrt die Befehle sofort aus, ohne die Bedingung zu pruefen. @throws ActionLicenseException ohne Pro */
    fun run(id: String)

    /** Brennstoff der Lok mit [address] in Prozent; ohne Eintrag voll (100). */
    fun fuel(address: Int): Double = book.value.fuel[address] ?: 100.0
    fun refuel(address: Int)

    companion object {
        const val MAX_LOG = 50
    }
}

class ActionLicenseException(message: String) : Exception(message)

/** Wie lange der Vorrat bei voller Fahrt reicht, in Minuten; `null` = verbraucht keinen Brennstoff (Elektro). */
fun Traction?.fuelMinutes(): Double? = when (this) {
    Traction.STEAM -> 20.0
    Traction.DIESEL, Traction.RAILCAR -> 60.0
    else -> null
}
