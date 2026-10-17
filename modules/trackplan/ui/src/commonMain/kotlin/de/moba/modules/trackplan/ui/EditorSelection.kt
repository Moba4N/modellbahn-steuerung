package de.moba.modules.trackplan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.moba.modules.trackplan.api.AccessoryProps
import de.moba.modules.trackplan.api.TileKind
import de.moba.modules.trackplan.api.TrackPlan
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.api.TrackTile
import de.moba.modules.trackplan.api.block
import de.moba.modules.trackplan.api.displayName

/**
 * Bearbeiten wie in iTrain: markierte Kacheln (Bereich oder die ausgewaehlte Kachel) verschieben, drehen,
 * kopieren, ausschneiden, loeschen und an anderer Stelle oder in einem anderen Gleisbild einfuegen.
 */
internal object EditorActions {
    /** Worauf sich die Befehle beziehen: der markierte Bereich, sonst die ausgewaehlte Kachel. */
    fun targets(plan: TrackPlan): Set<Pair<Int, Int>> {
        val area = TrackPlanUiState.area.value.filter { (x, y) -> plan.tileAt(x, y) != null }.toSet()
        if (area.isNotEmpty()) return area
        val sel = TrackPlanUiState.selected.value ?: return emptySet()
        return plan.objectAt(sel.first, sel.second)?.let { setOf(it.x to it.y) } ?: emptySet()
    }

    fun clear() {
        TrackPlanUiState.area.value = emptySet()
        TrackPlanUiState.selected.value = null
    }

    fun move(service: TrackPlanService, plan: TrackPlan, cells: Set<Pair<Int, Int>>, dx: Int, dy: Int) {
        if (cells.isEmpty() || dx == 0 && dy == 0) return
        val moved = plan.moveTiles(cells, dx, dy)
        if (moved == null) {
            say("Verschieben nicht möglich: Die Gleise lägen außerhalb des Rasters.")
            return
        }
        service.update(moved)
        val shifted = cells.map { (x, y) -> x + dx to y + dy }.toSet()
        if (TrackPlanUiState.area.value.isNotEmpty() || cells.size > 1) TrackPlanUiState.area.value = shifted
        TrackPlanUiState.selected.value = TrackPlanUiState.selected.value?.let { (x, y) -> if ((x to y) in cells) x + dx to y + dy else null }
        say(null)
    }

    fun copy(plan: TrackPlan, cells: Set<Pair<Int, Int>>): Boolean {
        val tiles = plan.tiles.filter { (it.x to it.y) in cells }
        if (tiles.isEmpty()) return false
        val minX = tiles.minOf { it.x }
        val minY = tiles.minOf { it.y }
        TrackPlanUiState.clipboard.value = tiles.map { it.copy(x = it.x - minX, y = it.y - minY) }
        say("${count(tiles.size)} kopiert. „Einfügen“ wählen und ins Raster tippen, auch in einem anderen Gleisbild.")
        return true
    }

    fun delete(service: TrackPlanService, plan: TrackPlan, cells: Set<Pair<Int, Int>>) {
        if (cells.isEmpty()) return
        service.update(plan.removeAll(cells))
        clear()
        say("${count(cells.size)} gelöscht.")
    }

    fun cut(service: TrackPlanService, plan: TrackPlan, cells: Set<Pair<Int, Int>>) {
        if (copy(plan, cells)) {
            service.update(plan.removeAll(cells))
            clear()
            TrackPlanUiState.tool.value = EditTool.Paste
            say("${count(cells.size)} ausgeschnitten. Ins Raster tippen, wo sie hin sollen.")
        }
    }

