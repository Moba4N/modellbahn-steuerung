package de.moba.modules.trackplan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import de.moba.modules.trackplan.api.AccessoryProps
import de.moba.modules.trackplan.api.Branch
import de.moba.modules.trackplan.api.FeedbackRef
import de.moba.modules.trackplan.api.Footprint
import de.moba.modules.trackplan.api.block
import de.moba.modules.trackplan.api.TableConfig
import de.moba.modules.trackplan.api.aspectNames
import de.moba.modules.trackplan.api.isOn
import de.moba.modules.trackplan.api.Cell
import de.moba.modules.trackplan.api.TileKind
import de.moba.modules.trackplan.api.TrackDir
import de.moba.modules.trackplan.api.TrackPlan
import de.moba.modules.trackplan.api.TrackTile
import de.moba.modules.trackplan.api.activeBranch
import de.moba.modules.symbols.api.PixelImage
import de.moba.modules.symbols.ui.drawPixels
import de.moba.ui.shell.Moba
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Farben des Gleisbilds, abgeleitet vom App-Thema. */
@Immutable
internal class TrackColors(
    val track: Color,
    val dimmed: Color,
    val occupied: Color,
    val signalGreen: Color,
    val signalRed: Color,
    val unknown: Color,
    val uncoupler: Color,
    val grid: Color,
    val selection: Color,
    val background: Color,
    val text: Color,
    val reserved: Color,
    val train: Color,
    val onTrain: Color,
)

@Composable
internal fun trackColors(): TrackColors {
    val scheme = MaterialTheme.colorScheme
    val moba = Moba.colors
    return TrackColors(
        track = scheme.onSurface.copy(alpha = 0.82f),
        dimmed = scheme.onSurface.copy(alpha = 0.22f),
        occupied = moba.occupied,
        signalGreen = moba.signalGreen,
        signalRed = moba.alarm,
        unknown = moba.locked,
        uncoupler = Color(0xFFE0C341),
        grid = scheme.outline.copy(alpha = 0.45f),
        selection = scheme.primary,
        background = scheme.surfaceContainerLowest,
        text = scheme.onSurfaceVariant,
        reserved = moba.signalGreen.copy(alpha = 0.32f),
        train = scheme.primary,
        onTrain = scheme.onPrimary,
    )
}

/** Was das Gleisbild im Betrieb zusaetzlich zeigt: Weichenstellungen und Belegung. */
internal class TrackState(
    val turnouts: Map<Int, Boolean> = emptyMap(),
    val occupied: Set<Pair<Int, Int>> = emptySet(),
    /** Im Betrieb wird der nicht befahrene Weichenstrang abgeblendet, im Editor nicht. */
    val operating: Boolean = false,
    /** Bild fuer Signal- und Symbolkacheln mit gewaehltem Symbol, sonst null. */
    val symbolImage: (TrackTile) -> PixelImage? = { null },
    /** Aktuelles Bild je Grundadresse (Signale und Zubehoer mit Bildern). */
    val aspects: Map<Int, Int> = emptyMap(),
    /** Position der Buehne je Grundadresse von Drehscheiben und Schiebebuehnen. */
    val tablePositions: Map<Int, Int> = emptyMap(),
    /** Buehnen, die gerade drehen bzw. fahren. */
    val moving: Set<Int> = emptySet(),
    /** Zuege in Bloecken: Block zu Beschriftung (Lokname). */
    val trains: Map<FeedbackRef, String> = emptyMap(),
    /** Reservierte Bloecke. */
    val reserved: Set<FeedbackRef> = emptySet(),
)

/**
 * Zeichnet ein Gleisbild mit festen Kachelgroessen. [onTap] bekommt die angetippte Rasterzelle.
 * [selected] wird im Editor umrandet, [showGrid] zeigt Rasterpunkte.
 */
