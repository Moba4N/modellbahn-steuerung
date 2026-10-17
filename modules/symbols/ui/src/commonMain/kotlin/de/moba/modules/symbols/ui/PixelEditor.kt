package de.moba.modules.symbols.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.moba.modules.symbols.api.LampColor
import de.moba.modules.symbols.api.PixelImage
import de.moba.modules.symbols.api.TRANSPARENT

/** Zeichenwerkzeuge wie im Symbol-Editor von TrainController. */
enum class DrawTool(val label: String, val icon: ImageVector) {
    PENCIL("Stift", Icons.Outlined.Edit),
    ERASER("Radierer", Icons.Outlined.AutoFixNormal),
    LINE("Linie", Icons.Outlined.HorizontalRule),
    RECT("Rechteck", Icons.Outlined.CropSquare),
    RECT_FILLED("Rechteck gefüllt", Icons.Filled.Square),
    ELLIPSE("Ellipse", Icons.Outlined.Circle),
    ELLIPSE_FILLED("Ellipse gefüllt", Icons.Filled.Circle),
    FILL("Fläche füllen", Icons.Outlined.FormatColorFill),
    PICKER("Farbe aufnehmen", Icons.Outlined.Colorize),
}

/** Grundpalette: Grautoene, Grundfarben, Erd- und Gleisfarben, dazu die Signallichter. */
val PALETTE: List<Int> = listOf(
    0xFF000000, 0xFF3A3F44, 0xFF6B7178, 0xFF9AA0A6, 0xFFC8CCD0, 0xFFFFFFFF,
    0xFF7A1F1A, 0xFFD32F2F, 0xFFF57C00, 0xFFFBC02D, 0xFF8D6E63, 0xFF5D4037,
    0xFF1B5E20, 0xFF43A047, 0xFF00796B, 0xFF4FC3F7, 0xFF1565C0, 0xFF283593,
    0xFF6A1B9A, 0xFFD81B60, 0xFFD9C7A7, 0xFFB0413E, 0xFF6FA8DC, 0xFF2B2F33,
).map { it.toInt() } + LampColor.entries.map { it.argb }

/**
 * Die Zeichenflaeche: ein Pixelraster, auf dem mit Maus, Stift oder Finger gezeichnet wird.
 * Formen (Linie, Rechteck, Ellipse) erscheinen beim Ziehen als Vorschau und werden beim Loslassen uebernommen.
 */
@Composable
fun PixelCanvas(
    image: PixelImage,
    tool: DrawTool,
    color: Int,
    showGrid: Boolean,
    onCommit: (PixelImage) -> Unit,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var preview by remember(image) { mutableStateOf<PixelImage?>(null) }
    val current by rememberUpdatedState(image)
    val currentTool by rememberUpdatedState(tool)
    val currentColor by rememberUpdatedState(color)
    val commit by rememberUpdatedState(onCommit)
    val pick by rememberUpdatedState(onPick)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val frame = MaterialTheme.colorScheme.outline

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Canvas(
            Modifier
                .size(side)
                .border(1.dp, frame)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val base = current
                        val cell = size.width.toFloat() / base.size
                        fun cellOf(o: Offset) = Pair(
                            (o.x / cell).toInt().coerceIn(0, base.size - 1),
                            (o.y / cell).toInt().coerceIn(0, base.size - 1),
                        )
                        val activeTool = currentTool
                        val paint = if (activeTool == DrawTool.ERASER) TRANSPARENT else currentColor
                        val (sx, sy) = cellOf(down.position)
                        down.consume()
                        fun shape(x: Int, y: Int): PixelImage = when (activeTool) {
                            DrawTool.LINE -> base.line(sx, sy, x, y, paint)
                            DrawTool.RECT -> base.rect(sx, sy, x, y, paint, filled = false)
                            DrawTool.RECT_FILLED -> base.rect(sx, sy, x, y, paint, filled = true)
                            DrawTool.ELLIPSE -> base.ellipse(sx, sy, x, y, paint, filled = false)
                            DrawTool.ELLIPSE_FILLED -> base.ellipse(sx, sy, x, y, paint, filled = true)
                            else -> base
                        }
                        when (activeTool) {
                            DrawTool.PICKER -> pick(base[sx, sy])
                            DrawTool.FILL -> commit(base.fill(sx, sy, paint))
                            else -> {
                                val freehand = activeTool == DrawTool.PENCIL || activeTool == DrawTool.ERASER
                                var img = if (freehand) base.set(sx, sy, paint) else shape(sx, sy)
                                var last = sx to sy
                                preview = img
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    val p = cellOf(change.position)
                                    if (p == last) continue
                                    img = if (freehand) img.line(last.first, last.second, p.first, p.second, paint) else shape(p.first, p.second)
                                    last = p
                                    preview = img
                                }
                                preview = null
                                commit(img)
                            }
                        }
                    }
                },
        ) {
            val shown = preview ?: image
            val (cell, topLeft) = drawPixels(shown, checkerboard = true, origin = Offset.Zero, side = size.width)
            if (showGrid && cell >= 6f) {
                for (i in 1 until shown.size) {
                    val p = i * cell
                    drawLine(gridColor, Offset(topLeft.x + p, 0f), Offset(topLeft.x + p, size.height), 1f)
                    drawLine(gridColor, Offset(0f, topLeft.y + p), Offset(size.width, topLeft.y + p), 1f)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolBar(tool: DrawTool, onTool: (DrawTool) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DrawTool.entries.forEach { t ->
            FilledIconToggleButton(checked = t == tool, onCheckedChange = { onTool(t) }) {
                Icon(t.icon, t.label)
            }
        }
    }
}

/** Farbauswahl: Palette, Durchsichtig und eigene Farbe als Hex-Wert (#RRGGBB). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Palette(color: Int, onColor: (Int) -> Unit) {
    var hex by remember(color) { mutableStateOf(colorToHex(color)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Swatch(color, selected = false, size = 40)
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = hex,
            onValueChange = { text ->
                hex = text
                hexToColor(text)?.let(onColor)
            },
            label = { Text("Eigene Farbe") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.width(150.dp),
        )
    }
    FlowRow(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (PALETTE + TRANSPARENT).forEach { c ->
            Box(Modifier.clickable { onColor(c) }) { Swatch(c, selected = c == color, size = 26) }
        }
    }
}

@Composable
private fun Swatch(color: Int, selected: Boolean, size: Int) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(size.dp)
            .clip(shape)
            .border(
                BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                shape,
            ),
    ) {
        if (color == TRANSPARENT) {
            SymbolImage(PixelImage(16, IntArray(256)), Modifier.size(size.dp), checkerboard = true)
        } else {
            Box(Modifier.size(size.dp).background(Color(color)))
        }
    }
}

fun colorToHex(color: Int): String =
    if (color == TRANSPARENT) "durchsichtig" else "#" + (color and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

fun hexToColor(text: String): Int? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != 6) return null
    val rgb = digits.toIntOrNull(16) ?: return null
    return (0xFF000000.toInt()) or rgb
}
