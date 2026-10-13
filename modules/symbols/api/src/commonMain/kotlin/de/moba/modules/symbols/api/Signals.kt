package de.moba.modules.symbols.api

/** Lichtfarben von Signallampen mit ihrer Darstellungsfarbe (ARGB). */
enum class LampColor(val label: String, val argb: Int) {
    RED("Rot", 0xFFFF3B30.toInt()),
    GREEN("Grün", 0xFF2EE66B.toInt()),
    YELLOW("Gelb", 0xFFFFC21A.toInt()),
    WHITE("Weiß", 0xFFF4F4F0.toInt()),
    BLUE("Blau", 0xFF3C8DFF.toInt()),
    VIOLET("Violett", 0xFFB45CFF.toInt()),
}

enum class SignalKind(val label: String) {
    MAIN("Hauptsignal"),
    DISTANT("Vorsignal"),
    COMBINED("Mehrabschnittssignal"),
    SHUNTING("Rangiersignal"),
}

/** Lampenplatz auf dem Signalschirm, Spalte und Zeile im Lampenraster. */
data class Lamp(val col: Int, val row: Int)

/** Eine leuchtende Lampe in einem Signalbild. */
data class LitLamp(val col: Int, val row: Int, val color: LampColor, val flash: Boolean = false) {
    val lamp: Lamp get() = Lamp(col, row)
}

/** Zusatzanzeiger, z. B. Geschwindigkeitskennziffer "6" (Zs 3). */
data class Indicator(val text: String, val color: LampColor = LampColor.WHITE)

/** Ein Signalbild, z. B. "Hp 1 – Fahrt". */
data class SignalAspect(
    val id: String,
    val name: String,
    val meaning: String,
    val lit: List<LitLamp>,
    val indicator: Indicator? = null,
)

/** Ein Signalsystem eines Landes mit Lampenanordnung und allen Signalbildern. */
data class SignalSystem(
    val id: String,
    val country: String,
    val name: String,
    val kind: SignalKind,
    val columns: Int,
    val rows: Int,
    val lamps: List<Lamp>,
    val aspects: List<SignalAspect>,
    val source: String = "",
) {
    init {
        val positions = lamps.toSet()
        aspects.forEach { aspect ->
            require(aspect.lit.all { it.lamp in positions }) { "$id/${aspect.id}: Lampe ausserhalb des Signalschirms" }
        }
    }

    /** Das erste Signalbild ist immer der Haltbegriff. */
    val stop: SignalAspect get() = aspects.first()

    /** Fahrbegriff: das erste Bild mit festem Gruen und ohne Rot, sonst das erste ohne Rot, sonst das zweite. */
    val clear: SignalAspect
        get() = aspects.drop(1).firstOrNull { a -> a.lit.none { it.color == LampColor.RED } && a.lit.any { it.color == LampColor.GREEN && !it.flash } }
            ?: aspects.drop(1).firstOrNull { a -> a.lit.isNotEmpty() && a.lit.none { it.color == LampColor.RED } }
            ?: aspects.getOrElse(1) { aspects.first() }

    /** Halt und Fahrt zuerst, dann alle uebrigen Bilder in Katalogreihenfolge. */
    val ordered: List<SignalAspect> get() = (listOf(stop, clear) + aspects).distinct()

    /** Lampenanordnung nicht anhand von Quellen geprueft (siehe README). */
    val unchecked: Boolean get() = country in SignalData.uncheckedCountries
}

/** Deutsche Laendernamen fuer die Auswahl. */
val COUNTRY_NAMES = mapOf(
    "AT" to "Österreich", "BE" to "Belgien", "BG" to "Bulgarien", "HR" to "Kroatien", "CY" to "Zypern",
    "CZ" to "Tschechien", "DK" to "Dänemark", "EE" to "Estland", "FI" to "Finnland", "FR" to "Frankreich",
    "DE" to "Deutschland", "GR" to "Griechenland", "HU" to "Ungarn", "IE" to "Irland", "IT" to "Italien",
    "LV" to "Lettland", "LT" to "Litauen", "LU" to "Luxemburg", "MT" to "Malta", "NL" to "Niederlande",
    "PL" to "Polen", "PT" to "Portugal", "RO" to "Rumänien", "SK" to "Slowakei", "SI" to "Slowenien",
    "ES" to "Spanien", "SE" to "Schweden", "CH" to "Schweiz",
)

/**
 * Macht aus einem Signalsystem ein Pixelsymbol: ein Zustand je Signalbild, Halt zuerst, dann Fahrt.
 * So lassen sich die fertigen Signalbilder im Symbol-Editor weiter anpassen; im Gleisbild zeigt
 * der erste Zustand die Signalstellung Halt, der zweite Fahrt.
 */
object SignalSymbols {
    private const val SCREEN = 0xFF1B1E21.toInt()
    private const val SCREEN_EDGE = 0xFF5A6066.toInt()
    private const val DARK_LAMP = 0xFF3A3F44.toInt()
    private const val MAST = 0xFF8A9096.toInt()

    fun toSymbol(system: SignalSystem, id: String, size: Int = PixelImage.DEFAULT_SIZE): SymbolDef =
        SymbolDef(
            id = id,
            name = system.name,
            category = SymbolCategory.SIGNAL,
            states = system.ordered.map { SymbolState("${it.name} – ${it.meaning}", render(system, it, size)) },
        )

    /**
     * Zeichnet ein Signalbild: Schirm mit allen Lampen, leuchtende in ihrer Farbe, darunter der Mast.
     * Mit [flashOn] = false sind blinkende Lampen gerade dunkel.
     */
    fun render(system: SignalSystem, aspect: SignalAspect?, size: Int, flashOn: Boolean = true): PixelImage {
        val margin = size / 16.0
        val mastHeight = size / 6.0
        val availW = size - 2 * margin
        val availH = size - 2 * margin - mastHeight
        val cell = minOf(availW / system.columns, availH / system.rows)
        val screenW = cell * system.columns
        val screenH = cell * system.rows
        val left = (size - screenW) / 2
        val top = margin
        var img = PixelImage(size)
        // Mast
        val mastX = (size / 2.0).toInt()
        img = img.rect(mastX - size / 32, (top + screenH).toInt(), mastX + size / 32 - 1, size - 1, MAST, filled = true)
        // Schirm
        img = img.rect(left.toInt(), top.toInt(), (left + screenW).toInt() - 1, (top + screenH).toInt() - 1, SCREEN, filled = true)
        img = img.rect(left.toInt(), top.toInt(), (left + screenW).toInt() - 1, (top + screenH).toInt() - 1, SCREEN_EDGE, filled = false)
        val lit = aspect?.lit.orEmpty().filter { flashOn || !it.flash }.associateBy { it.lamp }
        for (lamp in system.lamps) {
            val cx = left + (lamp.col + 0.5) * cell
            val cy = top + (lamp.row + 0.5) * cell
            val color = lit[lamp]?.color?.argb ?: DARK_LAMP
            img = PixelImage.disc(img, cx, cy, maxOf(cell * 0.36, 0.8), color)
        }
        return img
    }
}
