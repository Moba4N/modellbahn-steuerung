package de.moba.modules.trackplan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.AdsClick
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Rotate90DegreesCcw
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.moba.modules.trackplan.api.LicenseRequiredException
import de.moba.modules.trackplan.api.TRACKPLAN_MODULE_ID
import de.moba.modules.trackplan.api.TileKind
import de.moba.modules.trackplan.api.TrackPlan
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.api.TrackShape
import de.moba.modules.trackplan.api.TrackTile
import de.moba.modules.symbols.api.SymbolService
import de.moba.ui.shell.LocalWideLayout
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.Moba
import de.moba.ui.shell.ModuleScreen
import de.moba.ui.shell.ShellUiState
import de.moba.ui.shell.StatusPill
import de.moba.ui.shell.UnsavedWork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

enum class TrackPlanMode { BETRIEB, BEARBEITEN }

/** Werkzeug im Editor: auswaehlen, drehen, entfernen oder eine Gleisform setzen. */
sealed interface EditTool {
    data object Select : EditTool
    data object Rotate : EditTool
    data object Erase : EditTool
    data class Place(val shape: TrackShape) : EditTool
}

/** Zustand der Gleisbild-Ansicht, bleibt beim Wechsel zwischen den Reitern erhalten. */
object TrackPlanUiState {
    val mode = MutableStateFlow(TrackPlanMode.BETRIEB)
    val editPlan = MutableStateFlow<String?>(null)
    val tool = MutableStateFlow<EditTool>(EditTool.Place(TrackShape.STRAIGHT))

    /** Drehung neu gesetzter Gleise in 45-Grad-Schritten; 2 = waagerecht. */
    val placeRotation = MutableStateFlow(2)
    val selected = MutableStateFlow<Pair<Int, Int>?>(null)
    /** Groesse eines Gleissymbols in dp, im Betrieb und im Editor; bleibt ueber Neustarts erhalten. */
    val tileSize = MutableStateFlow(DEFAULT_TILE)

    /** Groesse der Vorschaubilder in der Leiste "Gleisformen" in dp; bleibt ueber Neustarts erhalten. */
    val paletteSize = MutableStateFlow(DEFAULT_PALETTE)
    val message = MutableStateFlow<String?>(null)

    /** Signalbild fuer neu gesetzte Signale: das zuletzt gewaehlte, anfangs das H/V-Lichthauptsignal. */
    val lastSignal = MutableStateFlow("signal:de-hv-hp")

    /** Offener Reiter der Zubehoer-Eigenschaften (Allgemein, Decoder, Steuerung ...). */
    val accessoryTab = MutableStateFlow(0)

    /** Offene Steuerung einer Drehscheibe/Schiebebuehne im Betrieb als (Gleisbild-Id, Zelle). */
    val openTable = MutableStateFlow<Pair<String, Pair<Int, Int>>?>(null)
}

/** Reiter "Gleisbild": Betrieb (Weichen schalten, Belegung sehen) und Editor. */
object TrackPlanScreen : ModuleScreen {
    override val moduleId = TRACKPLAN_MODULE_ID
    override val title = "Gleisbild"
    override val icon = Icons.Outlined.AltRoute

    override fun unsavedWork(app: MobaApp): UnsavedWork? {
        val service = app.session.value?.host?.service(TrackPlanService::class) ?: return null
        if (!service.unsaved.value) return null
        return UnsavedWork("Gleisbilder", service.canSave) { service.save() }
    }


