package de.moba.ui.shell

import androidx.compose.runtime.Composable

/** Eine Ansicht, die ein Fachmodul in die App einhaengt. Sichtbar, solange das Modul aktiv ist. */
interface ModuleScreen {
    val moduleId: String
    val title: String

    @Composable
    fun Content(app: MobaApp)
}
