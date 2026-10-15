package de.moba.modules.trackplan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import de.moba.modules.trackplan.api.Branch
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
    onTap: (x: Int, y: Int) -> Unit = { _, _ -> },
) {
    val colors = trackColors()
    val measurer = rememberTextMeasurer()
    val cellPx = with(LocalDensity.current) { cell.toPx() }
    val tap by rememberUpdatedState(onTap)
    Canvas(
        modifier
            .size(cell * plan.width, cell * plan.height)
            .pointerInput(plan.width, plan.height, cellPx) {
                detectTapGestures { o ->
                    val x = (o.x / cellPx).toInt()
                    val y = (o.y / cellPx).toInt()
                    if (x in 0 until plan.width && y in 0 until plan.height) tap(x, y)
                }
            },
    ) {
        if (showGrid) {
            val r = (cellPx * 0.04f).coerceAtLeast(1f)
            for (gx in 0..plan.width) for (gy in 0..plan.height) {
                drawCircle(colors.grid, r, Offset(gx * cellPx, gy * cellPx))
            }
        }
        for (tile in plan.tiles) drawTile(tile, tile.x * cellPx, tile.y * cellPx, cellPx, colors, state, measurer)
        selected?.let { (sx, sy) ->
            drawRoundRect(
                colors.selection,
                topLeft = Offset(sx * cellPx + 1f, sy * cellPx + 1f),
                size = Size(cellPx - 2f, cellPx - 2f),
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
        drawTile(tile.copy(x = 0, y = 0), 0f, 0f, this.size.width, colors, state, measurer)
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

    val occupied = kind == TileKind.FEEDBACK && tile.feedbackModule != null &&
        (tile.feedbackModule to tile.feedbackContact) in state.occupied
    val active: Branch? = if (state.operating && kind == TileKind.TURNOUT) {
        tile.activeBranch(state.turnouts) ?: Branch.MAIN
    } else {
        null
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
