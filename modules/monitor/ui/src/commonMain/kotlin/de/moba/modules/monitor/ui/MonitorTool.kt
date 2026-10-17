package de.moba.modules.monitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.moba.modules.monitor.api.MONITOR_MODULE_ID
import de.moba.modules.monitor.api.MonitorEntry
import de.moba.modules.monitor.api.MonitorKind
import de.moba.modules.monitor.api.MonitorService
import de.moba.modules.monitor.api.formatMonitorTime
import de.moba.ui.shell.LocalWideLayout
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.Moba
import de.moba.ui.shell.ModuleTool
import kotlinx.coroutines.flow.MutableStateFlow

/** Einstellungen des Monitors; bleiben erhalten, solange die App laeuft. */
object MonitorUiState {
    /** Meldungen sind standardmaessig aus: sie wiederholen, was schon unter "Empfangen" steht. */
    val kinds = MutableStateFlow(setOf(MonitorKind.SENT, MonitorKind.RECEIVED))
    val showHex = MutableStateFlow(true)
    val follow = MutableStateFlow(true)
}

const val MONITOR_LIST_TAG = "monitor-liste"

/** Werkzeug "Monitor" im Burger-Menue: zeigt live, welche Daten zur Zentrale gehen und zurueckkommen. */
object MonitorTool : ModuleTool {
    override val moduleId = MONITOR_MODULE_ID
    override val title = "Monitor"
    override val icon = Icons.Outlined.Terminal

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Content(app: MobaApp, onClose: () -> Unit) {
        val session by app.session.collectAsState()
        val service = session?.host?.service(MonitorService::class) ?: return
        val entries by service.entries.collectAsState()
        val paused by service.paused.collectAsState()
        val kinds by MonitorUiState.kinds.collectAsState()
        val showHex by MonitorUiState.showHex.collectAsState()
        val follow by MonitorUiState.follow.collectAsState()
        val clipboard = LocalClipboardManager.current
        val wide = LocalWideLayout.current
        val shown = entries.filter { it.kind in kinds }
        val list = rememberLazyListState()
        LaunchedEffect(shown.lastOrNull()?.seq, follow) {
            if (follow && shown.isNotEmpty()) list.scrollToItem(shown.lastIndex)
        }

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Terminal, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Monitor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${session?.station?.name ?: ""} · ${shown.size} von ${entries.size} Einträgen" + if (paused) " · angehalten" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Schließen") }
            }
            FlowRow(
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MonitorKind.entries.forEach { k ->
                    FilterChip(
                        selected = k in kinds,
                        onClick = { MonitorUiState.kinds.value = if (k in kinds) kinds - k else kinds + k },
                        label = { Text(k.label) },
                        leadingIcon = { KindMark(k) },
                    )
                }
                FilterChip(selected = showHex, onClick = { MonitorUiState.showHex.value = !showHex }, label = { Text("Hex") })
                FilterChip(selected = follow, onClick = { MonitorUiState.follow.value = !follow }, label = { Text("Mitlaufen") })
                OutlinedButton(onClick = { service.setPaused(!paused) }) {
                    Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (paused) "Weiter" else "Anhalten")
                }
                TextButton(onClick = { service.clear() }) {
                    Icon(Icons.Outlined.DeleteSweep, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Leeren")
                }
                TextButton(onClick = { clipboard.setText(AnnotatedString(service.export(kinds))) }) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kopieren")
                }
            }
            if (!service.rawAvailable) {
                Text(
                    "Diese Zentrale gibt ihre Rohdaten nicht heraus; der Monitor zeigt nur die Meldungen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (entries.isEmpty()) "Noch nichts gesendet. Fahre eine Lok oder schalte eine Weiche." else "Kein Eintrag passt zum Filter.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().testTag(MONITOR_LIST_TAG), state = list) {
                    items(shown, key = { it.seq }) { e -> EntryRow(e, showHex, wide) }
                }
            }
        }
    }
}

@Composable
private fun kindColor(kind: MonitorKind): Color = when (kind) {
    MonitorKind.SENT -> MaterialTheme.colorScheme.primary
    MonitorKind.RECEIVED -> Moba.colors.signalGreen
    MonitorKind.EVENT -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun KindMark(kind: MonitorKind) {
    Box(
        Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(kindColor(kind).copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when (kind) {
                MonitorKind.SENT -> "→"
                MonitorKind.RECEIVED -> "←"
                MonitorKind.EVENT -> "•"
            },
            color = kindColor(kind),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun EntryRow(e: MonitorEntry, showHex: Boolean, wide: Boolean) {
    val mono = FontFamily.Monospace
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            formatMonitorTime(e.millis),
            fontFamily = mono,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(if (wide) 84.dp else 72.dp).padding(top = 2.dp),
        )
        KindMark(e.kind)
        Column(Modifier.weight(1f)) {
            Text(e.text ?: "(unbekanntes Paket)", style = MaterialTheme.typography.bodyMedium)
            if (showHex && e.hex.isNotEmpty() && e.hex != e.text) {
                Text(e.hex, fontFamily = mono, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
