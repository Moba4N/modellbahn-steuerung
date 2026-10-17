package de.moba.modules.trackplan.api

/** Bahnhof wie in iTrain: Name, Kuerzel und die Bloecke, die dazugehoeren. */
data class StationDef(
    val id: String,
    val name: String,
    val code: String = "",
    val blocks: List<FeedbackRef> = emptyList(),
)

/**
 * Booster: versorgt einen Teil der Anlage. [stateFeedback] meldet "belegt", wenn der Booster abgeschaltet hat
 * (Kurzschluss); mit [address] laesst er sich schalten (gerade = an, abzweigend = aus).
 */
data class BoosterDef(
    val id: String,
    val name: String,
    val address: Int? = null,
    val stateFeedback: FeedbackRef? = null,
    val blocks: List<FeedbackRef> = emptyList(),
) {
    /** An, solange der Zustandsmelder frei ist und die Schaltadresse nicht auf "aus" steht. */
    fun isOn(occupied: Set<FeedbackRef>, turnouts: Map<Int, Boolean>): Boolean =
        (stateFeedback == null || stateFeedback !in occupied) && (address == null || turnouts[address] != true)
}

/** Bahnhoefe und Booster: gehoeren zur Anlage, aber zu keinem einzelnen Gleisbild. */
data class LayoutObjects(
    val stations: List<StationDef> = emptyList(),
    val boosters: List<BoosterDef> = emptyList(),
)

interface LayoutObjectStore {
    fun load(): LayoutObjects?
    fun save(objects: LayoutObjects)
}

/** Textformat: "BF <id> <name> <kuerzel> <bloecke>" und "BO <id> <name> <adresse> <zustandsmelder> <bloecke>", Tab-getrennt. */
object LayoutObjectCodec {
    private const val HEADER = "# anlagenobjekte v1"

    fun encode(objects: LayoutObjects): String = buildString {
        appendLine(HEADER)
        for (s in objects.stations) line("BF", s.id, s.name, s.code, FeedbackList.format(s.blocks))
        for (b in objects.boosters) {
            line("BO", b.id, b.name, b.address?.toString().orEmpty(), b.stateFeedback?.let { FeedbackList.format(listOf(it)) }.orEmpty(), FeedbackList.format(b.blocks))
        }
    }

    fun decode(text: String): LayoutObjects {
        val stations = mutableListOf<StationDef>()
        val boosters = mutableListOf<BoosterDef>()
        for (raw in text.lineSequence()) {
            if (raw.isBlank() || raw.startsWith("#")) continue
            val f = raw.split('\t')
            fun at(i: Int) = f.getOrElse(i) { "" }
            if (at(1).isEmpty()) continue
            when (f[0]) {
                "BF" -> stations += StationDef(at(1), at(2), at(3), FeedbackList.parse(at(4)))
                "BO" -> boosters += BoosterDef(at(1), at(2), at(3).toIntOrNull(), FeedbackList.parse(at(4)).firstOrNull(), FeedbackList.parse(at(5)))
            }
        }
        return LayoutObjects(stations, boosters)
    }

    private fun StringBuilder.line(vararg fields: String) =
        appendLine(fields.joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') })
}
