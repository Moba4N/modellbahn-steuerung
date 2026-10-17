package de.moba.modules.trackplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.moba.modules.trackplan.api.Branch
import de.moba.modules.trackplan.api.FeedbackRef
import de.moba.modules.trackplan.api.TileKind
import de.moba.modules.trackplan.api.TrackPlan
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.api.TrackShape
import de.moba.modules.trackplan.api.TrackTile
import de.moba.modules.trackplan.api.activeBranch
import de.moba.modules.trackplan.api.aspectNames
import de.moba.modules.trackplan.api.block
import de.moba.modules.trackplan.api.blockName
import de.moba.modules.trackplan.api.displayName
import de.moba.modules.trackplan.api.isOn
import de.moba.ui.shell.MobaApp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Ein gezogener Zug: Lokadresse, Beschriftung und der Block, aus dem er kommt (`null` = von der Zugleiste). */
internal class TrainDragItem(val address: Int, val label: String, val from: FeedbackRef?)

/**
 * Ziehen von Zuegen innerhalb eines Fensters: Gleisbilder melden ihre Lage an, die Zugleiste und die Gleisbilder
 * starten das Ziehen, beim Loslassen landet der Zug im Block unter dem Zeiger. Alle Positionen in Fensterkoordinaten.
 */
internal class DragLayer(private val service: TrackPlanService) {
    private class Target(val origin: Offset, val cellPx: Float, val plan: () -> TrackPlan)

    private val targets = mutableMapOf<String, Target>()
    var item by mutableStateOf<TrainDragItem?>(null)
        private set
    var position by mutableStateOf(Offset.Zero)
        private set

    fun register(planId: String, origin: Offset, cellPx: Float, plan: () -> TrackPlan) {
        targets[planId] = Target(origin, cellPx, plan)
    }

    fun originOf(planId: String): Offset = targets[planId]?.origin ?: Offset.Zero

    fun addressIn(block: FeedbackRef): Int? = service.trains.value[block]

    fun start(item: TrainDragItem, at: Offset) {
        this.item = item
        position = at
    }

    fun moveTo(at: Offset) {
        position = at
    }

    fun moveBy(delta: Offset) {
        position += delta
    }

    fun cancel() {
        item = null
    }

    /** Block unter dem Zeiger, falls er auf einem Rueckmelder eines angezeigten Gleisbilds steht. */
    fun blockAt(at: Offset): FeedbackRef? = targets.values.firstNotNullOfOrNull { t ->
        val plan = t.plan()
        val local = at - t.origin
        if (local.x < 0 || local.y < 0) return@firstNotNullOfOrNull null
        val x = (local.x / t.cellPx).toInt()
        val y = (local.y / t.cellPx).toInt()
        if (x >= plan.width || y >= plan.height) null else plan.objectAt(x, y)?.block()
    }

    fun drop() {
        val dragged = item ?: return
        item = null
        val target = blockAt(position)
        if (target == null) {
            say(if (dragged.from == null) "Lok auf einen Block (Rückmelder) im Gleisbild ziehen." else null)
            return
        }
        runCatching {
            if (dragged.from == null) service.placeTrain(target, dragged.address) else service.moveTrain(dragged.from, target)
        }.onSuccess { say(null) }.onFailure { say(it.message) }
    }
}

internal val LocalDragLayer = compositionLocalOf<DragLayer?> { null }

