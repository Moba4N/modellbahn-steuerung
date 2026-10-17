package de.moba.ui.shell

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily

/** Grundlegendes Aussehen der App: Farben und Ecken. Jedes Design gibt es hell und dunkel. */
enum class DesignStyle(val label: String, val description: String) {
    MODERN("Modern", "Kräftiges Blau, runde Ecken"),
    WINDOWS11("Windows 11", "Fluent-Farben, flache Flächen, kleine Ecken"),
    STELLWERK("Stellwerk", "Klassisches Stellpult in Grün und Signalgelb"),
}

/**
 * Schriftarten fuer die ganze App: die vier Systemschriften und mitgelieferte Schriften
 * (SIL Open Font License), die auf Windows und Android gleich aussehen.
 */
enum class FontChoice(val label: String, private val files: Pair<String, String>? = null, private val system: FontFamily = FontFamily.Default) {
    STANDARD("Standard"),
    SANS("Serifenlos", system = FontFamily.SansSerif),
    SERIF("Serifen", system = FontFamily.Serif),
    MONO("Schreibmaschine", system = FontFamily.Monospace),
    OUTFIT("Outfit", "outfit_regular" to "outfit_bold"),
    WORK_SANS("Work Sans", "worksans_regular" to "worksans_bold"),
    INSTRUMENT("Instrument Sans", "instrumentsans_regular" to "instrumentsans_bold"),
    BIG_SHOULDERS("Big Shoulders", "bigshoulders_regular" to "bigshoulders_bold"),
    TEKTUR("Tektur", "tektur_regular" to "tektur_medium"),
    LORA("Lora", "lora_regular" to "lora_bold"),
    PLEX_SERIF("IBM Plex Serif", "ibmplexserif_regular" to "ibmplexserif_bold"),
    JETBRAINS_MONO("JetBrains Mono", "jetbrainsmono_regular" to "jetbrainsmono_bold"),
    ;

    /** Die Schrift; mitgelieferte werden beim ersten Gebrauch geladen. */
    val family: FontFamily by lazy { files?.let { (regular, bold) -> bundledFontFamily(regular, bold) } ?: system }

    /** Mitgeliefert (nicht vom Betriebssystem). */
    val bundled: Boolean get() = files != null
}

/** Laedt eine mitgelieferte Schrift aus `fonts/font` (Windows: Klassenpfad, Android: res/font). */
internal expect fun bundledFontFamily(regular: String, bold: String): FontFamily

/**
 * Akzentfarbe fuer Knoepfe, Auswahl und Regler. [STANDARD] nimmt die Farbe des Designs.
 * Je Farbe ein Ton fuer dunkel und einer fuer hell, damit Text darauf lesbar bleibt.
 */
enum class AccentColor(val label: String, val dark: Long?, val light: Long?) {
    STANDARD("Design", null, null),
    BLUE("Blau", 0xFF4CC2FF, 0xFF00658F),
    TEAL("Türkis", 0xFF4FD8C4, 0xFF00695C),
    GREEN("Grün", 0xFF6FD67A, 0xFF2E7D32),
    LIME("Gelbgrün", 0xFFC5E15A, 0xFF5A6E00),
    YELLOW("Signalgelb", 0xFFF2C94C, 0xFF7A5C00),
    ORANGE("Orange", 0xFFFFA65C, 0xFFB35000),
    RED("Rot", 0xFFFF7A7A, 0xFFB3261E),
    PINK("Pink", 0xFFFF8AC8, 0xFFA8326E),
    PURPLE("Lila", 0xFFC4A1FF, 0xFF6A3FB5),
    GRAY("Grau", 0xFFB8C2CC, 0xFF4A5560),
}

enum class TextSize(val label: String, val scale: Float) {
    SMALL("Klein", 0.9f),
    NORMAL("Normal", 1f),
    LARGE("Groß", 1.15f),
}

/** Wie deutlich das eigene Hintergrundbild hinter den Ansichten zu sehen ist. */
enum class WatermarkStrength(val label: String, val alpha: Float) {
    LIGHT("Schwach", 0.07f),
    MEDIUM("Mittel", 0.14f),
    STRONG("Deutlich", 0.24f),
}

/** Alle Einstellungen zum Erscheinungsbild ausser hell/dunkel. */
data class Appearance(
    val style: DesignStyle = DesignStyle.MODERN,
    val font: FontChoice = FontChoice.STANDARD,
    val accent: AccentColor = AccentColor.STANDARD,
    val textSize: TextSize = TextSize.NORMAL,
    val watermark: WatermarkStrength = WatermarkStrength.MEDIUM,
)

/** Speichert das Hintergrundbild als Datei (Windows: Benutzerordner, Android: App-Speicher). */
interface ImageStore {
    fun load(): ByteArray?

    /** `null` loescht das Bild. */
    fun save(bytes: ByteArray?)

    class InMemory : ImageStore {
        private var bytes: ByteArray? = null
        override fun load() = bytes
        override fun save(bytes: ByteArray?) {
            this.bytes = bytes
        }
    }
}

/**
 * Oeffnet die Bildauswahl der Plattform (Windows: Dateidialog, Android: Galerie)
 * und liefert die Bilddatei oder `null`, wenn abgebrochen wurde.
 */
fun interface ImagePicker {
    fun pick(onResult: (ByteArray?) -> Unit)
}

/** Bildauswahl fuer Module (z. B. Fahrzeugfotos); `null`, wenn die Plattform keine anbietet. */
val LocalImagePicker = compositionLocalOf<ImagePicker?> { null }

/** Wandelt PNG, JPEG usw. in ein Bild fuer Compose; `null`, wenn die Datei kein lesbares Bild ist. */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?