    @Composable
    @OptIn(ExperimentalLayoutApi::class)
    override fun Content(app: MobaApp) {
        // Gleisgroesse aus den Einstellungen holen und jede Aenderung dort ablegen
        LaunchedEffect(app) {
            app.preference(PREF_TILE_SIZE)?.toIntOrNull()?.let { TrackPlanUiState.tileSize.value = it.coerceIn(MIN_CELL, MAX_CELL) }
            TrackPlanUiState.tileSize.drop(1).collect { app.setPreference(PREF_TILE_SIZE, it.toString()) }
        }
        LaunchedEffect(app) {
            app.preference(PREF_PALETTE_SIZE)?.toIntOrNull()?.let {
                TrackPlanUiState.paletteSize.value = it.coerceIn(MIN_PALETTE, MAX_PALETTE)
            }
            TrackPlanUiState.paletteSize.drop(1).collect { app.setPreference(PREF_PALETTE_SIZE, it.toString()) }
        }
        val session by app.session.collectAsState()
        val service = session?.host?.service(TrackPlanService::class) ?: return
        val symbolService = session?.host?.service(SymbolService::class)
        val library = symbolService?.symbols?.collectAsState()?.value.orEmpty()
        val mode by TrackPlanUiState.mode.collectAsState()
        val message by TrackPlanUiState.message.collectAsState()
        CompositionLocalProvider(LocalSymbolLibrary provides library) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // FlowRow: auf dem Handy rutscht die Gleisgroesse in die naechste Zeile statt abgeschnitten zu werden
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("Betrieb", Icons.Filled.PlayArrow, mode == TrackPlanMode.BETRIEB) {
                        TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
                        TrackPlanUiState.message.value = null
                    }
                    ModeChip("Bearbeiten", Icons.Filled.Edit, mode == TrackPlanMode.BEARBEITEN) {
                        TrackPlanUiState.mode.value = TrackPlanMode.BEARBEITEN
                        TrackPlanUiState.message.value = null
                    }
                    if (mode == TrackPlanMode.BETRIEB) TileSizeControl()
                    message?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                when (mode) {
                    TrackPlanMode.BETRIEB -> OperationView(app, service)
                    TrackPlanMode.BEARBEITEN -> EditorView(service)
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(18.dp)) },
    )
}

internal fun say(text: String?) {
    TrackPlanUiState.message.value = text
}

/**
 * Aendert das Gleisbild [planId] ausgehend von seinem aktuellen Stand im Dienst. So schreibt eine
 * Eingabe nie eine aeltere Fassung zurueck, die eine Ansicht noch im Speicher hat.
 */
private fun TrackPlanService.change(planId: String, transform: (TrackPlan) -> TrackPlan) {
    val current = plans.value.firstOrNull { it.id == planId } ?: return
    update(transform(current))
}

// ---------------------------------------------------------------- Betrieb

/** Was ein Gleisbild im Betrieb braucht: Zustand der Weichen und Melder, Tippen auf Kacheln, Signalbild-Menue. */
internal class PlanOperation(
    val state: TrackState,
    val onTap: (TrackPlan, Int, Int) -> Unit,
    val menu: (TrackPlan) -> AspectOverlayData?,
)