/** Flaeche, in der Zuege gezogen werden koennen; zeigt den gezogenen Zug am Zeiger. */
@Composable
internal fun TrainDragLayer(service: TrackPlanService, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val layer = remember(service) { DragLayer(service) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    CompositionLocalProvider(LocalDragLayer provides layer) {
        Box(modifier.onGloballyPositioned { origin = it.positionInWindow() }) {
            content()
            layer.item?.let { item ->
                val p = layer.position - origin
                val target = layer.blockAt(layer.position)
                Surface(
                    color = if (target != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (target != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(50),
                    shadowElevation = 6.dp,
                    modifier = Modifier.offset { IntOffset(p.x.roundToInt() + 12, p.y.roundToInt() - 18) },
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Train, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(item.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Beschriftung eines Zugs: Lokname, sonst "Lok <Adresse>". */
internal fun trainLabel(address: Int, names: Map<Int, String>) = names[address]?.takeIf { it.isNotBlank() } ?: "Lok $address"

/** Alle Loks zum Ziehen ins Gleisbild; eingesetzte zeigen ihren Block. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrainStrip(app: MobaApp, service: TrackPlanService) {
    val layout by app.layout.layout.collectAsState()
    val trains by service.trains.collectAsState()
    val plans by service.plans.collectAsState()
    val layer = LocalDragLayer.current ?: return
    val locos = layout.locos.sortedBy { it.address }
    if (locos.isEmpty()) return
    val names = locos.associate { it.address to it.name }
    val where = trains.entries.associate { it.value to it.key }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Züge",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        locos.forEach { loco ->
            val block = where[loco.address]
            var chipOrigin by remember { mutableStateOf(Offset.Zero) }
            val label = trainLabel(loco.address, names)
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (block != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .onGloballyPositioned { chipOrigin = it.positionInWindow() }
                    .pointerInputDrag(layer, { chipOrigin }) { TrainDragItem(loco.address, label, block) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.DragIndicator, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (block != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        plans.blockName(block),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Ziehen einer Lok aus der Zugleiste. */
private fun Modifier.pointerInputDrag(layer: DragLayer, origin: () -> Offset, item: () -> TrainDragItem): Modifier =
    this.then(
        Modifier.pointerInput(layer) {
            detectDragGestures(
                onDragStart = { o -> layer.start(item(), origin() + o) },
                onDrag = { change, amount ->
                    change.consume()
                    layer.moveBy(amount)
                },
                onDragEnd = { layer.drop() },
                onDragCancel = { layer.cancel() },
            )
        },
    )

// ---------------------------------------------------------------- Kontextmenue

/** Offenes Kontextmenue an einer Kachel im Betrieb. */
internal class ContextTarget(val planId: String, val tile: TrackTile)

/**
 * Kontextmenue wie in iTrain, je nach Kachel: Bloecke (Zug einsetzen/entfernen, freigeben), Weichen (Stellungen),
 * Signale und Zubehoer mit Bildern (alle Bilder), schaltbares Zubehoer (ein/aus), Drehscheiben (Steuerung).
 */
@Composable
internal fun PlanContextMenu(app: MobaApp, service: TrackPlanService, target: ContextTarget, state: TrackState, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val layout by app.layout.layout.collectAsState()
    val trains by service.trains.collectAsState()
    val reservations by service.reservations.collectAsState()
    val plans by service.plans.collectAsState()
    val library = LocalSymbolLibrary.current
    val tile = target.tile
    fun act(action: suspend () -> Unit) {
        onDismiss()
        scope.launch { runCatching { action() }.onSuccess { say(null) }.onFailure { say(it.message ?: "Nicht möglich") } }
    }

    @Composable
    fun header(text: String) = Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )

    @Composable
    fun item(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, bold: Boolean = false, onClick: () -> Unit) =
        DropdownMenuItem(
            text = { Text(text, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal) },
            leadingIcon = icon?.let { { Icon(it, null, Modifier.size(20.dp)) } },
            onClick = onClick,
        )

    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        val block = tile.block()
        when {
            block != null -> {
                header(plans.blockName(block))
                val address = trains[block]
                val names = layout.locos.associate { it.address to it.name }
                val reservation = reservations[block]
                Text(
                    listOfNotNull(
                        if (block in state.occupied) "belegt" else "frei",
                        address?.let { "Zug: ${trainLabel(it, names)}" },
                        reservation?.let { "reserviert: ${it.label}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                if (address != null) item("Zug entfernen", Icons.Outlined.RemoveCircleOutline) { act { service.removeTrain(block) } }
                if (reservation != null) item("Block freigeben", Icons.Outlined.LockOpen) { act { service.release(block) } }
                val locos = layout.locos.sortedBy { it.address }.filter { it.address != address }
                if (locos.isNotEmpty()) {
                    HorizontalDivider()
                    header(if (address == null) "Zug einsetzen" else "Anderen Zug einsetzen")
                    locos.forEach { loco ->
                        item(trainLabel(loco.address, names), Icons.Outlined.Train) {
                            act {
                                if (address != null) service.removeTrain(block)
                                service.placeTrain(block, loco.address)
                            }
                        }
                    }
                }
            }
            tile.shape.kind == TileKind.TURNOUT && tile.address != null -> {
                header("${tile.shape.label} ${tile.address}")
                val a = tile.address!!
                val active = tile.activeBranch(state.turnouts)
                val options = when (tile.shape) {
                    TrackShape.TURNOUT_THREE_WAY -> listOf(Branch.MAIN to "Gerade", Branch.LEFT to "Links", Branch.RIGHT to "Rechts")
                    else -> listOf(Branch.MAIN to "Gerade", (if (tile.shape == TrackShape.TURNOUT_LEFT) Branch.LEFT else Branch.RIGHT) to "Abzweigend")
                }
                options.forEach { (branch, label) ->
                    item(label, bold = branch == active) {
                        act { service.setBranch(tile.shape, a, branch) }
                    }
                }
            }
            (tile.shape.kind == TileKind.SIGNAL || tile.shape.kind == TileKind.ASPECT) && tile.address != null -> {
                header(if (tile.shape.kind == TileKind.SIGNAL) "Signal ${tile.address}" else tile.displayName())
                val names = if (tile.shape.kind == TileKind.ASPECT) tile.aspectNames()
                else tileStates(tile, library).map { it.name }.ifEmpty { listOf("Halt", "Fahrt") }
                val current = state.aspects[tile.address!!]
                names.forEachIndexed { i, name -> item(name, bold = i == current) { act { service.setAspect(tile, i) } } }
            }
            tile.shape.switchable && (tile.address != null || tile.shape.kind == TileKind.SOUND) -> {
                header(tile.displayName())
                if (tile.shape.kind == TileKind.SOUND) {
                    item("Abspielen") { act { service.setOn(tile, true) } }
                } else {
                    val on = tile.isOn(state.turnouts)
                    item(if (tile.shape.kind == TileKind.GATE) "Schranken schließen" else "Einschalten", bold = on) { act { service.setOn(tile, true) } }
                    item(if (tile.shape.kind == TileKind.GATE) "Schranken öffnen" else "Ausschalten", bold = !on) { act { service.setOn(tile, false) } }
                }
            }
            tile.shape.isTable && tile.address != null -> {
                header(tile.displayName())
                item("Steuerung öffnen") {
                    onDismiss()
                    TrackPlanUiState.openTable.value = target.planId to (tile.x to tile.y)
                }
            }
            else -> header(tile.shape.label)
        }
        if (reservations.isNotEmpty()) {
            HorizontalDivider()
            item("Alle Blöcke freigeben (${reservations.size})", Icons.Outlined.LockOpen) { act { service.releaseAll() } }
        }
    }
}

/** Weiche direkt in eine Stellung bringen; die Dreiwegweiche nutzt [address] fuer links und [address] + 1 fuer rechts. */
private suspend fun TrackPlanService.setBranch(shape: TrackShape, address: Int, branch: Branch) {
    if (shape == TrackShape.TURNOUT_THREE_WAY) {
        // Erst den anderen Antrieb auf gerade, dann den gewuenschten
        when (branch) {
            Branch.MAIN -> {
                setTurnout(address, false)
                setTurnout(address + 1, false)
            }
            Branch.LEFT -> {
                setTurnout(address + 1, false)
                setTurnout(address, true)
            }
            Branch.RIGHT -> {
                setTurnout(address, false)
                setTurnout(address + 1, true)
            }
        }
    } else {
        setTurnout(address, branch != Branch.MAIN)
    }
}