@Composable
internal fun TrackPlanView(
    plan: TrackPlan,
    cell: Dp,
    state: TrackState,
    modifier: Modifier = Modifier,
    selected: Pair<Int, Int>? = null,
    showGrid: Boolean = false,
    /** Kontextmenue: Rechtsklick (Windows) oder langes Druecken (Android); `null` = keins. */
    onContext: ((x: Int, y: Int) -> Unit)? = null,
    onTap: (x: Int, y: Int) -> Unit = { _, _ -> },
) {
    val colors = trackColors()
    val measurer = rememberTextMeasurer()
    val cellPx = with(LocalDensity.current) { cell.toPx() }
    val tap by rememberUpdatedState(onTap)
    val context by rememberUpdatedState(onContext)
    val current by rememberUpdatedState(plan)
    val currentState by rememberUpdatedState(state)
    // Zuege mit der Maus bzw. dem Finger von Block zu Block ziehen (nur im Betrieb)
    val layer = LocalDragLayer.current?.takeIf { state.operating }
    fun cellOf(o: Offset): Pair<Int, Int>? {
        val x = (o.x / cellPx).toInt()
        val y = (o.y / cellPx).toInt()
        return if (o.x >= 0 && o.y >= 0 && x in 0 until current.width && y in 0 until current.height) x to y else null
    }
    Canvas(
        modifier
            .size(cell * plan.width, cell * plan.height)
            .then(if (layer != null) Modifier.onGloballyPositioned { layer.register(plan.id, it.positionInWindow(), cellPx) { current } } else Modifier)
            .pointerInput(plan.width, plan.height, cellPx) {
                detectTapGestures(
                    onLongPress = { o -> cellOf(o)?.let { (x, y) -> context?.invoke(x, y) } },
                    onTap = { o -> cellOf(o)?.let { (x, y) -> tap(x, y) } },
                )
            }
            .pointerInput(cellPx) {
                // Rechtsklick vor allen anderen Gesten abfangen
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            val handler = context ?: continue
                            val change = event.changes.first()
                            cellOf(change.position)?.let { (x, y) -> handler(x, y) }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .then(
                if (layer == null) Modifier else Modifier.pointerInput(layer, cellPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.isConsumed) return@awaitEachGesture
                        val (x, y) = cellOf(down.position) ?: return@awaitEachGesture
                        val block = current.objectAt(x, y)?.block() ?: return@awaitEachGesture
                        val label = currentState.trains[block] ?: return@awaitEachGesture
                        val start = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: return@awaitEachGesture
                        val origin = layer.originOf(current.id)
                        layer.start(TrainDragItem(layer.addressIn(block) ?: return@awaitEachGesture, label, block), origin + start.position)
                        val done = drag(start.id) { change ->
                            layer.moveTo(origin + change.position)
                            change.consume()
                        }
                        if (done) layer.drop() else layer.cancel()
                    }
                },
            ),
    ) {
        if (showGrid) {
            val r = (cellPx * 0.04f).coerceAtLeast(1f)
            for (gx in 0..plan.width) for (gy in 0..plan.height) {
                drawCircle(colors.grid, r, Offset(gx * cellPx, gy * cellPx))
            }
        }
        // Drehscheiben und Schiebebuehnen zuerst, die anschliessenden Gleise liegen darueber
        for (tile in plan.tiles.sortedByDescending { it.shape.isTable }) drawTile(tile, tile.x * cellPx, tile.y * cellPx, cellPx, colors, state, measurer)
        // Zugnamen obenauf, damit sie ueber Nachbarkacheln reichen duerfen
        if (state.trains.isNotEmpty()) {
            for (tile in plan.tiles) {
                val label = tile.block()?.let { state.trains[it] } ?: continue
                drawTrainLabel(label, Offset((tile.x + 0.5f) * cellPx, (tile.y + 0.5f) * cellPx), cellPx, colors, measurer)
            }
        }
        selected?.let { (sx, sy) ->
            val area = plan.tileAt(sx, sy)?.footprint() ?: Footprint(sx, sy, 1, 1)
            drawRoundRect(
                colors.selection,
                topLeft = Offset(area.x * cellPx + 1f, area.y * cellPx + 1f),
                size = Size(area.width * cellPx - 2f, area.height * cellPx - 2f),
                cornerRadius = CornerRadius(cellPx * 0.15f),
                style = Stroke(width = (cellPx * 0.07f).coerceAtLeast(2f)),
            )
        }
    }
}

