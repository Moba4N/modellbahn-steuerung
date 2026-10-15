package de.moba.modules.routes.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.moba.modules.routes.api.AutomationStatus
import de.moba.modules.routes.api.Feedback
import de.moba.modules.routes.api.ROUTES_MODULE_ID
import de.moba.modules.routes.api.Route
import de.moba.modules.routes.api.RouteService
import de.moba.modules.routes.api.RouteSetting
import de.moba.modules.routes.api.Shuttle
import de.moba.modules.routes.api.TrainRoute
import de.moba.modules.routes.api.TrainRouteStep
import de.moba.ui.shell.LocalWideLayout
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.ModuleScreen
import de.moba.ui.shell.SectionCard
import de.moba.ui.shell.StatusPill
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Die drei Bereiche des Reiters. */
enum class RoutesTab(val label: String, val prefix: String) {
    ROUTES("Fahrstraßen", "fs-"),
    SHUTTLES("Pendelstrecken", "pe-"),
    TRAINS("Zugrouten", "zr-"),
}

/** Gewaehlter Bereich und gewaehlter Eintrag; [NEW] steht fuer einen noch nicht gespeicherten. */
object RoutesUiState {
    const val NEW = "<neu>"
    val tab = MutableStateFlow(RoutesTab.ROUTES)
    val selected = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)

    fun select(id: String?) {
        selected.value = id
        message.value = null
    }
}

const val ROUTES_EDITOR_TAG = "fahrstrassen-editor"

/** Reiter "Fahrstraßen": Fahrstrassen, Pendelstrecken und Zugrouten anlegen, stellen und fahren lassen. */
object RoutesScreen : ModuleScreen {
    override val moduleId = ROUTES_MODULE_ID
    override val title = "Fahrstraßen"
    override val icon = Icons.Outlined.AltRoute

