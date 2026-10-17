package de.moba.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import de.moba.core.runtime.ModuleState
import de.moba.core.runtime.ModuleStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

/**
 * Oeffnet unter Windows die eigenen Fenster der Module (z. B. Gleisbilder auf weiteren Bildschirmen).
 * Gehoert neben das Hauptfenster in `application { }`. Jedes Fenster oeffnet wieder dort, wo es zuletzt war.
 */
@Composable
fun ModuleWindowsHost(app: MobaApp, providers: List<ModuleWindows>, icon: Painter? = null) {
    val session by app.session.collectAsState()
    val empty = remember { MutableStateFlow(emptyMap<String, ModuleStatus>()) }
    val modules by (session?.host?.status ?: empty).collectAsState()
    // Lizenzwechsel neu auswerten, allowed() fragt die Lizenz ab
    app.activeLicense.collectAsState().value
    providers.filter { modules[it.moduleId]?.state == ModuleState.ACTIVE && it.allowed(app) }.forEach { provider ->
        val keys by provider.open(app).collectAsState()
        keys.forEach { k -> key(provider.moduleId, k) { ModuleWindow(app, provider, k, icon) } }
    }
}

@Composable
private fun ModuleWindow(app: MobaApp, provider: ModuleWindows, key: String, icon: Painter?) {
    val prefKey = WindowBounds.prefKey(provider.moduleId, key)
    val saved = remember { WindowBounds.parse(app.preference(prefKey)) }
    val state = rememberWindowState(
        placement = if (saved?.fullscreen == true) WindowPlacement.Fullscreen else WindowPlacement.Floating,
        position = saved?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
        size = saved?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(1100.dp, 760.dp),
    )
    LaunchedEffect(state) {
        // Lage merken, damit das Fenster beim naechsten Start wieder auf demselben Bildschirm aufgeht
        var floating = saved?.let { DpSize(it.width.dp, it.height.dp) } ?: state.size
        snapshotFlow { Triple(state.position, state.size, state.placement) }.drop(1).collectLatest { (position, size, placement) ->
            delay(400)
            val fullscreen = placement == WindowPlacement.Fullscreen
            if (placement == WindowPlacement.Floating) floating = size
            val p = position as? WindowPosition.Absolute ?: return@collectLatest
            app.setPreference(
                prefKey,
                WindowBounds(p.x.value.toInt(), p.y.value.toInt(), floating.width.value.toInt(), floating.height.value.toInt(), fullscreen).encode(),
            )
        }
    }
    val fullscreen = state.placement == WindowPlacement.Fullscreen
    fun toggle() {
        state.placement = if (fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
    }
    val title = provider.title(app, key)
    Window(
        onCloseRequest = { provider.close(app, key) },
        title = "$title – Modellbahn-Steuerung",
        icon = icon,
        state = state,
        onPreviewKeyEvent = {
            when {
                it.type != KeyEventType.KeyDown -> false
                it.key == Key.F11 -> true.also { toggle() }
                it.key == Key.Escape && fullscreen -> true.also { toggle() }
                else -> false
            }
        },
    ) {
        ModuleWindowFrame(app, title, fullscreen, onToggleFullscreen = ::toggle, onClose = { provider.close(app, key) }) {
            provider.Content(app, key)
        }
    }
}
