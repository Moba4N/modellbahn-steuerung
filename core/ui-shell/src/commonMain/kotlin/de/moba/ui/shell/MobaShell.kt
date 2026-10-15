package de.moba.ui.shell

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.outlined.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material.icons.outlined.Speed
import kotlinx.coroutines.flow.drop
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

/** Zustand des App-Rahmens, von aussen setzbar (z. B. fuer Vorschaubilder). */
object ShellUiState {
    /** Burger-Menue oben rechts aufgeklappt. */
    val menuOpen = MutableStateFlow(false)

    /** Dialog "Erscheinungsbild" offen. */
    val appearanceOpen = MutableStateFlow(false)

    /** Dialog "Lizenz" offen. */
    val licenseOpen = MutableStateFlow(false)

    /** Rechte Leiste (Fahrpult) eingeblendet; anfangs aus, die Wahl bleibt gespeichert. */
    val panelOpen = MutableStateFlow(false)

    /** Modul-Id des offenen Werkzeugs aus dem Burger-Menue, z. B. Monitor; `null` = keins. */
    val openTool = MutableStateFlow<String?>(null)
}

/** true, wenn die rechte Leiste (z. B. Fahrpult) sichtbar ist. Module zeigen ihre Inhalte sonst selbst an. */
val LocalWideLayout = compositionLocalOf { false }

/** Ist die rechte Leiste (Fahrpult) gerade zu sehen? Sonst zeigt z. B. "Fahren" das Fahrpult selbst. */
val LocalPanelVisible = compositionLocalOf { false }

private const val PREF_PANEL = "shell.fahrpult"

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
    imagePicker: ImagePicker? = null,
    docks: List<ModuleDock> = emptyList(),
    /** Werkzeuge, die nur ueber das Burger-Menue erreichbar sind (z. B. Monitor). */
    tools: List<ModuleTool> = emptyList(),
    /** Fragt vor dem Beenden nach ungespeicherten Aenderungen; [onExit] beendet dann die App. */
    exitGuard: ExitGuard? = null,
    onExit: () -> Unit = {},
) {
    val dark by app.darkTheme.collectAsState()
    val appearance by app.appearance.collectAsState()
    val background by app.backgroundImage.collectAsState()
    val appearanceOpen by ShellUiState.appearanceOpen.collectAsState()
    val licenseOpen by ShellUiState.licenseOpen.collectAsState()
    val session by app.session.collectAsState()
    val emptyStatus = remember { MutableStateFlow(emptyMap<String, ModuleStatus>()) }
    val modules by (session?.host?.status ?: emptyStatus).collectAsState()
    fun active(id: String) = modules[id]?.state == ModuleState.ACTIVE
    val visible = screens.filter { active(it.moduleId) }
    val activePanels = panels.filter { active(it.moduleId) }
    val activeDocks = docks.filter { active(it.moduleId) }
    val activeTools = tools.filter { active(it.moduleId) }
    val openTool by ShellUiState.openTool.collectAsState()
    LaunchedEffect(app) {
        ShellUiState.panelOpen.value = app.preference(PREF_PANEL) == "an"
        ShellUiState.panelOpen.drop(1).collect { app.setPreference(PREF_PANEL, if (it) "an" else "aus") }
    }
    val panelOpen by ShellUiState.panelOpen.collectAsState()
    var current by remember { mutableStateOf(initialScreen ?: OVERVIEW) }
    if (current != OVERVIEW && visible.none { it.moduleId == current }) current = OVERVIEW

    MobaTheme(dark, appearance) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= WIDE_LAYOUT
                val showPanel = wide && panelOpen && activePanels.isNotEmpty()
                CompositionLocalProvider(
                    LocalWideLayout provides wide,
                    LocalImagePicker provides imagePicker,
                    LocalPanelVisible provides showPanel,
                ) {
                Column(Modifier.fillMaxSize()) {
                    TopBar(
                        app,
                        compact = !wide,
                        panelToggle = wide && activePanels.isNotEmpty(),
                        destinations = listOf(Destination(OVERVIEW, "Übersicht", Icons.Outlined.Dashboard)) +
                            visible.map { Destination(it.moduleId, it.title, it.icon) },
                        tools = activeTools.map { Destination(it.moduleId, it.title, it.icon) },
                        current = current,
                        onNavigate = { current = it },
                    )
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
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            background?.let { image ->
                                Image(
                                    image,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    alpha = appearance.watermark.alpha,
                                    modifier = Modifier.matchParentSize(),
                                )
                            }
                            Box(Modifier.fillMaxSize().padding(if (wide) 24.dp else 12.dp)) {
                                val screen = visible.firstOrNull { it.moduleId == current }
                                if (screen == null) Overview(app, modules, wide) else screen.Content(app)
                            }
                        }
                        if (showPanel) {
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Surface(
                                Modifier.width(380.dp).fillMaxHeight(),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                // Scrollt, damit auch viele Funktionstasten (bis F31) Platz haben
                                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) { activePanels.forEach { it.Content(app) } }
                            }
                        }
                    }
                    activeDocks.forEach { dock ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        dock.Content(app)
                    }
                    if (!wide) {
                        // Ab sechs Eintraegen kleinere Beschriftung, damit nichts umbricht
                        val compact = visible.size + 1 > 5
                        // Ab sieben Eintraegen nur noch beim gewaehlten Reiter beschriften, sonst wird alles abgekuerzt
                        val crowded = visible.size + 1 > 6
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                            NavigationBarItem(
                                selected = current == OVERVIEW,
                                onClick = { current = OVERVIEW },
                                icon = { Icon(Icons.Outlined.Dashboard, null) },
                                label = { NavLabel("Übersicht", compact) },
                                alwaysShowLabel = !crowded,
                            )
                            visible.forEach { screen ->
                                NavigationBarItem(
                                    selected = current == screen.moduleId,
                                    onClick = { current = screen.moduleId },
                                    icon = { Icon(screen.icon, null) },
                                    label = { NavLabel(screen.title, compact) },
                                    alwaysShowLabel = !crowded,
                                )
                            }
                        }
                    }
                }
                }
            }
            if (appearanceOpen) {
                AppearanceDialog(app, imagePicker, onDismiss = { ShellUiState.appearanceOpen.value = false })
            }
            if (licenseOpen) {
                LicenseDialog(app, onDismiss = { ShellUiState.licenseOpen.value = false })
            }
            activeTools.firstOrNull { it.moduleId == openTool }?.let { tool ->
                ToolWindow(app, tool) { ShellUiState.openTool.value = null }
            }
            if (exitGuard != null && exitGuard.asking) {
                ExitDialog(visible.mapNotNull { it.unsavedWork(app) }, exitGuard, onExit)
            }
        }
    }
}