    @Composable
    override fun Content(app: MobaApp) {
        val session by app.session.collectAsState()
        val service = session?.host?.service(RouteService::class) ?: return
        val tab by RoutesUiState.tab.collectAsState()
        val selected by RoutesUiState.selected.collectAsState()
        val wide = LocalWideLayout.current

        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TabChips(tab)
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.width(420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ListCard(service, tab)
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        EditorCard(app, service, tab, selected)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (selected != null) EditorCard(app, service, tab, selected) else ListCard(service, tab)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TabChips(tab: RoutesTab) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RoutesTab.entries.forEach { t ->
            FilterChip(
                selected = tab == t,
                onClick = {
                    RoutesUiState.tab.value = t
                    RoutesUiState.select(null)
                },
                label = { Text(t.label) },
                leadingIcon = { Icon(t.icon, null, Modifier.size(18.dp)) },
            )
        }
    }
}

private val RoutesTab.singular: String
    get() = when (this) {
        RoutesTab.ROUTES -> "Fahrstraße"
        RoutesTab.SHUTTLES -> "Pendelstrecke"
        RoutesTab.TRAINS -> "Zugroute"
    }

private val RoutesTab.icon: ImageVector
    get() = when (this) {
        RoutesTab.ROUTES -> Icons.Outlined.Route
        RoutesTab.SHUTTLES -> Icons.Outlined.SwapHoriz
        RoutesTab.TRAINS -> Icons.Outlined.Train
    }

// ------------------------------------------------------------------ Liste

@Composable
private fun ListCard(service: RouteService, tab: RoutesTab) {
    val book by service.book.collectAsState()
    val active by service.activeRoutes.collectAsState()
    val automation by service.automation.collectAsState()
    val message by RoutesUiState.message.collectAsState()
    val scope = rememberCoroutineScope()

    SectionCard(
        tab.label,
        tab.icon,
        trailing = {
            OutlinedButton(onClick = { RoutesUiState.select(RoutesUiState.NEW) }) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Neu")
            }
        },
    ) {
        LicenseHint(service, tab)
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        when (tab) {
            RoutesTab.ROUTES -> {
                if (book.routes.isEmpty()) Empty("Noch keine Fahrstraße. Mit „Neu“ Weichen und Signale zusammenstellen.")
                book.routes.forEach { r ->
                    val isSet = r.id in active
                    ListRow(
                        title = r.name,
                        detail = r.settings.joinToString("  ") { "${it.address} ${if (it.thrown) "abzw." else "gerade"}" }.ifEmpty { "keine Weichen" },
                        pill = if (isSet) "Gestellt" else null,
                        onEdit = { RoutesUiState.select(r.id) },
                    ) {
                        if (isSet) {
                            OutlinedButton(onClick = { service.releaseRoute(r.id) }) {
                                Icon(Icons.Outlined.LockOpen, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Auflösen")
                            }
                        } else {
                            Button(enabled = service.canSetRoutes, onClick = {
                                scope.launch {
                                    runCatching { service.setRoute(r.id) }
                                        .onSuccess { RoutesUiState.message.value = null }
                                        .onFailure { RoutesUiState.message.value = it.message }
                                }
                            }) {
                                Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Stellen")
                            }
                        }
                    }
                }
            }
            RoutesTab.SHUTTLES -> {
                if (book.shuttles.isEmpty()) Empty("Noch keine Pendelstrecke. Mit „Neu“ Lok und die beiden Endmelder festlegen.")
                book.shuttles.forEach { s ->
                    AutomationRow(service, s.id, s.name, "Lok ${s.locoAddress} · ${s.endA ?: "?"} ↔ ${s.endB ?: "?"}", automation[s.id]) {
                        service.startShuttle(s.id)
                    }
                }
            }
            RoutesTab.TRAINS -> {
                if (book.trainRoutes.isEmpty()) Empty("Noch keine Zugroute. Zuerst Fahrstraßen anlegen, dann hier aneinanderreihen.")
                book.trainRoutes.forEach { t ->
                    val names = t.steps.map { step -> book.routes.firstOrNull { it.id == step.routeId }?.name ?: "?" }
                    AutomationRow(
                        service, t.id, t.name,
                        "Lok ${t.locoAddress} · ${names.joinToString(" → ").ifEmpty { "keine Abschnitte" }}${if (t.repeat) " · wiederholen" else ""}",
                        automation[t.id],
                    ) { service.startTrainRoute(t.id) }
                }
            }
        }
    }
}

@Composable
private fun AutomationRow(
    service: RouteService,
    id: String,
    name: String,
    detail: String,
    status: AutomationStatus?,
    start: () -> Unit,
) {
    val running = status?.running == true
    ListRow(
        title = name,
        detail = detail,
        pill = if (running) "Fährt" else null,
        status = status?.text,
        onEdit = { RoutesUiState.select(id) },
    ) {
        if (running) {
            OutlinedButton(onClick = { service.stopAutomation(id) }) {
                Icon(Icons.Filled.Stop, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Stopp")
            }
        } else {
            Button(enabled = service.canAutomate, onClick = {
                runCatching(start)
                    .onSuccess { RoutesUiState.message.value = null }
                    .onFailure { RoutesUiState.message.value = it.message }
            }) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Start")
            }
        }
    }
}

@Composable
private fun ListRow(
    title: String,
    detail: String,
    pill: String?,
    status: String? = null,
    onEdit: () -> Unit,
    actions: @Composable () -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Name kuerzen, nicht die Pille daneben zusammendruecken
                    modifier = Modifier.weight(1f, fill = false),
                )
                pill?.let { StatusPill(it, MaterialTheme.colorScheme.primary) }
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Bearbeiten") }
        actions()
    }
}

@Composable
private fun LicenseHint(service: RouteService, tab: RoutesTab) {
    val text = when {
        tab == RoutesTab.ROUTES && !service.canSetRoutes -> "Anlegen geht immer. Fahrstraßen stellen gibt es ab der Basis-Lizenz."
        tab != RoutesTab.ROUTES && !service.canAutomate -> "Anlegen geht immer. Pendelzüge und Zugrouten fahren lassen gibt es ab Pro."
        else -> return
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
}

@Composable
private fun Empty(text: String) =
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)

// ------------------------------------------------------------------ Editoren

