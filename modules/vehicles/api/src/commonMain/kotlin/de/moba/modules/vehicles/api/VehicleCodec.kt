package de.moba.modules.vehicles.api

/**
 * Textformat fuer gespeicherte Fahrzeuge, eine Zeile je Eintrag, Felder durch Tabulator getrennt:
 *
 * ```
 * MOBA-FAHRZEUGE 1
 * lok    <id> <name> <adresse> <baureihe> <bahn> <epoche> <antrieb> <protokoll> <hersteller> <art.-nr.> <vmax> <laenge> <notiz>
 * fn     <lok-id> <nummer> <name>
 * wagen  <id> <gruppe> <gattung> <name> <bauart> <bahn> <epoche> <laenge> <anzahl> <hersteller> <art.-nr.> <notiz>
 * ```
 * Leere Felder stehen fuer "nicht gesetzt". Unbekannte Zeilen und Werte werden beim Lesen uebersprungen.
 */
object VehicleCodec {
    private const val HEADER = "MOBA-FAHRZEUGE 1"

    fun encode(fleet: Fleet): String = buildString {
        appendLine(HEADER)
        for (l in fleet.locos) {
            line(
                "lok", l.id, l.name, l.address, l.series, l.railway, l.epoch?.name, l.traction?.name, l.protocol.name,
                l.manufacturer, l.articleNumber, l.vmax, l.lengthMm, l.notes,
            )
            for ((n, label) in l.functions.toSortedMap()) line("fn", l.id, n, label)
        }
        for (w in fleet.wagons) {
            line(
                "wagen", w.id, w.group.name, w.code, w.name, w.category?.name, w.railway, w.epoch?.name, w.lengthMm,
                w.count, w.manufacturer, w.articleNumber, w.notes,
            )
        }
    }

    private fun StringBuilder.line(vararg fields: Any?) {
        appendLine(fields.joinToString("\t") { escape(it?.toString().orEmpty()) })
    }

    fun decode(text: String): Fleet {
        val lines = text.lines()
        require(lines.firstOrNull()?.trim() == HEADER) { "Keine Fahrzeug-Datei" }
        val locos = mutableListOf<LocoRecord>()
        val wagons = mutableListOf<WagonClass>()
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val f = line.split('\t').map(::unescape)
            fun at(i: Int) = f.getOrElse(i) { "" }
            runCatching {
                when (f[0]) {
                    "lok" -> locos += LocoRecord(
                        id = at(1),
                        name = at(2),
                        address = at(3).toInt(),
                        series = at(4),
                        railway = at(5),
                        epoch = Epoch.entries.firstOrNull { it.name == at(6) },
                        traction = Traction.entries.firstOrNull { it.name == at(7) },
                        protocol = DecoderProtocol.entries.firstOrNull { it.name == at(8) } ?: DecoderProtocol.DCC,
                        manufacturer = at(9),
                        articleNumber = at(10),
                        vmax = at(11).toIntOrNull(),
                        lengthMm = at(12).toIntOrNull(),
                        notes = at(13),
                    )
                    "fn" -> {
                        val i = locos.indexOfFirst { it.id == at(1) }
                        if (i >= 0) locos[i] = locos[i].copy(functions = locos[i].functions + (at(2).toInt() to at(3)))
                    }
                    "wagen" -> wagons += WagonClass(
                        id = at(1),
                        group = VehicleGroup.valueOf(at(2)),
                        code = at(3),
                        name = at(4),
                        category = WagonCategory.entries.firstOrNull { it.name == at(5) },
                        railway = at(6),
                        epoch = Epoch.entries.firstOrNull { it.name == at(7) },
                        lengthMm = at(8).toIntOrNull(),
                        count = at(9).toIntOrNull() ?: 1,
                        manufacturer = at(10),
                        articleNumber = at(11),
                        notes = at(12),
                    )
                }
            }
        }
        return Fleet(locos, wagons)
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
