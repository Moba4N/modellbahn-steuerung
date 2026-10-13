package de.moba.modules.decoder.api

/**
 * Textformat der Decoder-Datenbank, Felder durch Tabulator getrennt:
 *
 * ```
 * MOBA-DECODER 1
 * profil <id> <name> <lok> <lokadresse> <decodertyp> <notiz>
 * cv     <profil-id> <cv> <wert>
 * ```
 */
object DecoderCodec {
    private const val HEADER = "MOBA-DECODER 1"

    fun encode(profiles: List<DecoderProfile>): String = buildString {
        appendLine(HEADER)
        for (p in profiles) {
            appendLine(
                listOf("profil", p.id, p.name, p.locoName, p.locoAddress?.toString().orEmpty(), p.decoderType, p.notes)
                    .joinToString("\t") { escape(it) },
            )
            for ((cv, value) in p.cvs.toSortedMap()) appendLine("cv\t${escape(p.id)}\t$cv\t$value")
        }
    }

    fun decode(text: String): List<DecoderProfile> {
        val lines = text.lines()
        require(lines.firstOrNull()?.trim() == HEADER) { "Keine Decoder-Datei" }
        val profiles = mutableListOf<DecoderProfile>()
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val f = line.split('\t').map(::unescape)
            fun at(i: Int) = f.getOrElse(i) { "" }
            when (f[0]) {
                "profil" -> profiles += DecoderProfile(
                    id = at(1), name = at(2), locoName = at(3), locoAddress = at(4).toIntOrNull(),
                    decoderType = at(5), notes = at(6),
                )
                "cv" -> {
                    val i = profiles.indexOfFirst { it.id == at(1) }
                    val cv = at(2).toIntOrNull()
                    val value = at(3).toIntOrNull()
                    if (i >= 0 && cv != null && cv in 1..1024 && value != null && value in 0..255) {
                        profiles[i] = profiles[i].copy(cvs = profiles[i].cvs + (cv to value))
                    }
                }
            }
        }
        return profiles
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