/** Kleine Vorschau einer Gleisform fuer die Palette. */
@Composable
internal fun ShapePreview(tile: TrackTile, size: Dp, state: TrackState = TrackState()) {
    val colors = trackColors()
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.size(size)) {
        if (tile.shape.isTable) {
            // Drehscheibe und Schiebebuehne verkleinert auf 3 x 3 Felder
            val small = tile.copy(x = 0, y = 0)
                .withProp(AccessoryProps.SIZE, if (tile.shape.kind == TileKind.TURNTABLE) "1" else "3")
                .withProp(AccessoryProps.POSITIONS, if (tile.shape.kind == TileKind.TURNTABLE) tile.prop(AccessoryProps.POSITIONS).ifEmpty { "24" } else "3")
            val area = small.footprint()
            val cell = this.size.width / maxOf(area.width, area.height)
            drawTile(small, -area.x * cell, -area.y * cell, cell, colors, state, measurer)
        } else {
            drawTile(tile.copy(x = 0, y = 0), 0f, 0f, this.size.width, colors, state, measurer)
        }
    }
}

// Richtungspunkte als Vielfaches der halben Kachelbreite, 0 = oben, im Uhrzeigersinn.
private val DX = floatArrayOf(0f, 1f, 1f, 1f, 0f, -1f, -1f, -1f)
private val DY = floatArrayOf(-1f, -1f, 0f, 1f, 1f, 1f, 0f, -1f)

