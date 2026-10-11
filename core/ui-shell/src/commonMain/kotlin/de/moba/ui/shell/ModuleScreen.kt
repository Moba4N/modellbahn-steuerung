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
