package de.moba.ui.shell

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.MutableStateFlow

/** Eine Ansicht, die ein Fachmodul in die App einhaengt. Sichtbar, solange das Modul aktiv ist. */
interface ModuleScreen {
    val moduleId: String
    val title: String
    val icon: ImageVector

    @Composable
    fun Content(app: MobaApp)

    /** Aenderungen dieses Reiters, die beim Beenden verloren gingen; `null` = alles gespeichert. */
    fun unsavedWork(app: MobaApp): UnsavedWork? = null

    /** Was sich aus diesem Reiter drucken laesst (Datei-Menue, „Drucken …“), z. B. jedes Gleisbild. */
    fun printables(app: MobaApp): List<PrintItem> = emptyList()
}

/**
 * Eine druckbare Seite: [content] wird hell und in voller Groesse gezeichnet, als Bild aufgenommen
 * und auf eine Seite eingepasst gedruckt.
 */
class PrintItem(val title: String, val content: @Composable () -> Unit)

/** Ungespeicherte Aenderungen, nach denen die App vor dem Beenden fragt. */
class UnsavedWork(
    /** Was ungespeichert ist, z. B. "Gleisbilder". */
    val title: String,
    /** Ob sich die Aenderungen mit der aktuellen Lizenz speichern lassen. */
    val canSave: Boolean,
    /** Speichert; wirft bei einem Fehler. */
    val save: () -> Unit,
)

/**
 * Eine Leiste am unteren Rand, z. B. mehrere Fahrregler. Sie bleibt sichtbar, egal welche Ansicht offen ist.
 */
interface ModuleDock {
    val moduleId: String

    @Composable
    fun Content(app: MobaApp)
}

/**
 * Eine feste Leiste am rechten Rand (nur auf breiten Bildschirmen), z. B. das Fahrpult.
 * Sie bleibt sichtbar, egal welche Ansicht gerade offen ist.
 */
interface ModulePanel {
    val moduleId: String

    @Composable
    fun Content(app: MobaApp)
}

/**
 * Ein Werkzeug, das nur ueber das Burger-Menue geoeffnet wird (z. B. der Monitor).
 * Es erscheint als grosses Fenster ueber der App, nicht als Reiter.
 */
interface ModuleTool {
    val moduleId: String
    val title: String
    val icon: ImageVector

    @Composable
    fun Content(app: MobaApp, onClose: () -> Unit)
}

/**
 * Ein eigenes Burger-Menue in der oberen Leiste, rechts neben "Datei" (z. B. "Aktionen").
 * Es erscheint, solange das Modul laeuft.
 */
interface ModuleMenu {
    val moduleId: String
    val title: String
    val icon: ImageVector

    /** Ob das Menue aufgeklappt ist; von aussen setzbar (z. B. fuer Vorschaubilder). */
    val expanded: MutableStateFlow<Boolean>

    /** Eintraege des aufgeklappten Menues; [close] klappt es zu. */
    @Composable
    fun ColumnScope.Items(app: MobaApp, close: () -> Unit)

    /** Fenster, die das Menue oeffnet; immer eingehaengt, solange das Modul laeuft. */
    @Composable
    fun Dialogs(app: MobaApp)
}

/**
 * Ein Eintrag im Menue "Bearbeiten": eine Liste von Objekten mit Editor (z. B. Lokomotiven, Bloecke),
 * erreichbar ohne den passenden Reiter oder das Gleisbild zu oeffnen.
 */
interface ModuleEditor {
    val moduleId: String

    /** Eindeutig ueber alle Module, z. B. "lokomotiven". */
    val key: String
    val title: String
    val icon: ImageVector

    /** Platz im Menue; kleiner steht weiter oben. */
    val order: Int

    @Composable
    fun Content(app: MobaApp)
}