internal fun DrawScope.drawTile(
    tile: TrackTile,
    left: Float,
    top: Float,
    cell: Float,
    colors: TrackColors,
    state: TrackState,
    measurer: TextMeasurer,
) {
    val c = Offset(left + cell / 2, top + cell / 2)
    fun point(dir: Int) = if (dir == TrackDir.CENTER) c else Offset(c.x + DX[dir] * cell / 2, c.y + DY[dir] * cell / 2)
    val width = cell * 0.16f
    val kind = tile.shape.kind

    if (kind == TileKind.TEXT) {
        if (tile.text.isNotBlank()) {
            val style = TextStyle(color = colors.text, fontSize = (cell * 0.42f).toSp(), fontWeight = FontWeight.Medium)
            val layout = measurer.measure(tile.text, style)
            drawText(layout, topLeft = Offset(left + cell * 0.1f, c.y - layout.size.height / 2f))
        } else {
            drawRoundRect(colors.dimmed, Offset(left + cell * 0.2f, top + cell * 0.35f), Size(cell * 0.6f, cell * 0.3f), CornerRadius(cell * 0.05f))
        }
        return
    }
    val image = state.symbolImage(tile)
    if (kind == TileKind.SYMBOL) {
        if (image != null) {
            drawPixels(image, origin = Offset(left, top), side = cell)
        } else {
            drawRoundRect(
                colors.dimmed,
                Offset(left + cell * 0.15f, top + cell * 0.15f),
                Size(cell * 0.7f, cell * 0.7f),
                CornerRadius(cell * 0.1f),
                style = Stroke(width = (cell * 0.06f).coerceAtLeast(1f)),
            )
            drawCircle(colors.dimmed, cell * 0.12f, c)
        }
        return
    }

    when (kind) {
        TileKind.TURNTABLE -> return drawTurntable(tile, left, top, cell, colors, state)
        TileKind.TRANSFER -> return drawTransferTable(tile, left, top, cell, colors, state)
        TileKind.RELAY, TileKind.LIGHT, TileKind.SOUND, TileKind.ASPECT -> return drawAccessory(tile, left, top, cell, colors, state)
        else -> Unit
    }

    val occupied = kind == TileKind.FEEDBACK && tile.feedbackModule != null &&
        (tile.feedbackModule to tile.feedbackContact) in state.occupied
    val active: Branch? = if (state.operating && kind == TileKind.TURNOUT) {
        tile.activeBranch(state.turnouts) ?: Branch.MAIN
    } else {
        null
    }
    if (kind == TileKind.FEEDBACK && tile.block()?.let { it in state.reserved } == true) {
        // Reservierter Block: farbig unterlegt wie in iTrain
        drawRoundRect(colors.reserved, Offset(left + cell * 0.04f, top + cell * 0.04f), Size(cell * 0.92f, cell * 0.92f), CornerRadius(cell * 0.18f))
    }
    val closed = kind == TileKind.GATE && state.operating && tile.isOn(state.turnouts)
    if (kind == TileKind.GATE) {
        // Strasse quer zum Gleis
        val g = tile.segments().first()
        val roadAlong = Offset(DX[(g.from + 2) % 8], DY[(g.from + 2) % 8]).let { it / it.getDistance() }
        drawLine(colors.dimmed, c + roadAlong * (cell * 0.5f), c - roadAlong * (cell * 0.5f), strokeWidth = cell * 0.46f)
    }
    // Nicht befahrene Straenge zuerst, damit der befahrene obenauf liegt.
    val segments = tile.segments().sortedBy { active != null && it.branch == active }
    for (seg in segments) {
        val color = when {
            occupied -> colors.occupied
            active != null && seg.branch != active -> colors.dimmed
            else -> colors.track
        }
        val path = Path().apply {
            val from = point(seg.from)
            moveTo(from.x, from.y)
            if (seg.to == TrackDir.CENTER) {
                lineTo(c.x, c.y)
            } else {
                val to = point(seg.to)
                quadraticTo(c.x, c.y, to.x, to.y)
            }
        }
        drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round))
    }

    // Laengsrichtung des Gleises (erstes Segment) fuer Signale, Entkuppler und Prellbock.
    val first = tile.segments().firstOrNull() ?: return
    val a = point(first.from)
    val b = if (first.to == TrackDir.CENTER) c else point(first.to)
    val len = sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)).coerceAtLeast(0.001f)
    val along = Offset((b.x - a.x) / len, (b.y - a.y) / len)
    val across = Offset(-along.y, along.x)

    when (kind) {
        TileKind.BUFFER -> drawLine(
            colors.track,
            c + across * (cell * 0.3f),
            c - across * (cell * 0.3f),
            strokeWidth = width * 1.3f,
            cap = StrokeCap.Round,
        )
        TileKind.UNCOUPLER -> drawLine(
            colors.uncoupler,
            c + across * (cell * 0.26f),
            c - across * (cell * 0.26f),
            strokeWidth = width * 1.6f,
        )
        TileKind.FEEDBACK -> {
            val size = Size(cell * 0.34f, cell * 0.34f)
            val topLeft = Offset(c.x - size.width / 2, c.y - size.height / 2)
            drawRoundRect(if (occupied) colors.occupied else colors.background, topLeft, size, CornerRadius(cell * 0.06f))
            drawRoundRect(
                if (occupied) colors.occupied else colors.track,
                topLeft,
                size,
                CornerRadius(cell * 0.06f),
                style = Stroke(width = width * 0.5f),
            )
        }
        TileKind.GATE -> {
            // Schranken beidseits des Gleises: geschlossen rot quer ueber der Strasse, offen kurz und hochgeklappt
            val bar = if (closed) colors.signalRed else colors.track
            for (sign in listOf(1f, -1f)) {
                val post = c + across * (cell * 0.36f * sign)
                if (closed) {
                    drawLine(bar, post - along * (cell * 0.24f), post + along * (cell * 0.24f), strokeWidth = width * 0.7f, cap = StrokeCap.Round)
                } else {
                    drawLine(bar, post - along * (cell * 0.24f), post - along * (cell * 0.24f) + across * (cell * 0.12f * sign), strokeWidth = width * 0.6f, cap = StrokeCap.Round)
                }
                drawCircle(bar, width * 0.5f, post - along * (cell * 0.24f))
            }
        }
        TileKind.SIGNAL -> if (image != null) {
            // Wie in iTrain: Signal neben dem Gleis, Mastfuss am Gleis, Schirm zeigt vom Gleis weg
            val side = cell * 0.9f
            val center = c + across * (cell * 0.3f)
            val degrees = atan2(across.x, -across.y) * 180f / PI.toFloat()
            rotate(degrees, pivot = center) {
                drawPixels(image, origin = Offset(center.x - side / 2, center.y - side / 2), side = side)
            }
        } else {
            val lamp = when (tile.address?.let { state.turnouts[it] }) {
                true -> colors.signalRed
                false -> colors.signalGreen
                null -> colors.unknown
            }
            val foot = c + across * (cell * 0.16f)
            val head = c + across * (cell * 0.34f)
            drawLine(colors.track, foot, head, strokeWidth = width * 0.45f)
            drawCircle(colors.background, cell * 0.15f, head)
            drawCircle(lamp, cell * 0.11f, head)
        }
        else -> Unit
    }
}

