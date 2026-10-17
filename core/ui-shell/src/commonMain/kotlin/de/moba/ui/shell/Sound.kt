package de.moba.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Klaenge der Plattform fuer Sound-Zubehoer: Datei waehlen (Windows: Dateidialog, Android: Dateiauswahl),
 * als Kopie im Datenordner der App ablegen und abspielen. Ein Klang heisst nach seiner Datei, z. B. "pfiff.wav".
 */
interface SoundSupport {
    /** Hinweis fuer die Auswahl, z. B. welche Dateiarten gehen. */
    val formats: String

    /** Waehlt eine Klangdatei, kopiert sie in die App und liefert ihren Namen, `null` bei Abbruch oder Fehler. */
    fun pick(onResult: (String?) -> Unit)

    /** Spielt einen abgelegten Klang ab; fehlt die Datei, passiert nichts. */
    fun play(name: String)
}

/** Klaenge fuer Module; `null`, wenn die Plattform keine anbietet. */
val LocalSoundSupport = compositionLocalOf<SoundSupport?> { null }

/** Erlaubte Zeichen fuer abgelegte Klangdateien; alles andere wird zu "_". */
fun soundFileName(original: String): String {
    val name = original.substringAfterLast('/').substringAfterLast('\\').ifBlank { "klang" }
    return name.map { if (it.isLetterOrDigit() || it in "._- ") it else '_' }.joinToString("").take(80)
}

/**
 * Laeuft unsichtbar, solange das Modul aktiv ist, egal welche Ansicht offen ist,
 * z. B. um Klaenge abzuspielen, die ein Rueckmelder ausgeloest hat.
 */
interface ModuleEffect {
    val moduleId: String

    @Composable
    fun Run(app: MobaApp)
}