    /** Fuegt die Zwischenablage mit der linken oberen Ecke auf ([x], [y]) ein und markiert das Eingefuegte. */
    fun paste(service: TrackPlanService, plan: TrackPlan, x: Int, y: Int) {
        val clip = TrackPlanUiState.clipboard.value
        if (clip.isEmpty()) return
        service.update(plan.paste(clip, x, y))
        val placed = clip.map { it.x + x to it.y + y }.filter { (px, py) -> px in 0 until plan.width && py in 0 until plan.height }.toSet()
        TrackPlanUiState.area.value = placed
        TrackPlanUiState.selected.value = placed.singleOrNull()
        say(if (placed.size < clip.size) "${count(placed.size)} eingefügt, ${clip.size - placed.size} lagen außerhalb des Rasters." else "${count(placed.size)} eingefügt.")
    }

    fun rotate(service: TrackPlanService, plan: TrackPlan, cells: Set<Pair<Int, Int>>, steps: Int) {
        if (cells.isEmpty()) return
        service.update(cells.fold(plan) { p, (x, y) -> p.rotate(x, y, steps) })
    }

    /** Tastatur im Editor: Entf loescht, Pfeile verschieben, Strg+C/X/V, R dreht, Esc hebt die Auswahl auf. */
    fun onKey(service: TrackPlanService, planId: String, event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val plan = service.plans.value.firstOrNull { it.id == planId } ?: return false
        val cells = targets(plan)
        val ctrl = event.isCtrlPressed || event.isMetaPressed
        when {
            event.key == Key.Delete || event.key == Key.Backspace -> delete(service, plan, cells)
            event.key == Key.DirectionLeft -> move(service, plan, cells, -1, 0)
            event.key == Key.DirectionRight -> move(service, plan, cells, 1, 0)
            event.key == Key.DirectionUp -> move(service, plan, cells, 0, -1)
            event.key == Key.DirectionDown -> move(service, plan, cells, 0, 1)
            ctrl && event.key == Key.C -> copy(plan, cells)
            ctrl && event.key == Key.X -> cut(service, plan, cells)
            ctrl && event.key == Key.V -> if (TrackPlanUiState.clipboard.value.isNotEmpty()) {
                TrackPlanUiState.tool.value = EditTool.Paste
                say("Ins Raster tippen, wo eingefügt werden soll.")
            }
            ctrl && event.key == Key.A -> {
                TrackPlanUiState.area.value = plan.tiles.map { it.x to it.y }.toSet()
                TrackPlanUiState.selected.value = null
            }
            !ctrl && event.key == Key.R -> rotate(service, plan, cells, 1)
            event.key == Key.Escape -> {
                clear()
                if (TrackPlanUiState.tool.value == EditTool.Paste) TrackPlanUiState.tool.value = EditTool.Select
            }
            else -> return false
        }
        return true
    }

    fun count(n: Int) = if (n == 1) "1 Kachel" else "$n Kacheln"
}

