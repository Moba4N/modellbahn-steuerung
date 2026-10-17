package de.moba.modules.symbols.api

/**
 * Textformat der Symbolbibliothek, Felder durch Tabulator getrennt:
 *
 * ```
 * MOBA-SYMBOLE 1
 * symbol  <id> <name> <kategorie> <groesse>
 * state   <name> <pixel>
 * ```
 * Die Pixel stehen zeilenweise als laufende Folge `anzahl*AARRGGBB`, durch Komma getrennt,
 * damit grosse leere Flaechen wenig Platz brauchen. `state`-Zeilen gehoeren zum Symbol darueber.
 */
object SymbolCodec {
    private const val HEADER = "MOBA-SYMBOLE 1"

    fun encode(symbols: List<SymbolDef>): String = buildString {
        appendLine(HEADER)
        for (s in symbols) {
            line("symbol", s.id, s.name, s.category.name, s.size)
            for (state in s.states) line("state", state.name, encodePixels(state.image))
        }
    }

    fun decode(text: String): List<SymbolDef> {
        val lines = text.lines()
        require(lines.firstOrNull()?.trim() == HEADER) { "Keine Symbol-Datei" }
        val result = mutableListOf<SymbolDef>()
        var head: List<String>? = null
        val states = mutableListOf<SymbolState>()
        fun flush() {
            val h = head ?: return
            runCatching {
                result += SymbolDef(
                    id = h[1],
                    name = h[2],
                    category = SymbolCategory.entries.firstOrNull { it.name == h[3] } ?: SymbolCategory.OTHER,
                    states = states.toList(),
                )
            }
            head = null
            states.clear()
        }
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val f = line.split('\t').map(::unescape)
            when (f[0]) {
                "symbol" -> {
                    flush()
                    head = f.takeIf { it.size >= 5 }
                }
                "state" -> {
                    val size = head?.getOrNull(4)?.toIntOrNull() ?: continue
                    runCatching { states += SymbolState(f[1], decodePixels(size, f[2])) }
                }
            }
        }
        flush()
        return result
    }

    fun encodePixels(image: PixelImage): String {
        val px = image.pixels
        val parts = mutableListOf<String>()
        var i = 0
        while (i < px.size) {
            var n = 1
            while (i + n < px.size && px[i + n] == px[i]) n++
            parts += "$n*" + px[i].toUInt().toString(16).padStart(8, '0')
            i += n
        }
        return parts.joinToString(",")
    }

    fun decodePixels(size: Int, text: String): PixelImage {
        val out = IntArray(size * size)
        var i = 0
        for (part in text.split(',')) {
            val n = part.substringBefore('*').toInt()
            val color = part.substringAfter('*').toUInt(16).toInt()
            repeat(n) { out[i++] = color }
        }
        require(i == out.size) { "Pixelanzahl passt nicht" }
        return PixelImage(size, out)
    }

    private fun StringBuilder.line(vararg fields: Any?) {
        appendLine(fields.joinToString("\t") { escape(it?.toString().orEmpty()) })
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
