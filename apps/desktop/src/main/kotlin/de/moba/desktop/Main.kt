package de.moba.desktop

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.MobaShell

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Modellbahn-Steuerung") {
        val scope = rememberCoroutineScope()
        val app = remember { MobaApp(scope).also { it.start() } }
        MobaShell(app)
    }
}