/** Betrieb eines Gleisbilds; gleich im Hauptfenster und in eigenen Fenstern. */
@Composable
internal fun rememberPlanOperation(service: TrackPlanService): PlanOperation {
    val turnouts by service.turnouts.collectAsState()
    val occupied by service.occupied.collectAsState()
    val scope = rememberCoroutineScope()
    val library = LocalSymbolLibrary.current
    val aspects by service.signalAspects.collectAsState()
    val positions by service.tablePositions.collectAsState()
    val allPlans by service.plans.collectAsState()
    val moving by service.tablesMoving.collectAsState()
    val state = TrackState(
        turnouts, occupied, operating = true, symbolImage = { symbolImage(it, turnouts, aspects, library) },
        aspects = aspects, tablePositions = positions, moving = moving,
    )
    // Offenes Signalbild-Menue (Gleisbild-Id, Kachel), wie in iTrain per Klick aufs Signal
    var aspectMenu by remember { mutableStateOf<Pair<String, TrackTile>?>(null) }
    val table by TrackPlanUiState.openTable.collectAsState()
    val onTap = { plan: TrackPlan, x: Int, y: Int ->
        plan.objectAt(x, y)?.takeIf { it.shape.hasAddress && (it.address != null || it.shape.kind == TileKind.SOUND) }?.let { tile ->
            when {
                tile.shape.isTable -> TrackPlanUiState.openTable.value = plan.id to (tile.x to tile.y)
                tile.shape.kind == TileKind.ASPECT ||
                    tile.shape.kind == TileKind.SIGNAL && tileStates(tile, library).size > 2 -> aspectMenu = plan.id to tile
                else -> scope.launch {
                    runCatching { service.operate(tile) }.onFailure { say("Schalten nicht möglich: ${it.message}") }
                }
            }
        }
        Unit
    }
    table?.let { (planId, cell) ->
        // Immer die aktuelle Fassung der Kachel, falls sie im Editor geaendert wurde
        val tile = allPlans.firstOrNull { it.id == planId }?.tileAt(cell.first, cell.second)
        if (tile == null) TrackPlanUiState.openTable.value = null
        else TableControlDialog(service, tile, onDismiss = { TrackPlanUiState.openTable.value = null })
    }
    val menu = { plan: TrackPlan ->
        aspectMenu?.takeIf { it.first == plan.id }?.second?.let { tile ->
            AspectOverlay(tile, aspects[tile.address], onPick = { i ->
                aspectMenu = null
                scope.launch {
                    runCatching { service.setAspect(tile, i) }.onFailure { say("Schalten nicht möglich: ${it.message}") }
                }
            }, onDismiss = { aspectMenu = null })
        }
    }
    return PlanOperation(state, onTap, menu)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.OperationView(app: MobaApp, service: TrackPlanService) {
    val plans by service.plans.collectAsState()
    val visible by service.visiblePlans.collectAsState()
    val op = rememberPlanOperation(service)
    val wide = LocalWideLayout.current
    // Gleisbilder in eigenen Fenstern (Windows, Pro) fehlen hier und tragen ein Fenster-Zeichen
    val separate by ShellUiState.separateWindows.collectAsState()
    val windowsAllowed = separate && service.maxVisiblePlans > 1
    val detached by TrackPlanWindows.open(app).collectAsState()

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        plans.forEach { plan ->
            val on = plan.id in visible
            val outside = windowsAllowed && plan.id in detached
            FilterChip(
                selected = on,
                onClick = {
                    try {
                        service.toggleVisible(plan.id)
                        say(null)
                    } catch (e: LicenseRequiredException) {
                        say(e.message)
                    }
                },
                label = { Text(plan.name) },
                leadingIcon = when {
                    outside -> {
                        { Icon(Icons.AutoMirrored.Filled.OpenInNew, "In eigenem Fenster", modifier = Modifier.size(16.dp)) }
                    }
                    on -> {
                        { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
                    }
                    else -> null
                },
            )
        }
        if (service.maxVisiblePlans > 1) {
            StatusPill(
                if (windowsAllowed) {
                    "Pro: bis zu ${service.maxVisiblePlans} Gleisbilder, auch in eigenen Fenstern"
                } else {
                    "Pro: bis zu ${service.maxVisiblePlans} Gleisbilder gleichzeitig"
                },
                Moba.colors.signalGreen,
            )
        } else if (plans.size > 1) {
            StatusPill(
                if (separate) "Mehrere Gleisbilder und eigene Fenster ab Pro" else "Mehrere Gleisbilder gleichzeitig ab Pro",
                Moba.colors.locked,
                Icons.Filled.Lock,
            )
        }
    }

    val shown = visible.filter { !windowsAllowed || it !in detached }.mapNotNull { id -> plans.firstOrNull { it.id == id } }
    if (shown.isEmpty() && visible.isNotEmpty()) {
        Text(
            "Alle eingeblendeten Gleisbilder stehen in eigenen Fenstern. „Ins Hauptfenster“ holt sie zurück.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val onDetach: ((TrackPlan) -> Unit)? = if (windowsAllowed) {
        { plan -> say(TrackPlanWindows.detach(app, plan.id)) }
    } else {
        null
    }
    val card = @Composable { plan: TrackPlan, modifier: Modifier, fill: Boolean ->
        PlanCard(plan, op.state, modifier, fill = fill, onTap = op.onTap, menu = op.menu, onDetach = onDetach)
    }
    if (wide) {
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (shown.size) {
                0 -> Unit
                1 -> card(shown[0], Modifier.weight(1f).fillMaxWidth(), true)
                2 -> shown.forEach { card(it, Modifier.weight(1f).fillMaxWidth(), true) }
                else -> {
                    card(shown[0], Modifier.weight(1f).fillMaxWidth(), true)
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        shown.drop(1).forEach { card(it, Modifier.weight(1f).fillMaxHeight(), true) }
                    }
                }
            }
        }
    } else {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            shown.forEach { card(it, Modifier.fillMaxWidth(), false) }
        }
    }
}