/** Leiste unter dem Raster, solange etwas markiert ist oder in der Zwischenablage liegt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SelectionBar(service: TrackPlanService, plan: TrackPlan) {
    val area by TrackPlanUiState.area.collectAsState()
    val selected by TrackPlanUiState.selected.collectAsState()
    val clipboard by TrackPlanUiState.clipboard.collectAsState()
    // Neu berechnen, wenn sich Auswahl oder Gleisbild aendern
    val cells = remember(area, selected, plan) { EditorActions.targets(plan) }
    if (cells.isEmpty() && clipboard.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (cells.isNotEmpty()) {
                Text(
                    "${EditorActions.count(cells.size)} markiert",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterVertically).padding(horizontal = 6.dp),
                )
                MoveButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Nach links", cells, plan, service, -1, 0)
                MoveButton(Icons.Filled.KeyboardArrowUp, "Nach oben", cells, plan, service, 0, -1)
                MoveButton(Icons.Filled.KeyboardArrowDown, "Nach unten", cells, plan, service, 0, 1)
                MoveButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nach rechts", cells, plan, service, 1, 0)
                BarButton("Drehen", Icons.Outlined.Rotate90DegreesCw) { EditorActions.rotate(service, plan, cells, 1) }
                BarButton("Kopieren", Icons.Filled.ContentCopy) { EditorActions.copy(plan, cells) }
                BarButton("Ausschneiden", Icons.Filled.ContentCut) { EditorActions.cut(service, plan, cells) }
                BarButton("Löschen", Icons.Outlined.Delete) { EditorActions.delete(service, plan, cells) }
            }
            if (clipboard.isNotEmpty()) {
                BarButton("Einfügen (${clipboard.size})", Icons.Filled.ContentPaste) {
                    TrackPlanUiState.tool.value = EditTool.Paste
                    say("Ins Raster tippen, wo eingefügt werden soll.")
                }
            }
            if (cells.isNotEmpty()) {
                IconButton(onClick = { EditorActions.clear() }) { Icon(Icons.Filled.Close, "Auswahl aufheben") }
            }
        }
    }
}

@Composable
private fun MoveButton(icon: ImageVector, label: String, cells: Set<Pair<Int, Int>>, plan: TrackPlan, service: TrackPlanService, dx: Int, dy: Int) {
    IconButton(onClick = { EditorActions.move(service, plan, cells, dx, dy) }) { Icon(icon, label) }
}

@Composable
private fun BarButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label)
    }
}

/** Rechtsklick bzw. langes Druecken im Editor auf die Zelle [cell]. */
@Composable
internal fun EditorContextMenu(service: TrackPlanService, plan: TrackPlan, cell: Pair<Int, Int>, onDismiss: () -> Unit) {
    val clipboard by TrackPlanUiState.clipboard.collectAsState()
    val tile = plan.objectAt(cell.first, cell.second)
    val area = TrackPlanUiState.area.value
    val cells = when {
        tile != null && (tile.x to tile.y) in area -> area.filter { (x, y) -> plan.tileAt(x, y) != null }.toSet()
        tile != null -> setOf(tile.x to tile.y)
        else -> emptySet()
    }
    fun act(f: () -> Unit) {
        onDismiss()
        f()
    }

    @Composable
    fun item(text: String, icon: ImageVector, onClick: () -> Unit) =
        DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null, Modifier.size(20.dp)) }, onClick = { act(onClick) })

    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        Text(
            when {
                cells.size > 1 -> "${EditorActions.count(cells.size)} markiert"
                tile != null -> tile.displayName()
                else -> "Spalte ${cell.first + 1}, Zeile ${cell.second + 1}"
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        if (tile != null) {
            if (cells.size == 1) {
                item("Eigenschaften", Icons.Outlined.Tune) {
                    TrackPlanUiState.area.value = emptySet()
                    TrackPlanUiState.selected.value = tile.x to tile.y
                }
            }
            item("Drehen", Icons.Outlined.Rotate90DegreesCw) { EditorActions.rotate(service, plan, cells, 1) }
            item("Kopieren", Icons.Filled.ContentCopy) { EditorActions.copy(plan, cells) }
            item("Ausschneiden", Icons.Filled.ContentCut) { EditorActions.cut(service, plan, cells) }
            item("Löschen", Icons.Outlined.Delete) { EditorActions.delete(service, plan, cells) }
        }
        if (clipboard.isNotEmpty()) {
            if (tile != null) HorizontalDivider()
            item("Hier einfügen (${EditorActions.count(clipboard.size)})", Icons.Filled.ContentPaste) {
                EditorActions.paste(service, plan, cell.first, cell.second)
            }
        }
    }
}

// ---------------------------------------------------------------- Steuerobjekte wiederverwenden

/** Kennung des Steuerobjekts einer Kachel: Block, Adresse oder Messstrecke; `null` = noch nichts eingetragen. */
internal fun TrackTile.objectKey(): String? {
    block()?.let { (m, c) -> return "block:$m.$c" }
    address?.let { return "adresse:${shape.kind}:$it" }
    if (shape.kind == TileKind.MEASURE) {
        val start = prop(AccessoryProps.MEASURE_START)
        val end = prop(AccessoryProps.MEASURE_END)
        if (start.isNotBlank() || end.isNotBlank()) return "messung:$start-$end"
    }
    return null
}

