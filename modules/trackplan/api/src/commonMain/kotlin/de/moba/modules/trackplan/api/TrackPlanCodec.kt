package de.moba.modules.trackplan.api

/**
 * Textformat fuer gespeicherte Gleisbilder, eine Zeile je Plan bzw. Kachel, Felder durch Tabulator getrennt:
 *
 * ```
 * MOBA-GLEISBILD 1
 * plan  <id>  <name>  <breite>  <hoehe>
 * tile  <x>  <y>  <FORM>  <drehung>  <adresse>  <s88-modul>  <s88-kontakt>  <text>
 * ```
 * Leere Felder stehen fuer "nicht gesetzt". Unbekannte Formen werden beim Lesen uebersprungen.
 */
object TrackPlanCodec {
    private const val HEADER = "MOBA-GLEISBILD 1"

    fun encode(plans: List<TrackPlan>): String = buildString {
        appendLine(HEADER)
        for (plan in plans) {
            appendLine(listOf("plan", escape(plan.id), escape(plan.name), plan.width, plan.height).joinToString("\t"))
            for (t in plan.tiles.sortedWith(compareBy({ it.y }, { it.x }))) {
                appendLine(
                    listOf(
                        "tile", t.x, t.y, t.shape.name, t.rotation,
                        t.address ?: "", t.feedbackModule ?: "", t.feedbackContact ?: "", escape(t.text),
                    ).joinToString("\t"),
                )
            }
        }
    }

    fun decode(text: String): List<TrackPlan> {
        val lines = text.lines()
        require(lines.firstOrNull()?.trim() == HEADER) { "Keine Gleisbild-Datei" }
        val plans = mutableListOf<TrackPlan>()
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val f = line.split('\t')
            when (f[0]) {
                "plan" -> plans += TrackPlan(
                    id = unescape(f[1]),
                    name = unescape(f[2]),
                    width = f[3].toInt(),
                    height = f[4].toInt(),
                )
                "tile" -> {
                    val plan = plans.removeLastOrNull() ?: continue
                    val shape = TrackShape.entries.firstOrNull { it.name == f[3] }
                    plans += if (shape == null) plan else plan.copy(
                        tiles = plan.tiles + TrackTile(
                            x = f[1].toInt(),
                            y = f[2].toInt(),
                            shape = shape,
                            rotation = f[4].toInt().mod(8),
                            address = f.getOrNull(5)?.toIntOrNull(),
                            feedbackModule = f.getOrNull(6)?.toIntOrNull(),
                            feedbackContact = f.getOrNull(7)?.toIntOrNull(),
                            text = unescape(f.getOrNull(8).orEmpty()),
                        ),
                    )
                }
            }
        }
        return plans
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unescape(s: String) = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                append(
                    when (s[i + 1]) {
                        't' -> '\t'
                        'n' -> '\n'
                        else -> s[i + 1]
                    },
                )
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
