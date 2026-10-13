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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.moba.modules.trackplan.api.LicenseRequiredException
import de.moba.modules.trackplan.api.TRACKPLAN_MODULE_ID
import de.moba.modules.trackplan.api.TileKind
import de.moba.modules.trackplan.api.TrackPlan
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.api.TrackShape
import de.moba.modules.trackplan.api.TrackTile
import de.moba.ui.shell.LocalWideLayout
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.Moba
import de.moba.ui.shell.ModuleScreen
import de.moba.ui.shell.StatusPill
import kotlinx.coroutines.flow.MutableStateFlow
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
    /** Kachelgroesse im Editor in dp; `null` = so gross, dass das ganze Raster ins Fenster passt. */
    val cellSize = MutableStateFlow<Int?>(null)
    val message = MutableStateFlow<String?>(null)
}

/** Reiter "Gleisbild": Betrieb (Weichen schalten, Belegung sehen) und Editor. */
object TrackPlanScreen : ModuleScreen {
    override val moduleId = TRACKPLAN_MODULE_ID
    override val title = "Gleisbild"
    override val icon = Icons.Outlined.AltRoute

    @Composable
    override fun Content(app: MobaApp) {
        val session by app.session.collectAsState()
        val service = session?.host?.service(TrackPlanService::class) ?: return
        val mode by TrackPlanUiState.mode.collectAsState()
        val message by TrackPlanUiState.message.collectAsState()
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("Betrieb", Icons.Filled.PlayArrow, mode == TrackPlanMode.BETRIEB) {
                    TrackPlanUiState.mode.value = TrackPlanMode.BETRIEB
                    TrackPlanUiState.message.value = null
                }
                ModeChip("Bearbeiten", Icons.Filled.Edit, mode == TrackPlanMode.BEARBEITEN) {
                    TrackPlanUiState.mode.value = TrackPlanMode.BEARBEITEN
                    TrackPlanUiState.message.value = null
                }
                Spacer(Modifier.width(8.dp))
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
                TrackPlanMode.BETRIEB -> OperationView(service)
                TrackPlanMode.BEARBEITEN -> EditorView(service)
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

private fun say(text: String?) {
    TrackPlanUiState.message.value = text
}

// ---------------------------------------------------------------- Betrieb

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.OperationView(service: TrackPlanService) {
    val plans by service.plans.collectAsState()
    val visible by service.visiblePlans.collectAsState()
    val turnouts by service.turnouts.collectAsState()
    val occupied by service.occupied.collectAsState()
    val scope = rememberCoroutineScope()
    val state = TrackState(turnouts, occupied, operating = true)
    val wide = LocalWideLayout.current

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        plans.forEach { plan ->
            val on = plan.id in visible
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
                leadingIcon = if (on) {
                    { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
                } else {
                    null
                },
            )
        }
        if (service.maxVisiblePlans > 1) {
            StatusPill("Pro: bis zu ${service.maxVisiblePlans} Gleisbilder gleichzeitig", Moba.colors.signalGreen)
        } else if (plans.size > 1) {
            StatusPill("Mehrere Gleisbilder gleichzeitig ab Pro", Moba.colors.locked, Icons.Filled.Lock)
        }
    }

    val shown = visible.mapNotNull { id -> plans.firstOrNull { it.id == id } }
    val onTap = { plan: TrackPlan, x: Int, y: Int ->
        plan.tileAt(x, y)?.takeIf { it.shape.hasAddress && it.address != null }?.let { tile ->
            scope.launch {
                runCatching { service.operate(tile) }.onFailure { say("Schalten nicht möglich: ${it.message}") }
            }
        }
        Unit
    }
    if (wide) {
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (shown.size) {
                0 -> Unit
                1 -> PlanCard(shown[0], state, Modifier.weight(1f).fillMaxWidth(), fill = true, onTap = onTap)
                2 -> shown.forEach { PlanCard(it, state, Modifier.weight(1f).fillMaxWidth(), fill = true, onTap = onTap) }
                else -> {
                    PlanCard(shown[0], state, Modifier.weight(1f).fillMaxWidth(), fill = true, onTap = onTap)
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        shown.drop(1).forEach {
                            PlanCard(it, state, Modifier.weight(1f).fillMaxHeight(), fill = true, onTap = onTap)
                        }
                    }
                }
            }
        }
    } else {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            shown.forEach { PlanCard(it, state, Modifier.fillMaxWidth(), fill = false, onTap = onTap) }
        }
    }
}