private class Destination(val id: String, val title: String, val icon: ImageVector)

/** Grosses Fenster ueber der App fuer ein Werkzeug aus dem Burger-Menue. */
@Composable
private fun ToolWindow(app: MobaApp, tool: ModuleTool, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f),
        ) {
            tool.Content(app, onClose)
        }
    }
}

@Composable
private fun TopBar(
    app: MobaApp,
    compact: Boolean,
    panelToggle: Boolean,
    destinations: List<Destination>,
    tools: List<Destination>,
    current: String,
    onNavigate: (String) -> Unit,
) {
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

            // Fahrpult rechts ein- und ausblenden
            if (panelToggle) {
                val open by ShellUiState.panelOpen.collectAsState()
                FilledTonalIconToggleButton(
                    checked = open,
                    onCheckedChange = { ShellUiState.panelOpen.value = it },
                ) {
                    Icon(Icons.Outlined.Speed, if (open) "Fahrpult ausblenden" else "Fahrpult einblenden")
                }
            }

            BurgerMenu(app, dark, destinations, tools, current, onNavigate)

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

/** Menue neben "Gleis an/aus": Ansichten wechseln, Erscheinungsbild, hell/dunkel. */
@Composable
private fun BurgerMenu(
    app: MobaApp,
    dark: Boolean,
    destinations: List<Destination>,
    tools: List<Destination>,
    current: String,
    onNavigate: (String) -> Unit,
) {
    val open by ShellUiState.menuOpen.collectAsState()
    fun close() {
        ShellUiState.menuOpen.value = false
    }
    Box {
        IconButton(onClick = { ShellUiState.menuOpen.value = !open }) {
            Icon(Icons.Filled.Menu, "Menü")
        }
        DropdownMenu(expanded = open, onDismissRequest = ::close, modifier = Modifier.widthIn(min = 240.dp)) {
            Text(
                "Ansicht",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            destinations.forEach { dest ->
                val selected = dest.id == current
                DropdownMenuItem(
                    text = { Text(dest.title, fontWeight = if (selected) FontWeight.SemiBold else null) },
                    leadingIcon = { Icon(dest.icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current) },
                    trailingIcon = if (selected) {
                        { Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    onClick = {
                        onNavigate(dest.id)
                        close()
                    },
                )
            }
            if (tools.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    "Werkzeuge",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                tools.forEach { tool ->
                    DropdownMenuItem(
                        text = { Text("${tool.title} …") },
                        leadingIcon = { Icon(tool.icon, null) },
                        onClick = {
                            close()
                            ShellUiState.openTool.value = tool.id
                        },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("Erscheinungsbild …") },
                leadingIcon = { Icon(Icons.Outlined.Palette, null) },
                onClick = {
                    close()
                    ShellUiState.appearanceOpen.value = true
                },
            )
            val license by app.activeLicense.collectAsState()
            DropdownMenuItem(
                text = { Text("Lizenz …") },
                leadingIcon = { Icon(Icons.Outlined.Key, null) },
                trailingIcon = {
                    Text(editionLabel(license), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                onClick = {
                    close()
                    ShellUiState.licenseOpen.value = true
                },
            )
            DropdownMenuItem(
                text = { Text(if (dark) "Helles Design" else "Dunkles Design") },
                leadingIcon = { Icon(if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode, null) },
                onClick = {
                    app.setDarkTheme(!dark)
                    close()
                },
            )
        }
    }
}

@Composable
private fun NavLabel(text: String, compact: Boolean) {
    Text(
        text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
    )
}