/**
 * Gleisbild zum Auswaehlen fuer andere Module (z. B. Fahrstrassen): [highlight] wird farbig unterlegt,
 * Weichen stehen wie in [turnouts], [marks] beschriftet Zellen (z. B. "A", "B", "1"). [onTap] meldet die angetippte Zelle.
 */
@Composable
fun TrackPlanPicker(
    plan: TrackPlan,
    cell: Dp,
    turnouts: Map<Int, Boolean>,
    highlight: List<Cell>,
    marks: Map<Cell, String>,
    modifier: Modifier = Modifier,
    onTap: (Cell) -> Unit,
) {
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val markColor = MaterialTheme.colorScheme.tertiary
    val markText = MaterialTheme.colorScheme.onTertiary
    val measurer = rememberTextMeasurer()
    val cellPx = with(LocalDensity.current) { cell.toPx() }
    Box(modifier) {
        TrackPlanView(plan, cell, TrackState(turnouts = turnouts, operating = true), onTap = { x, y -> onTap(Cell(x, y)) })
        Canvas(Modifier.matchParentSize()) {
            for (c in highlight.toSet()) {
                drawRoundRect(fill, Offset(c.x * cellPx, c.y * cellPx), Size(cellPx, cellPx), CornerRadius(cellPx * 0.2f))
            }
            for ((c, label) in marks) {
                val center = Offset((c.x + 0.5f) * cellPx, (c.y + 0.5f) * cellPx)
                val r = (cellPx * 0.42f).coerceAtLeast(8f)
                drawCircle(markColor, r, center)
                val layout = measurer.measure(label, TextStyle(color = markText, fontSize = (r * 1.1f / density).sp, fontWeight = FontWeight.Bold))
                drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
            }
        }
    }
}