/** Uebernimmt Adresse, Melder, Bild und Einstellungen von [source]; Lage, Drehung und Form bleiben. */
internal fun TrackTile.reuse(source: TrackTile): TrackTile = copy(
    address = source.address,
    feedbackModule = source.feedbackModule,
    feedbackContact = source.feedbackContact,
    symbol = if (shape.kind == source.shape.kind) source.symbol else symbol,
    props = source.props,
)

/** Kacheln eines Steuerobjekts ausser [self], ueber alle Gleisbilder. */
internal fun occurrences(plans: List<TrackPlan>, key: String, selfPlan: String, self: TrackTile): List<Pair<TrackPlan, TrackTile>> =
    plans.flatMap { p -> p.tiles.filter { it.objectKey() == key && !(p.id == selfPlan && it.x == self.x && it.y == self.y) }.map { p to it } }

/**
 * Wie in iTrain dasselbe Steuerobjekt (Weiche, Signal, Block ...) an mehreren Stellen und in mehreren Gleisbildern:
 * ein vorhandenes Objekt uebernehmen, sehen, wo es noch liegt, und Aenderungen auf alle Stellen uebertragen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReuseObject(service: TrackPlanService, plan: TrackPlan, tile: TrackTile, set: (TrackTile) -> Unit) {
    val plans by service.plans.collectAsState()
    var open by remember { mutableStateOf(false) }
    // Vorhandene Objekte derselben Art, je Objekt einmal
    val candidates = plans.flatMap { p -> p.tiles.filter { it.shape.kind == tile.shape.kind }.map { p to it } }
        .filter { (p, t) -> t.objectKey() != null && !(p.id == plan.id && t.x == tile.x && t.y == tile.y) }
        .distinctBy { it.second.objectKey() }
        .filter { it.second.objectKey() != tile.objectKey() }
    val key = tile.objectKey()
    val others = key?.let { occurrences(plans, it, plan.id, tile) }.orEmpty()
    if (candidates.isEmpty() && others.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (candidates.isNotEmpty()) {
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { open = true }) { Text("Vorhandenes Objekt übernehmen …") }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        candidates.forEach { (p, t) ->
                            DropdownMenuItem(
                                text = { Text("${objectLabel(plans, t)} · ${p.name}") },
                                onClick = {
                                    open = false
                                    set(tile.reuse(t))
                                    say("Einstellungen von „${objectLabel(plans, t)}“ übernommen.")
                                },
                            )
                        }
                    }
                }
            }
            if (others.isNotEmpty()) {
                TextButton(onClick = {
                    others.forEach { (p, t) -> service.updateTile(p.id, t.reuse(tile)) }
                    say("Einstellungen auf ${EditorActions.count(others.size)} übertragen.")
                }) { Text("Auf alle Stellen übertragen") }
            }
        }
        if (others.isNotEmpty()) {
            Text(
                "Dasselbe Objekt auch in: " + others.joinToString(", ") { (p, t) -> "${p.name} (Spalte ${t.x + 1}, Zeile ${t.y + 1})" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun objectLabel(plans: List<TrackPlan>, t: TrackTile): String {
    val name = t.prop(AccessoryProps.NAME)
    return when {
        name.isNotBlank() -> name
        t.block() != null -> plans.firstNotNullOfOrNull { p -> p.tiles.firstOrNull { it.block() == t.block() && it.prop(AccessoryProps.NAME).isNotBlank() } }
            ?.prop(AccessoryProps.NAME) ?: "Block ${t.block()!!.first}.${t.block()!!.second}"
        t.address != null -> "${t.shape.label} ${t.address}"
        else -> t.shape.label
    }
}

private fun TrackPlanService.updateTile(planId: String, tile: TrackTile) {
    val current = plans.value.firstOrNull { it.id == planId } ?: return
    update(current.put(tile))
}