/** Ein Gleisbild im Betrieb, auf die verfuegbare Flaeche eingepasst. */
@Composable
internal fun PlanCard(
    plan: TrackPlan,
    state: TrackState,
    modifier: Modifier,
    fill: Boolean,
    onTap: (TrackPlan, Int, Int) -> Unit,
    menu: (TrackPlan) -> AspectOverlayData? = { null },
    /** Zeigt im Kopf "In eigenem Fenster"; `null` = geht hier nicht. */
    onDetach: ((TrackPlan) -> Unit)? = null,
    /** Feste Gleisgroesse, z. B. eingepasst ins eigene Fenster; sonst die eingestellte. */
    cellSize: Dp? = null,
    showTitle: Boolean = true,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(12.dp)) {
            if (showTitle) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AltRoute, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(plan.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (onDetach != null) {
                        TextButton(onClick = { onDetach(plan) }, modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Eigenes Fenster", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            val tileSize by TrackPlanUiState.tileSize.collectAsState()
            val cell = cellSize ?: tileSize.dp
            Box(
                (if (fill) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier).fillMaxWidth().padding(top = if (showTitle) 8.dp else 0.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    TrackPlanView(plan, cell, state, onTap = { x, y -> onTap(plan, x, y) })
                    menu(plan)?.let { m ->
                        Box(Modifier.offset(cell * m.tile.x, cell * m.tile.y).size(cell)) {
                            AspectMenu(m.tile, m.current, m.onPick, m.onDismiss)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Editor

@Composable
private fun ColumnScope.EditorView(service: TrackPlanService) {
    val plans by service.plans.collectAsState()
    val visible by service.visiblePlans.collectAsState()
    val editId by TrackPlanUiState.editPlan.collectAsState()
    val plan = plans.firstOrNull { it.id == editId } ?: plans.firstOrNull { it.id in visible } ?: plans.first()
    val wide = LocalWideLayout.current

    if (wide) {
        EditorToolbar(service, plans, plan)
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val paletteSize by TrackPlanUiState.paletteSize.collectAsState()
            Palette(Modifier.width((paletteSize + 176).dp).fillMaxHeight(), vertical = true)
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EditorGrid(service, plan, Modifier.weight(1f).fillMaxWidth())
                TileProperties(service, plan)
            }
        }
    } else {
        // Auf dem Handy scrollt die ganze Seite, damit das Raster genug Hoehe bekommt.
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorToolbar(service, plans, plan)
            Palette(Modifier.fillMaxWidth(), vertical = false)
            EditorGrid(service, plan, Modifier.height(420.dp).fillMaxWidth())
            TileProperties(service, plan)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorToolbar(service: TrackPlanService, plans: List<TrackPlan>, plan: TrackPlan) {
    val unsaved by service.unsaved.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        plans.forEach { p ->
            FilterChip(
                selected = p.id == plan.id,
                onClick = {
                    TrackPlanUiState.editPlan.value = p.id
                    TrackPlanUiState.selected.value = null
                },
                label = { Text(p.name) },
            )
        }
        OutlinedButton(onClick = {
            val names = service.plans.value.map { it.name }.toSet()
            val name = generateSequence(service.plans.value.size + 1) { it + 1 }.map { "Gleisbild $it" }.first { it !in names }
            val created = service.addPlan(name)
            TrackPlanUiState.editPlan.value = created.id
            TrackPlanUiState.selected.value = null
            say("„$name“ angelegt. Gleisform wählen und ins Raster tippen.")
        }) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Neues Gleisbild")
        }
        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Gleisbild löschen") }

        OutlinedTextField(
            value = plan.name,
            onValueChange = { name -> service.change(plan.id) { it.copy(name = name.take(40)) } },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.width(190.dp),
        )
        SizeFields(service, plan)

        Spacer(Modifier.width(8.dp))
        if (unsaved) StatusPill("Nicht gespeichert", Moba.colors.occupied)
        if (service.canSave) {
            Button(
                onClick = {
                    runCatching { service.save() }
                        .onSuccess { say("Gleisbilder gespeichert.") }
                        .onFailure { say("Speichern fehlgeschlagen: ${it.message}") }
                },
                enabled = unsaved,
            ) {
                Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Speichern")
            }
        } else {
            OutlinedButton(onClick = {
                say("Speichern gibt es ab der Basis-Lizenz (Burger-Menü, „Lizenz …“). Zeichnen und Ausprobieren bleiben kostenlos.")
            }) {
                Icon(Icons.Filled.Lock, null, tint = Moba.colors.locked, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Speichern ab Basis-Lizenz")
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Gleisbild löschen?") },
            text = { Text("„${plan.name}“ wird entfernt. Bis zum nächsten Speichern bleibt die gespeicherte Fassung erhalten.") },
            confirmButton = {
                TextButton(onClick = {
                    service.removePlan(plan.id)
                    TrackPlanUiState.editPlan.value = null
                    TrackPlanUiState.selected.value = null
                    confirmDelete = false
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun SizeFields(service: TrackPlanService, plan: TrackPlan) {
    var w by remember(plan.id, plan.width) { mutableStateOf(plan.width.toString()) }
    var h by remember(plan.id, plan.height) { mutableStateOf(plan.height.toString()) }
    val changed = w != plan.width.toString() || h != plan.height.toString()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NumberField("Breite", w, Modifier.width(84.dp)) { w = it }
        NumberField("Höhe", h, Modifier.width(84.dp)) { h = it }
        if (changed) {
            IconButton(onClick = {
                val nw = w.toIntOrNull() ?: plan.width
                val nh = h.toIntOrNull() ?: plan.height
                service.change(plan.id) { it.resize(nw, nh) }
                service.plans.value.firstOrNull { it.id == plan.id }?.let {
                    w = it.width.toString()
                    h = it.height.toString()
                }
            }) { Icon(Icons.Filled.Check, "Größe übernehmen") }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** Alle Gleisformen und Werkzeuge. Am PC als Leiste links, auf dem Handy als Zeile zum Wischen. */
@Composable
private fun Palette(modifier: Modifier, vertical: Boolean) {
    val tool by TrackPlanUiState.tool.collectAsState()
    val rotation by TrackPlanUiState.placeRotation.collectAsState()
    val size by TrackPlanUiState.paletteSize.collectAsState()
    val iconSize = (size * 2 / 3).coerceAtLeast(16)
    val tools = listOf(
        Triple(EditTool.Select, "Auswählen", Icons.Outlined.AdsClick),
        Triple(EditTool.Rotate, "Drehen", Icons.Outlined.Rotate90DegreesCw),
        Triple(EditTool.Erase, "Entfernen", Icons.Outlined.Delete),
    )
    val shapes = TrackShape.entries

    @Composable
    fun item(selected: Boolean, label: String, onClick: () -> Unit, preview: @Composable () -> Unit) {
        val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        if (vertical) {
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(bg).clickable(onClick = onClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) { preview() }
                Spacer(Modifier.width(10.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Column(
                Modifier.width(maxOf(76, size + 20).dp).clip(MaterialTheme.shapes.small).background(bg).clickable(onClick = onClick)
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) { preview() }
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    val content: @Composable () -> Unit = {
        tools.forEach { (t, label, icon) ->
            item(tool == t, label, { TrackPlanUiState.tool.value = t }) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(iconSize.dp))
            }
        }
        item(false, "Richtung", { TrackPlanUiState.placeRotation.value = (rotation + 1).mod(8) }) {
            Icon(Icons.Outlined.Rotate90DegreesCcw, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(iconSize.dp))
        }
        shapes.forEach { shape ->
            item(tool == EditTool.Place(shape), shape.label, { TrackPlanUiState.tool.value = EditTool.Place(shape) }) {
                ShapePreview(TrackTile(0, 0, shape, if (shape == TrackShape.TEXT) 0 else rotation, text = "Abc"), size.dp)
            }
        }
    }

    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
        if (vertical) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gleisformen", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    PaletteSizeControl()
                }
                content()
            }
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PaletteSizeControl()
                content()
            }
        }
    }
}

/** Das Raster zum Zeichnen, scroll- und zoombar. */
@Composable
private fun EditorGrid(service: TrackPlanService, plan: TrackPlan, modifier: Modifier) {
    val selected by TrackPlanUiState.selected.collectAsState()
    val tileSize by TrackPlanUiState.tileSize.collectAsState()

    val library = LocalSymbolLibrary.current
    val editorState = TrackState(symbolImage = { symbolImage(it, emptyMap(), emptyMap(), library) })

    val planId = plan.id
    // Liest Werkzeug, Auswahl und Gleisbild erst beim Antippen, damit jeder Klick auf dem neuesten Stand arbeitet.
    val tap = { x: Int, y: Int ->
        val current = service.plans.value.firstOrNull { it.id == planId }
        val tile = current?.tileAt(x, y)
        // Drehscheiben und Schiebebuehnen sind ueber ihre ganze Flaeche auswaehlbar
        val target = current?.objectAt(x, y)
        when (val t = TrackPlanUiState.tool.value) {
            EditTool.Select -> TrackPlanUiState.selected.value = target?.let { it.x to it.y }
            EditTool.Rotate -> if (target != null) {
                service.change(planId) { it.rotate(target.x, target.y) }
                TrackPlanUiState.selected.value = target.x to target.y
            }
            EditTool.Erase -> if (target != null) {
                service.change(planId) { it.remove(target.x, target.y) }
                if (TrackPlanUiState.selected.value == target.x to target.y) TrackPlanUiState.selected.value = null
            }
            is EditTool.Place -> {
                // Gleiche Form nochmal antippen dreht sie, andere Form ersetzt die Kachel.
                if (tile?.shape == t.shape) {
                    service.change(planId) { it.rotate(x, y) }
                } else {
                    val rot = if (t.shape == TrackShape.TEXT) 0 else TrackPlanUiState.placeRotation.value
                    val symbol = when (t.shape) {
                        TrackShape.SYMBOL -> library.firstOrNull()?.id.orEmpty()
                        TrackShape.SIGNAL -> TrackPlanUiState.lastSignal.value
                        else -> ""
                    }
                    val text = if (t.shape == TrackShape.TEXT) "Text" else ""
                    service.change(planId) { it.put(TrackTile(x, y, t.shape, rot, text = text, symbol = symbol)) }
                }
                TrackPlanUiState.selected.value = x to y
            }
        }
    }

    BoxWithConstraints(
        modifier.clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        // Platz fuer das Raster ohne Rand (12 dp je Seite)
        val areaWidth = (maxWidth - 24.dp).value
        val areaHeight = (maxHeight - 24.dp).value
        val fitCell = minOf(areaWidth / plan.width, areaHeight / plan.height).toInt().coerceIn(MIN_CELL, MAX_CELL)
        val cell = tileSize
        // Auf schmalen Bildschirmen nur Symbole, sonst passt die Leiste nicht
        val labels = maxWidth >= 560.dp
        Box(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            TrackPlanView(plan, cell.dp, editorState, Modifier.testTag(EDITOR_GRID_TAG), selected = selected, showGrid = true, onTap = tap)
        }
        Surface(
            Modifier.align(Alignment.BottomEnd).padding(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TileSizeControl()
                ToolButton("Einpassen", Icons.Filled.FitScreen, labels, enabled = fitCell != cell) {
                    TrackPlanUiState.tileSize.value = fitCell
                }
                // Raster so gross machen, dass es bei dieser Zoomstufe genau das Fenster fuellt
                val columns = (areaWidth / cell).toInt()
                val rows = (areaHeight / cell).toInt()
                val fitted = plan.fitTo(columns, rows)
                ToolButton(
                    "Raster ans Fenster",
                    Icons.Filled.GridOn,
                    labels,
                    enabled = fitted.width != plan.width || fitted.height != plan.height,
                ) {
                    service.change(plan.id) { it.fitTo(columns, rows) }
                    say("Raster jetzt ${fitted.width} × ${fitted.height} Felder.")
                }
            }
        }
    }
}

@Composable
private fun ToolButton(label: String, icon: ImageVector, showLabel: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (showLabel) {
        TextButton(enabled = enabled, onClick = onClick) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(label)
        }
    } else {
        IconButton(enabled = enabled, onClick = onClick) { Icon(icon, label) }
    }
}

/** Kacheln, bei denen die Drehung nichts aendert. */
private val UNROTATED = setOf(TileKind.TEXT, TileKind.SYMBOL, TileKind.RELAY, TileKind.LIGHT, TileKind.SOUND, TileKind.ASPECT, TileKind.TURNTABLE)

/** Kennung des Editor-Rasters fuer Oberflaechentests. */
const val EDITOR_GRID_TAG = "gleisbild-raster"

/** Kleinste und groesste Gleisgroesse in dp; anfangs 24 dp. */
internal const val MIN_CELL = 8
internal const val MAX_CELL = 80
internal const val DEFAULT_TILE = 24
private const val TILE_STEP = 2
private const val PREF_TILE_SIZE = "gleisbild.gleisgroesse"

internal const val MIN_PALETTE = 20
internal const val MAX_PALETTE = 64
internal const val DEFAULT_PALETTE = 30
private const val PREF_PALETTE_SIZE = "gleisbild.symbolgroesse"

/** Symbole in der Leiste "Gleisformen" kleiner/groesser. */
@Composable
private fun PaletteSizeControl() {
    val size by TrackPlanUiState.paletteSize.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            enabled = size > MIN_PALETTE,
            onClick = { TrackPlanUiState.paletteSize.value = (size - 4).coerceAtLeast(MIN_PALETTE) },
            modifier = Modifier.size(32.dp),
        ) { Icon(Icons.Filled.ZoomOut, "Symbole kleiner", modifier = Modifier.size(18.dp)) }
        IconButton(
            enabled = size < MAX_PALETTE,
            onClick = { TrackPlanUiState.paletteSize.value = (size + 4).coerceAtMost(MAX_PALETTE) },
            modifier = Modifier.size(32.dp),
        ) { Icon(Icons.Filled.ZoomIn, "Symbole größer", modifier = Modifier.size(18.dp)) }
    }
}

/** Gleisgroesse kleiner/groesser, gilt fuer Betrieb und Editor. */
@Composable
private fun TileSizeControl() {
    val size by TrackPlanUiState.tileSize.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(enabled = size > MIN_CELL, onClick = { TrackPlanUiState.tileSize.value = (size - TILE_STEP).coerceAtLeast(MIN_CELL) }) {
            Icon(Icons.Filled.ZoomOut, "Gleise kleiner")
        }
        Text("Gleisgröße $size", style = MaterialTheme.typography.labelMedium, maxLines = 1)
        IconButton(enabled = size < MAX_CELL, onClick = { TrackPlanUiState.tileSize.value = (size + TILE_STEP).coerceAtMost(MAX_CELL) }) {
            Icon(Icons.Filled.ZoomIn, "Gleise größer")
        }
    }
}

/** Eigenschaften der ausgewaehlten Kachel: Adresse, s88-Kontakt, Text, Drehung. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TileProperties(service: TrackPlanService, plan: TrackPlan) {
    val selected by TrackPlanUiState.selected.collectAsState()
    val tile = selected?.let { (x, y) -> plan.tileAt(x, y) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            if (tile == null) {
                Text(
                    "Gleisform wählen und ins Raster tippen. Dieselbe Form erneut antippen dreht sie um 45°. " +
                        "Mit „Auswählen“ eine Kachel antippen, um Adresse oder Rückmelder einzutragen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }
            val set = { t: TrackTile -> service.change(plan.id) { it.put(t) } }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val library = LocalSymbolLibrary.current
                ShapePreview(tile, 32.dp, TrackState(symbolImage = { symbolImage(it, emptyMap(), emptyMap(), library) }))
                Column {
                    Text(tile.shape.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Spalte ${tile.x + 1}, Zeile ${tile.y + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (tile.shape.kind !in UNROTATED) {
                    IconButton(onClick = { set(tile.copy(rotation = (tile.rotation - 1).mod(8))) }) {
                        Icon(Icons.Outlined.Rotate90DegreesCcw, "Links drehen")
                    }
                    IconButton(onClick = { set(tile.copy(rotation = (tile.rotation + 1).mod(8))) }) {
                        Icon(Icons.Outlined.Rotate90DegreesCw, "Rechts drehen")
                    }
                }
                if (tile.shape.kind == TileKind.SIGNAL || tile.shape.kind == TileKind.SYMBOL) {
                    SymbolChooser(tile) {
                        if (tile.shape.kind == TileKind.SIGNAL) TrackPlanUiState.lastSignal.value = it
                        set(tile.copy(symbol = it))
                    }
                }
                if (tile.shape.hasAddress && !hasAccessoryTabs(tile)) {
                    val label = when {
                        tile.shape.kind == TileKind.SYMBOL -> "Schaltadresse (optional)"
                        tile.shape.addressCount == 2 -> "Adresse (links, rechts = +1)"
                        tile.shape.kind == TileKind.SIGNAL -> "Signaladresse"
                        tile.shape.kind == TileKind.UNCOUPLER -> "Entkuppler-Adresse"
                        else -> "Weichenadresse"
                    }
                    NumberField(label, tile.address?.toString().orEmpty(), Modifier.width(220.dp)) {
                        set(tile.copy(address = it.toIntOrNull()))
                    }
                }
                if (tile.shape.kind == TileKind.FEEDBACK) {
                    NumberField("s88-Modul", tile.feedbackModule?.toString().orEmpty(), Modifier.width(120.dp)) {
                        set(tile.copy(feedbackModule = it.toIntOrNull()))
                    }
                    NumberField("Kontakt (1–16)", tile.feedbackContact?.toString().orEmpty(), Modifier.width(140.dp)) {
                        set(tile.copy(feedbackContact = it.toIntOrNull()))
                    }
                }
                if (tile.shape == TrackShape.TEXT) {
                    OutlinedTextField(
                        value = tile.text,
                        onValueChange = { set(tile.copy(text = it.take(60))) },
                        label = { Text("Text") },
                        singleLine = true,
                        modifier = Modifier.width(240.dp),
                    )
                }
                FilledTonalButton(onClick = {
                    service.change(plan.id) { it.remove(tile.x, tile.y) }
                    TrackPlanUiState.selected.value = null
                }) {
                    Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Entfernen")
                }
            }
            if (tile.shape.kind == TileKind.SIGNAL) {
                Spacer(Modifier.size(8.dp))
                AspectAddresses(tile)
            }
            if (hasAccessoryTabs(tile)) {
                Spacer(Modifier.size(8.dp))
                AccessoryTabs(tile, set)
            }
        }
    }
}

/** Offenes Signalbild-Menue an einer Signalkachel im Betrieb. */
internal class AspectOverlayData(val tile: TrackTile, val current: Int?, val onPick: (Int) -> Unit, val onDismiss: () -> Unit)

private fun AspectOverlay(tile: TrackTile, current: Int?, onPick: (Int) -> Unit, onDismiss: () -> Unit) =
    AspectOverlayData(tile, current, onPick, onDismiss)
