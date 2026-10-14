package de.moba.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/** Eine Ansicht, die ein Fachmodul in die App einhaengt. Sichtbar, solange das Modul aktiv ist. */
interface ModuleScreen {
    val moduleId: String
    val title: String
    val icon: ImageVector

    @Composable
    fun Content(app: MobaApp)

    /** Aenderungen dieses Reiters, die beim Beenden verloren gingen; `null` = alles gespeichert. */
    fun unsavedWork(app: MobaApp): UnsavedWork? = null
}

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
