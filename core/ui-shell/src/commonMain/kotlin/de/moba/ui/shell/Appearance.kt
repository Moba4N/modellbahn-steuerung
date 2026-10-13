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

/** Schriftarten, die auf Windows und Android ohne Zusatzdateien vorhanden sind. */
enum class FontChoice(val label: String, val family: FontFamily) {
    STANDARD("Standard", FontFamily.Default),
    SANS("Serifenlos", FontFamily.SansSerif),
    SERIF("Serifen", FontFamily.Serif),
    MONO("Schreibmaschine", FontFamily.Monospace),
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