/** Relais, Licht, Sound und Zubehoer mit Bildern als Zeichen in der Kachel; eingeschaltet farbig. */
private fun DrawScope.drawAccessory(tile: TrackTile, left: Float, top: Float, cell: Float, colors: TrackColors, state: TrackState) {
    val c = Offset(left + cell / 2, top + cell / 2)
    val on = state.operating && tile.isOn(state.turnouts)
    val line = (cell * 0.07f).coerceAtLeast(1f)
    val frame = Size(cell * 0.76f, cell * 0.76f)
    val frameTopLeft = Offset(c.x - frame.width / 2, c.y - frame.height / 2)
    drawRoundRect(colors.background, frameTopLeft, frame, CornerRadius(cell * 0.12f))
    drawRoundRect(colors.track, frameTopLeft, frame, CornerRadius(cell * 0.12f), style = Stroke(line))
    when (tile.shape.kind) {
        TileKind.RELAY -> {
            // Spule links, Kontakt rechts: offen schraeg, geschlossen gerade und gruen
            val coil = Size(cell * 0.18f, cell * 0.36f)
            drawRect(colors.track, Offset(c.x - cell * 0.26f, c.y - coil.height / 2), coil, style = Stroke(line))
            val pivot = Offset(c.x, c.y + cell * 0.14f)
            val end = if (on) Offset(c.x + cell * 0.26f, c.y + cell * 0.14f) else Offset(c.x + cell * 0.22f, c.y - cell * 0.08f)
            val contact = if (on) colors.signalGreen else colors.track
            drawLine(contact, pivot, end, strokeWidth = line * 1.3f, cap = StrokeCap.Round)
            drawCircle(contact, line * 1.1f, pivot)
            drawCircle(contact, line * 1.1f, Offset(c.x + cell * 0.26f, c.y + cell * 0.14f))
        }
        TileKind.LIGHT -> {
            val bulb = Offset(c.x, c.y - cell * 0.04f)
            if (on) {
                drawCircle(colors.uncoupler.copy(alpha = 0.35f), cell * 0.3f, bulb)
                drawCircle(colors.uncoupler, cell * 0.17f, bulb)
            } else {
                drawCircle(colors.track, cell * 0.17f, bulb, style = Stroke(line))
            }
            drawLine(colors.track, Offset(c.x - cell * 0.08f, c.y + cell * 0.2f), Offset(c.x + cell * 0.08f, c.y + cell * 0.2f), strokeWidth = line * 1.4f)
        }
        TileKind.SOUND -> {
            val col = if (on) colors.signalGreen else colors.track
            val speaker = Path().apply {
                moveTo(c.x - cell * 0.24f, c.y - cell * 0.08f)
                lineTo(c.x - cell * 0.12f, c.y - cell * 0.08f)
                lineTo(c.x + cell * 0.02f, c.y - cell * 0.2f)
                lineTo(c.x + cell * 0.02f, c.y + cell * 0.2f)
                lineTo(c.x - cell * 0.12f, c.y + cell * 0.08f)
                lineTo(c.x - cell * 0.24f, c.y + cell * 0.08f)
                close()
            }
            drawPath(speaker, col)
            for (r in listOf(0.12f, 0.22f)) {
                drawArc(col, -45f, 90f, false, Offset(c.x + cell * 0.02f - cell * r, c.y - cell * r), Size(cell * r * 2, cell * r * 2), style = Stroke(line, cap = StrokeCap.Round))
            }
        }
        else -> {
            // Zubehoer mit Bildern: ein Punkt je Bild, das aktuelle leuchtet
            val count = tile.aspectNames().size
            val current = tile.address?.let { state.aspects[it] }
            val perRow = if (count > 4) (count + 1) / 2 else count
            val rows = if (count > 4) 2 else 1
            val step = cell * 0.6f / perRow
            for (i in 0 until count) {
                val col = i % perRow
                val row = i / perRow
                val p = Offset(c.x - cell * 0.3f + step * (col + 0.5f), c.y + (row - (rows - 1) / 2f) * cell * 0.22f)
                val lit = state.operating && i == current
                drawCircle(if (lit) colors.signalGreen else colors.dimmed, (step * 0.32f).coerceAtMost(cell * 0.08f), p)
            }
        }
    }
}

/**
 * Drehscheibe: Grube um die Kachel mit Radius aus dem Reiter Zubehoer, Anschluesse an den Gleispositionen
 * (Position 0 oben, im Uhrzeigersinn) und die Buehne an ihrer aktuellen Position; waehrend sie dreht, gelb.
 */
private fun DrawScope.drawTurntable(tile: TrackTile, left: Float, top: Float, cell: Float, colors: TrackColors, state: TrackState) {
    val config = TableConfig.of(tile)
    val c = Offset(left + cell / 2, top + cell / 2)
    val pit = (config.radius + 0.5f) * cell * 0.94f
    val line = (cell * 0.07f).coerceAtLeast(1f)
    val width = cell * 0.16f
    drawCircle(colors.background, pit, c)
    drawCircle(colors.track, pit, c, style = Stroke(line * 1.2f))
    drawCircle(colors.dimmed, pit * 0.86f, c, style = Stroke(line * 0.6f))
    fun dir(position: Int): Offset {
        val angle = position * 2 * PI / config.positions
        return Offset(sin(angle).toFloat(), -cos(angle).toFloat())
    }
    for (p in config.tracks) {
        val d = dir(p)
        drawLine(colors.track, c + d * (pit * 0.86f), c + d * (pit + cell * 0.5f * 0.06f), strokeWidth = width, cap = StrokeCap.Butt)
    }
    val base = tile.address
    val position = base?.let { state.tablePositions[it] }
    val moving = base != null && base in state.moving
    val d = dir(position ?: config.tracks.first())
    val bridge = when {
        moving -> colors.uncoupler
        position == null || !state.operating -> colors.track
        else -> colors.selection
    }
    val across = Offset(-d.y, d.x)
    val half = pit * 0.84f
    drawLine(bridge.copy(alpha = 0.35f), c + d * half, c - d * half, strokeWidth = cell * 0.5f)
    drawLine(bridge, c + d * half, c - d * half, strokeWidth = width, cap = StrokeCap.Butt)
    // Haeuschen markiert das Buehnenende, das an der Position steht
    val hut = c + d * (half - cell * 0.18f) + across * (cell * 0.2f)
    drawCircle(bridge, cell * 0.09f, hut)
    drawCircle(colors.track, cell * 0.08f, c)
}

