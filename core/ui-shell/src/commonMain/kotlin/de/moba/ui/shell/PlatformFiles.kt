package de.moba.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Was die Plattform fuer das Datei-Menue kann: Dateien speichern und waehlen (Windows: Dateidialog,
 * Android: Dateiauswahl des Systems), auf Windows Ordner waehlen und oeffnen, und drucken.
 */
interface PlatformFiles {
    /** Speichert [bytes] an einem Ort, den der Nutzer waehlt; [onDone] bekommt den Ort oder `null` bei Abbruch. */
    fun save(name: String, bytes: ByteArray, onDone: (String?) -> Unit)

    /** Waehlt eine Datei (z. B. ein ZIP fuer den Import); `null` bei Abbruch. */
    fun pick(onResult: (ByteArray?) -> Unit)

    /** Kann einen beliebigen Ordner als Anlage waehlen (Windows). */
    val canChooseFolder: Boolean get() = false
    fun chooseFolder(onResult: (String?) -> Unit) = onResult(null)

    /** Kann den Anlagen-Ordner im Datei-Explorer zeigen (Windows). */
    val canOpenFolder: Boolean get() = false
    fun openFolder(path: String) = Unit

    /** Druckt ein Bild auf einer Seite, quer, wenn es breiter als hoch ist. @return Fehlermeldung oder `null` */
    fun print(title: String, image: ImageBitmap): String?
}

@Composable
expect fun rememberPlatformFiles(): PlatformFiles
