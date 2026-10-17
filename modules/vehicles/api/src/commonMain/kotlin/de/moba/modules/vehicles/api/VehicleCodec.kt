package de.moba.modules.vehicles.api

import de.moba.core.model.LocoDynamics

/**
 * Textformat fuer gespeicherte Fahrzeuge, eine Zeile je Eintrag, Felder durch Tabulator getrennt:
 *
 * ```
 * MOBA-FAHRZEUGE 1
 * lok    <id> <name> <adresse> <baureihe> <bahn> <epoche> <antrieb> <protokoll> <hersteller> <art.-nr.> <vmax> <laenge> <notiz> <spurweite> <wagen-ids mit Komma>
 * fn     <lok-id> <nummer> <name>
 * fahr   <lok-id> <fuehrerstand 0/1> <traegheit 0/1> <anfahren s> <bremsen s> <zeitraum ms> <offset mm> <reaktion ms>
 * wagen  <id> <gruppe> <gattung> <name> <bauart> <bahn> <epoche> <laenge> <anzahl> <hersteller> <art.-nr.> <notiz>
 * zg     <id> <kuerzel> <name> <p = Personen, g = Gueter> <vmax>
 * zug    <id> <name> <zuggattung-id> <lok-ids mit Komma> <wagen-ids mit Komma>
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
                l.manufacturer, l.articleNumber, l.vmax, l.lengthMm, l.notes, l.gauge?.name, l.consist.joinToString(","),
            )
            for ((n, label) in l.functions.toSortedMap()) line("fn", l.id, n, label)
            val d = l.dynamics
            if (d != LocoDynamics()) {
                line(
                    "fahr", l.id, if (d.cab) 1 else 0, if (d.inertia) 1 else 0, d.accelerationSeconds, d.brakingSeconds,
                    d.periodMs, d.feedbackOffsetMm, d.reactionDelayMs,
                )
            }
        }
        for (w in fleet.wagons) {
            line(
                "wagen", w.id, w.group.name, w.code, w.name, w.category?.name, w.railway, w.epoch?.name, w.lengthMm,
                w.count, w.manufacturer, w.articleNumber, w.notes,
            )
        }
        for (c in fleet.categories) line("zg", c.id, c.code, c.name, if (c.passenger) "p" else "g", c.maxSpeed)
        for (t in fleet.trains) line("zug", t.id, t.name, t.categoryId, t.locos.joinToString(","), t.wagons.joinToString(","))
    }

    private fun StringBuilder.line(vararg fields: Any?) {
        appendLine(fields.joinToString("\t") { escape(it?.toString().orEmpty()) })
    }

    fun decode(text: String): Fleet {
        val lines = text.lines()
        require(lines.firstOrNull()?.trim() == HEADER) { "Keine Fahrzeug-Datei" }
        val locos = mutableListOf<LocoRecord>()
        val wagons = mutableListOf<WagonClass>()
        val categories = mutableListOf<TrainCategory>()
        val trains = mutableListOf<TrainSet>()
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
                        gauge = Gauge.entries.firstOrNull { it.name == at(14) },
                        consist = list(at(15)),
                    )
                    "fn" -> {
                        val i = locos.indexOfFirst { it.id == at(1) }
                        if (i >= 0) locos[i] = locos[i].copy(functions = locos[i].functions + (at(2).toInt() to at(3)))
                    }
                    "fahr" -> {
                        val i = locos.indexOfFirst { it.id == at(1) }
                        val defaults = LocoDynamics()
                        if (i >= 0) {
                            locos[i] = locos[i].copy(
                                dynamics = LocoDynamics(
                                    cab = at(2) == "1",
                                    inertia = at(3) == "1",
                                    accelerationSeconds = at(4).toDoubleOrNull() ?: defaults.accelerationSeconds,
                                    brakingSeconds = at(5).toDoubleOrNull() ?: defaults.brakingSeconds,
                                    periodMs = at(6).toIntOrNull() ?: defaults.periodMs,
                                    feedbackOffsetMm = at(7).toIntOrNull() ?: 0,
                                    reactionDelayMs = at(8).toIntOrNull() ?: 0,
                                ).validated(),
                            )
                        }
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
                    "zg" -> categories += TrainCategory(at(1).ifEmpty { error("ohne Id") }, at(2), at(3), at(4) != "g", at(5).toIntOrNull())
                    "zug" -> trains += TrainSet(at(1).ifEmpty { error("ohne Id") }, at(2), at(3), list(at(4)), list(at(5)))
                }
            }
        }
        return Fleet(locos, wagons, categories, trains)
    }

    private fun list(s: String) = s.split(',').filter { it.isNotBlank() }

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