@Composable
private fun EditorCard(app: MobaApp, service: RouteService, tab: RoutesTab, selected: String?) {
    val book by service.book.collectAsState()
    if (selected == null) {
        SectionCard("Bearbeiten", Icons.Outlined.Edit) {
            Empty("Links einen Eintrag wählen oder mit „Neu“ anlegen.")
        }
        return
    }
    val isNew = selected == RoutesUiState.NEW
    val id = if (isNew) remember(tab, book) { service.newId(tab.prefix) } else selected
    val save: (() -> Unit) -> Unit = { action ->
        runCatching(action)
            .onSuccess { RoutesUiState.select(id) }
            .onFailure { RoutesUiState.message.value = it.message }
    }
    val title = (if (isNew) "Neu: " else "Bearbeiten: ") + tab.singular
    SectionCard(
        title,
        Icons.Outlined.Edit,
        modifier = Modifier.testTag(ROUTES_EDITOR_TAG),
        trailing = {
            IconButton(onClick = { RoutesUiState.select(null) }) { Icon(Icons.Outlined.Close, contentDescription = "Schließen") }
        },
    ) {
        when (tab) {
            RoutesTab.ROUTES -> RouteEditor(book.routes.firstOrNull { it.id == id } ?: Route(id, ""), isNew, save, service)
            RoutesTab.SHUTTLES -> ShuttleEditor(app, book.shuttles.firstOrNull { it.id == id } ?: Shuttle(id, "", 3), isNew, save, service)
            RoutesTab.TRAINS -> TrainRouteEditor(app, book.routes, book.trainRoutes.firstOrNull { it.id == id } ?: TrainRoute(id, "", 3), isNew, save, service)
        }
    }
}

private class SettingDraft(address: String, thrown: Boolean) {
    var address by mutableStateOf(address)
    var thrown by mutableStateOf(thrown)
}

@Composable
private fun RouteEditor(route: Route, isNew: Boolean, save: (() -> Unit) -> Unit, service: RouteService) {
    var name by remember(route.id) { mutableStateOf(route.name) }
    val settings = remember(route.id) { mutableStateListOf(*route.settings.map { SettingDraft(it.address.toString(), it.thrown) }.toTypedArray()) }
    var start by remember(route.id) { mutableStateOf(route.start?.toString() ?: "") }
    var end by remember(route.id) { mutableStateOf(route.end?.toString() ?: "") }
    val message by RoutesUiState.message.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NameField(name) { name = it }
        Label("Weichen und Signale")
        if (settings.isEmpty()) Empty("Noch keine Weiche eingetragen.")
        settings.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Adresse", s.address, Modifier.width(110.dp)) { s.address = it }
                FilterChip(selected = !s.thrown, onClick = { s.thrown = false }, label = { Text("Gerade") })
                FilterChip(selected = s.thrown, onClick = { s.thrown = true }, label = { Text("Abzweig") })
                IconButton(onClick = { settings.removeAt(i) }) { Icon(Icons.Outlined.Delete, contentDescription = "Weiche entfernen") }
            }
        }
        TextButton(onClick = { settings += SettingDraft("", false) }) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Weiche oder Signal hinzufügen")
        }
        Label("Melder")
        FeedbackRow {
            FeedbackField("Start (optional)", start) { start = it }
            FeedbackField("Ziel: löst auf", end) { end = it }
        }
        Hint("Melder als s88-Modul.Kontakt, z. B. 1.3. Wird der Zielmelder belegt, löst sich die Fahrstraße von selbst auf.")
        message?.let { Error(it) }
        EditorButtons(isNew, onDelete = { service.deleteRoute(route.id) }) {
            save {
                val parsed = settings.map { RouteSetting(it.address.toIntOrNull() ?: 0, it.thrown) }
                service.saveRoute(Route(route.id, name.trim(), parsed, parseFeedback(start, "Start"), parseFeedback(end, "Ziel")))
            }
        }
    }
}

