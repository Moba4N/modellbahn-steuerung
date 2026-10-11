package de.moba.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.moba.core.runtime.ModuleState
import de.moba.core.runtime.ModuleStatus
import kotlinx.coroutines.flow.MutableStateFlow

private const val OVERVIEW = "overview"

/** true, wenn die rechte Leiste (z. B. Fahrpult) sichtbar ist. Module zeigen ihre Inhalte sonst selbst an. */
val LocalWideLayout = compositionLocalOf { false }

/** Ab dieser Breite: Seitenleiste links, Fahrpult rechts (Tablet quer, PC). Darunter: Leiste unten (Handy). */
private val WIDE_LAYOUT = 900.dp

/**
 * App-Rahmen im Stil moderner Stellpult-Software: Statusleiste mit Nothalt oben,
 * Navigation links (bzw. unten auf dem Handy), Ansicht in der Mitte, Fahrpult rechts.
 */
@Composable
fun MobaShell(
    app: MobaApp,
    screens: List<ModuleScreen> = emptyList(),
    panels: List<ModulePanel> = emptyList(),
    initialScreen: String? = null,
) {
    val dark by app.darkTheme.collectAsState()
    val session by app.session.collectAsState()
    val emptyStatus = remember { MutableStateFlow(emptyMap<String, ModuleStatus>()) }
    val modules by (session?.host?.status ?: emptyStatus).collectAsState()
    fun active(id: String) = modules[id]?.state == ModuleState.ACTIVE
    val visible = screens.filter { active(it.moduleId) }
    val activePanels = panels.filter { active(it.moduleId) }
    var current by remember { mutableStateOf(initialScreen ?: OVERVIEW) }
    if (current != OVERVIEW && visible.none { it.moduleId == current }) current = OVERVIEW

    MobaTheme(dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= WIDE_LAYOUT
                CompositionLocalProvider(LocalWideLayout provides wide) {
                Column(Modifier.fillMaxSize()) {
                    TopBar(app, compact = !wide)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        if (wide) {
                            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Spacer(Modifier.height(8.dp))
                                NavigationRailItem(
                                    selected = current == OVERVIEW,
                                    onClick = { current = OVERVIEW },
                                    icon = { Icon(Icons.Outlined.Dashboard, null) },
                                    label = { Text("Übersicht") },
                                )
                                visible.forEach { screen ->
                                    NavigationRailItem(
                                        selected = current == screen.moduleId,
                                        onClick = { current = screen.moduleId },
                                        icon = { Icon(screen.icon, null) },
                                        label = { Text(screen.title) },
                                    )
                                }
                            }
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        Box(Modifier.weight(1f).fillMaxHeight().padding(if (wide) 24.dp else 12.dp)) {
                            val screen = visible.firstOrNull { it.moduleId == current }
                            if (screen == null) Overview(app, modules, wide) else screen.Content(app)
                        }
                        if (wide && activePanels.isNotEmpty()) {
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Surface(
                                Modifier.width(380.dp).fillMaxHeight(),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Column(Modifier.padding(20.dp)) { activePanels.forEach { it.Content(app) } }
                            }
                        }
                    }
                    if (!wide) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                            NavigationBarItem(
                                selected = current == OVERVIEW,
                                onClick = { current = OVERVIEW },
                                icon = { Icon(Icons.Outlined.Dashboard, null) },
                                label = { Text("Übersicht") },
                            )
                            visible.forEach { screen ->
                                NavigationBarItem(
                                    selected = current == screen.moduleId,
                                    onClick = { current = screen.moduleId },
                                    icon = { Icon(screen.icon, null) },
                                    label = { Text(screen.title) },
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun TopBar(app: MobaApp, compact: Boolean) {
    val session by app.session.collectAsState()
    val connected by app.connected.collectAsState()
    val trackPower by app.trackPower.collectAsState()
    val dark by app.darkTheme.collectAsState()
    val colors = Moba.colors

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Train, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            if (!compact) {
                Text("Modellbahn-Steuerung", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
            }

            // Verbindung zur Zentrale
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                StatusDot(if (connected) colors.signalGreen else colors.alarm)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (connected) session?.station?.name ?: "" else "Nicht verbunden",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Gleisspannung
            OutlinedButton(
                onClick = { app.setTrackPower(!trackPower) },
                enabled = connected,
                border = BorderStroke(1.dp, if (trackPower) colors.signalGreen else MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (trackPower) colors.signalGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.Filled.PowerSettingsNew, null, modifier = Modifier.size(18.dp))
                if (!compact) {
                    Spacer(Modifier.width(6.dp))
                    Text(if (trackPower) "Gleis an" else "Gleis aus")
                }
            }

            if (!compact) {
                IconButton(onClick = { app.setDarkTheme(!dark) }) {
                    Icon(if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode, "Hell/Dunkel umschalten")
                }
            }

            // Nothalt: immer sichtbar, nie lizenzpflichtig
            Button(
                onClick = { app.emergencyStop() },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = colors.alarm, contentColor = MaterialTheme.colorScheme.onError),
                contentPadding = ButtonDefaults.ContentPadding,
                modifier = Modifier.height(44.dp),
            ) {
                Icon(Icons.Filled.Block, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("NOTHALT", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
