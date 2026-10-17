package de.moba.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow

/**
 * Ansichten, die ein Modul in eigenen Fenstern zeigt, etwa Gleisbilder auf weiteren Bildschirmen.
 * Unter Windows oeffnet die App fuer jeden Schluessel in [open] ein Fenster; Lage und Groesse merkt sie sich.
 */
interface ModuleWindows {
    val moduleId: String

    /** Schluessel der offenen Fenster, z. B. Gleisbild-Ids. */
    fun open(app: MobaApp): StateFlow<List<String>>

    /** Duerfen die Fenster gerade gezeigt werden, z. B. nur mit Pro? */
    fun allowed(app: MobaApp): Boolean

    fun title(app: MobaApp, key: String): String

    /** Fenster schliessen; die Ansicht kehrt ins Hauptfenster zurueck. */
    fun close(app: MobaApp, key: String)

    @Composable
    fun Content(app: MobaApp, key: String)
}

/** Lage eines eigenen Fensters in Bildschirmpunkten, als Einstellung "x,y,breite,hoehe,vollbild". */
data class WindowBounds(val x: Int, val y: Int, val width: Int, val height: Int, val fullscreen: Boolean) {
    fun encode() = "$x,$y,$width,$height,${if (fullscreen) 1 else 0}"

    companion object {
        fun prefKey(moduleId: String, key: String) = "fenster.$moduleId.$key"

        fun parse(text: String?): WindowBounds? {
            val p = text?.split(',')?.map { it.trim().toIntOrNull() } ?: return null
            if (p.size != 5 || p.any { it == null }) return null
            val (x, y, w, h, f) = p.map { it!! }
            if (w < 200 || h < 150) return null
            return WindowBounds(x, y, w, h, f == 1)
        }
    }
}

/**
 * Rahmen eines eigenen Fensters: Titel, Verbindung, Vollbild, Zurueckholen und Nothalt.
 * Der Nothalt ist auch hier immer erreichbar, wer am zweiten Bildschirm steht, braucht ihn genauso.
 */
@Composable
fun ModuleWindowFrame(
    app: MobaApp,
    title: String,
    fullscreen: Boolean,
    onToggleFullscreen: (() -> Unit)?,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val dark by app.darkTheme.collectAsState()
    val appearance by app.appearance.collectAsState()
    val connected by app.connected.collectAsState()
    val session by app.session.collectAsState()
    val colors = Moba.colors
    MobaTheme(dark, appearance) {
        CompositionLocalProvider(LocalWideLayout provides true) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize()) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(
                            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Filled.Train, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(if (connected) colors.signalGreen else colors.alarm)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (connected) session?.station?.name ?: "" else "Nicht verbunden",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (onToggleFullscreen != null) {
                                OutlinedButton(onClick = onToggleFullscreen) {
                                    Icon(if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (fullscreen) "Vollbild aus" else "Vollbild")
                                }
                            }
                            TextButton(onClick = onClose) {
                                Icon(Icons.Filled.Close, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ins Hauptfenster")
                            }
                            Button(
                                onClick = { app.emergencyStop() },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.alarm, contentColor = MaterialTheme.colorScheme.onError),
                                modifier = Modifier.height(40.dp),
                            ) {
                                Icon(Icons.Filled.Block, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("NOTHALT", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) { content() }
                }
            }
        }
    }
}