/** Ein Gleisbild im Betrieb, auf die verfuegbare Flaeche eingepasst. */
@Composable
private fun PlanCard(
    plan: TrackPlan,
    state: TrackState,
    modifier: Modifier,
    fill: Boolean,
    onTap: (TrackPlan, Int, Int) -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AltRoute, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(plan.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            BoxWithConstraints(
                (if (fill) Modifier.weight(1f) else Modifier).fillMaxWidth().padding(top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val byWidth = maxWidth / plan.width
                val cell = (if (fill) minOf(byWidth, maxHeight / plan.height) else byWidth).coerceIn(10.dp, 44.dp)
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    TrackPlanView(plan, cell, state, onTap = { x, y -> onTap(plan, x, y) })
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
            Palette(Modifier.width(200.dp).fillMaxHeight(), vertical = true)
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
        IconButton(onClick = {
            val created = service.addPlan("Gleisbild ${plans.size + 1}")
            TrackPlanUiState.editPlan.value = created.id
            TrackPlanUiState.selected.value = null
        }) { Icon(Icons.Filled.Add, "Neues Gleisbild") }
        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Gleisbild löschen") }

        OutlinedTextField(
            value = plan.name,
            onValueChange = { service.update(plan.copy(name = it.take(40))) },
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
                val resized = plan.resize(nw, nh)
                service.update(resized)
                w = resized.width.toString()
                h = resized.height.toString()
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
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { preview() }
                Spacer(Modifier.width(10.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Column(
                Modifier.width(76.dp).clip(MaterialTheme.shapes.small).background(bg).clickable(onClick = onClick)
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { preview() }
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    val content: @Composable () -> Unit = {
        tools.forEach { (t, label, icon) ->
            item(tool == t, label, { TrackPlanUiState.tool.value = t }) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        item(false, "Richtung", { TrackPlanUiState.placeRotation.value = (rotation + 1).mod(8) }) {
            Icon(Icons.Outlined.Rotate90DegreesCcw, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        shapes.forEach { shape ->
            item(tool == EditTool.Place(shape), shape.label, { TrackPlanUiState.tool.value = EditTool.Place(shape) }) {
                ShapePreview(TrackTile(0, 0, shape, if (shape == TrackShape.TEXT) 0 else rotation, text = "Abc"), 30.dp)
            }
        }
    }

    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
        if (vertical) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Gleisformen", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(4.dp))
                content()
            }
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) { content() }
        }
    }
}

/** Das Raster zum Zeichnen, scroll- und zoombar. */
@Composable
private fun EditorGrid(service: TrackPlanService, plan: TrackPlan, modifier: Modifier) {
    val tool by TrackPlanUiState.tool.collectAsState()
    val rotation by TrackPlanUiState.placeRotation.collectAsState()
    val selected by TrackPlanUiState.selected.collectAsState()
    val cellSize by TrackPlanUiState.cellSize.collectAsState()

    fun tap(x: Int, y: Int) {
        val tile = plan.tileAt(x, y)
        when (val t = tool) {
            EditTool.Select -> TrackPlanUiState.selected.value = if (tile != null) x to y else null
            EditTool.Rotate -> if (tile != null) {
                service.update(plan.rotate(x, y))
                TrackPlanUiState.selected.value = x to y
            }
            EditTool.Erase -> {
                service.update(plan.remove(x, y))
                if (selected == x to y) TrackPlanUiState.selected.value = null
            }
            is EditTool.Place -> {
                // Gleiche Form nochmal antippen dreht sie, andere Form ersetzt die Kachel.
                if (tile?.shape == t.shape) {
                    service.update(plan.rotate(x, y))
                } else {
                    val rot = if (t.shape == TrackShape.TEXT) 0 else rotation
                    service.update(plan.put(TrackTile(x, y, t.shape, rot, text = if (t.shape == TrackShape.TEXT) "Text" else "")))
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
        val cell = cellSize ?: fitCell
        // Auf schmalen Bildschirmen nur Symbole, sonst passt die Leiste nicht
        val labels = maxWidth >= 560.dp
        Box(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            TrackPlanView(plan, cell.dp, TrackState(), selected = selected, showGrid = true, onTap = ::tap)
        }
        Surface(
            Modifier.align(Alignment.BottomEnd).padding(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { TrackPlanUiState.cellSize.value = (cell - 4).coerceAtLeast(MIN_CELL) }) {
                    Icon(Icons.Filled.ZoomOut, "Verkleinern")
                }
                Text("${cell * 100 / 32} %", style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { TrackPlanUiState.cellSize.value = (cell + 4).coerceAtMost(MAX_CELL) }) {
                    Icon(Icons.Filled.ZoomIn, "Vergrößern")
                }
                ToolButton("Einpassen", Icons.Filled.FitScreen, labels, enabled = cellSize != null) {
                    TrackPlanUiState.cellSize.value = null
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
                    TrackPlanUiState.cellSize.value = cell
                    service.update(fitted)
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

/** Kleinste und groesste Kachel im Editor in dp. */
private const val MIN_CELL = 8
private const val MAX_CELL = 80

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
            fun set(t: TrackTile) = service.update(plan.put(t))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShapePreview(tile, 32.dp)
                Column {
                    Text(tile.shape.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Spalte ${tile.x + 1}, Zeile ${tile.y + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (tile.shape != TrackShape.TEXT) {
                    IconButton(onClick = { set(tile.copy(rotation = (tile.rotation - 1).mod(8))) }) {
                        Icon(Icons.Outlined.Rotate90DegreesCcw, "Links drehen")
                    }
                    IconButton(onClick = { set(tile.copy(rotation = (tile.rotation + 1).mod(8))) }) {
                        Icon(Icons.Outlined.Rotate90DegreesCw, "Rechts drehen")
                    }
                }
                if (tile.shape.hasAddress) {
                    val label = when {
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
                    service.update(plan.remove(tile.x, tile.y))
                    TrackPlanUiState.selected.value = null
                }) {
                    Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Entfernen")
                }
            }
        }
    }
}