@Composable
private fun ShuttleEditor(app: MobaApp, shuttle: Shuttle, isNew: Boolean, save: (() -> Unit) -> Unit, service: RouteService) {
    var name by remember(shuttle.id) { mutableStateOf(shuttle.name) }
    var loco by remember(shuttle.id) { mutableStateOf(shuttle.locoAddress.toString()) }
    var speed by remember(shuttle.id) { mutableStateOf(shuttle.speed.toString()) }
    var brakeSpeed by remember(shuttle.id) { mutableStateOf(shuttle.brakeSpeed.toString()) }
    var wait by remember(shuttle.id) { mutableStateOf(shuttle.waitSeconds.toString()) }
    var endA by remember(shuttle.id) { mutableStateOf(shuttle.endA?.toString() ?: "") }
    var endB by remember(shuttle.id) { mutableStateOf(shuttle.endB?.toString() ?: "") }
    var brakeA by remember(shuttle.id) { mutableStateOf(shuttle.brakeA?.toString() ?: "") }
    var brakeB by remember(shuttle.id) { mutableStateOf(shuttle.brakeB?.toString() ?: "") }
    val message by RoutesUiState.message.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NameField(name) { name = it }
        LocoPicker(app, loco) { loco = it }
        FeedbackRow {
            NumberField("Fahrstufe (0–126)", speed, Modifier.width(160.dp)) { speed = it }
            NumberField("Bremsstufe", brakeSpeed, Modifier.width(130.dp)) { brakeSpeed = it }
            NumberField("Wartezeit in s", wait, Modifier.width(140.dp)) { wait = it }
        }
        Label("Ende A (Rückwärts dorthin)")
        FeedbackRow {
            FeedbackField("Endmelder A", endA) { endA = it }
            FeedbackField("Bremsmelder A", brakeA) { brakeA = it }
        }
        Label("Ende B (Vorwärts dorthin)")
        FeedbackRow {
            FeedbackField("Endmelder B", endB) { endB = it }
            FeedbackField("Bremsmelder B", brakeB) { brakeB = it }
        }
        Hint("Die Lok fährt vorwärts bis Melder B, hält, wartet und fährt rückwärts bis Melder A. Bremsmelder sind freiwillig: dort fährt sie mit der Bremsstufe weiter.")
        message?.let { Error(it) }
        EditorButtons(isNew, onDelete = { service.deleteShuttle(shuttle.id) }) {
            save {
                service.saveShuttle(
                    Shuttle(
                        shuttle.id, name.trim(), loco.toIntOrNull() ?: 0, speedOf(speed, 60),
                        parseFeedback(endA, "Ende A"), parseFeedback(endB, "Ende B"),
                        parseFeedback(brakeA, "Bremsmelder A"), parseFeedback(brakeB, "Bremsmelder B"),
                        speedOf(brakeSpeed, 20), wait.toIntOrNull() ?: 0,
                    ),
                )
            }
        }
    }
}