/**
 * Schiebebuehne: Grube ueber alle Gleise, Anschluesse links und rechts (bzw. oben und unten),
 * die Buehne steht an der aktuellen Position.
 */
private fun DrawScope.drawTransferTable(tile: TrackTile, left: Float, top: Float, cell: Float, colors: TrackColors, state: TrackState) {
    val config = TableConfig.of(tile)
    val area = tile.footprint()
    val origin = Offset(left + (area.x - tile.x) * cell, top + (area.y - tile.y) * cell)
    val size = Size(area.width * cell, area.height * cell)
    val line = (cell * 0.07f).coerceAtLeast(1f)
    val width = cell * 0.16f
    val inset = cell * 0.3f
    drawRect(colors.background, origin + Offset(inset, 0f).let { if (config.vertical) Offset(0f, inset) else it }, if (config.vertical) Size(size.width, size.height - 2 * inset) else Size(size.width - 2 * inset, size.height))
    drawRect(colors.track, origin + if (config.vertical) Offset(0f, inset) else Offset(inset, 0f), if (config.vertical) Size(size.width, size.height - 2 * inset) else Size(size.width - 2 * inset, size.height), style = Stroke(line * 1.2f))
    // Mitte des Gleises an Position p und die Richtung entlang der Gleise
    fun center(p: Int) = if (config.vertical) Offset(origin.x + (p + 0.5f) * cell, origin.y) else Offset(origin.x, origin.y + (p + 0.5f) * cell)
    val along = if (config.vertical) Offset(0f, 1f) else Offset(1f, 0f)
    val length = config.length * cell
    for (p in config.tracks) {
        val start = center(p)
        drawLine(colors.track, start, start + along * inset, strokeWidth = width)
        drawLine(colors.track, start + along * (length - inset), start + along * length, strokeWidth = width)
    }
    val base = tile.address
    val position = base?.let { state.tablePositions[it] }
    val moving = base != null && base in state.moving
    val bridge = when {
        moving -> colors.uncoupler
        position == null || !state.operating -> colors.track
        else -> colors.selection
    }
    val start = center(position ?: config.tracks.first())
    drawLine(bridge.copy(alpha = 0.35f), start + along * inset, start + along * (length - inset), strokeWidth = cell * 0.62f)
    drawLine(bridge, start + along * inset, start + along * (length - inset), strokeWidth = width)
}

/** Zugname als Schild ueber dem Block. */
internal fun DrawScope.drawTrainLabel(label: String, center: Offset, cell: Float, colors: TrackColors, measurer: TextMeasurer) {
    val style = TextStyle(color = colors.onTrain, fontSize = (cell * 0.36f).coerceAtLeast(9f * density).toSp(), fontWeight = FontWeight.SemiBold)
    val layout = measurer.measure(label, style, maxLines = 1)
    val padX = cell * 0.18f
    val w = layout.size.width + 2 * padX
    val h = layout.size.height + cell * 0.08f
    val topLeft = Offset(center.x - w / 2, center.y - h / 2)
    drawRoundRect(colors.train, topLeft, Size(w, h), CornerRadius(h / 2))
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}