private class StepDraft(routeId: String, stopAt: String, wait: String, speed: String) {
    var routeId by mutableStateOf(routeId)
    var stopAt by mutableStateOf(stopAt)
    var wait by mutableStateOf(wait)
    var speed by mutableStateOf(speed)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrainRouteEditor(
    app: MobaApp,
    routes: List<Route>,
    train: TrainRoute,
    isNew: Boolean,
    save: (() -> Unit) -> Unit,
    service: RouteService,
) {
    var name by remember(train.id) { mutableStateOf(train.name) }
    var loco by remember(train.id) { mutableStateOf(train.locoAddress.toString()) }
    var speed by remember(train.id) { mutableStateOf(train.speed.toString()) }
    var forward by remember(train.id) { mutableStateOf(train.forward) }
    var repeat by remember(train.id) { mutableStateOf(train.repeat) }
    val steps = remember(train.id) {
        mutableStateListOf(
            *train.steps.map { StepDraft(it.routeId, it.stopAt?.toString() ?: "", it.waitSeconds.toString(), it.speed?.toString() ?: "") }.toTypedArray(),
        )
    }
    val message by RoutesUiState.message.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NameField(name) { name = it }
        LocoPicker(app, loco) { loco = it }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Fahrstufe (0–126)", speed, Modifier.width(160.dp)) { speed = it }
            FilterChip(selected = forward, onClick = { forward = true }, label = { Text("Vorwärts") })
            FilterChip(selected = !forward, onClick = { forward = false }, label = { Text("Rückwärts") })
            FilterChip(selected = repeat, onClick = { repeat = !repeat }, label = { Text("Immer wieder von vorn") })
        }
        Label("Abschnitte")
        if (routes.isEmpty()) Empty("Zuerst unter „Fahrstraßen“ mindestens eine Fahrstraße anlegen.")
        else if (steps.isEmpty()) Empty("Noch kein Abschnitt.")
        steps.forEachIndexed { i, step ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    RoutePicker(routes, step.routeId, Modifier.weight(1f)) { step.routeId = it }
                    IconButton(onClick = { steps.removeAt(i) }) { Icon(Icons.Outlined.Delete, contentDescription = "Abschnitt entfernen") }
                }
                FeedbackRow {
                    FeedbackField("Halt an Melder", step.stopAt) { step.stopAt = it }
                    NumberField("Halt in s", step.wait, Modifier.width(110.dp)) { step.wait = it }
                    NumberField("Fahrstufe", step.speed, Modifier.width(120.dp)) { step.speed = it }
                }
            }
        }
        TextButton(enabled = routes.isNotEmpty(), onClick = { steps += StepDraft(routes.first().id, "", "0", "") }) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Abschnitt hinzufügen")
        }
        Hint(
            "Für jeden Abschnitt stellt die App die Fahrstraße, sobald sie frei ist, fährt bis zum Haltemelder " +
                "(leer = Zielmelder der Fahrstraße), hält, löst die Fahrstraße auf und wartet. Leere Fahrstufe = die der Zugroute.",
        )
        message?.let { Error(it) }
        EditorButtons(isNew, onDelete = { service.deleteTrainRoute(train.id) }) {
            save {
                val parsed = steps.mapIndexed { i, s ->
                    TrainRouteStep(s.routeId, parseFeedback(s.stopAt, "Abschnitt ${i + 1}"), s.wait.toIntOrNull() ?: 0, s.speed.toIntOrNull()?.coerceIn(0, 126))
                }
                service.saveTrainRoute(TrainRoute(train.id, name.trim(), loco.toIntOrNull() ?: 0, speedOf(speed, 60), forward, parsed, repeat))
            }
        }
    }
}

// ------------------------------------------------------------------ Bausteine

@Composable
private fun RoutePicker(routes: List<Route>, selected: String, modifier: Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(routes.firstOrNull { it.id == selected }?.name ?: "Fahrstraße wählen", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            routes.forEach { r ->
                DropdownMenuItem(text = { Text(r.name) }, onClick = {
                    onPick(r.id)
                    open = false
                })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocoPicker(app: MobaApp, address: String, onChange: (String) -> Unit) {
    val layout by app.layout.layout.collectAsState()
    Label("Lok")
    if (layout.locos.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            layout.locos.forEach { loco ->
                FilterChip(selected = address == loco.address.toString(), onClick = { onChange(loco.address.toString()) }, label = { Text(loco.name) })
            }
        }
    }
    NumberField("Adresse", address, Modifier.width(140.dp), onChange)
}

@Composable
private fun EditorButtons(isNew: Boolean, onDelete: () -> Unit, onSave: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onSave) { Text("Speichern") }
        if (!isNew) {
            TextButton(onClick = {
                onDelete()
                RoutesUiState.select(null)
            }) {
                Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Löschen")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedbackRow(content: @Composable () -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

@Composable
private fun FeedbackField(label: String, value: String, onChange: (String) -> Unit) =
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }.take(7)) },
        label = { Text(label) },
        placeholder = { Text("z. B. 1.3") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.width(200.dp),
    )

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) =
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )

@Composable
private fun Label(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Error(text: String) =
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)

private fun speedOf(text: String, default: Int) = (text.toIntOrNull() ?: default).coerceIn(0, 126)

/** "1.3" zu Melder; leer = keiner. @throws IllegalArgumentException bei falscher Schreibweise */
internal fun parseFeedback(text: String, what: String): Feedback? {
    if (text.isBlank()) return null
    val p = text.trim().split('.')
    val module = p.getOrNull(0)?.toIntOrNull()
    val contact = p.getOrNull(1)?.toIntOrNull()
    require(p.size == 2 && module != null && contact != null && module >= 1 && contact in 1..16) {
        "$what: Melder bitte als Modul.Kontakt schreiben, z. B. 1.3 (Kontakt 1 bis 16)."
    }
    return Feedback(module, contact)
}
