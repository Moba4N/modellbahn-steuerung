package de.moba.modules.symbols.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.moba.modules.symbols.api.PixelImage
import de.moba.modules.symbols.api.TRANSPARENT

private val CHECKER_LIGHT = Color(0xFFE6E8EB)
private val CHECKER_DARK = Color(0xFFC9CDD2)

/** Zeigt ein Pixelbild scharf vergroessert, z. B. als Vorschau oder in Listen. */
@Composable
fun SymbolImage(image: PixelImage, modifier: Modifier = Modifier, checkerboard: Boolean = false) {
    Canvas(modifier) { drawPixels(image, checkerboard) }
}

/** Zeichnet [image] mittig in die Flaeche; liefert Kantenlaenge eines Pixels und linke obere Ecke. */
fun DrawScope.drawPixels(image: PixelImage, checkerboard: Boolean = false, origin: Offset? = null, side: Float? = null): Pair<Float, Offset> {
    val full = side ?: minOf(size.width, size.height)
    val cell = full / image.size
    val topLeft = origin ?: Offset((size.width - cell * image.size) / 2f, (size.height - cell * image.size) / 2f)
    val pixel = Size(cell + 0.6f, cell + 0.6f)
    for (y in 0 until image.size) for (x in 0 until image.size) {
        val c = image[x, y]
        val color = when {
            c != TRANSPARENT -> Color(c)
            checkerboard -> if ((x + y) % 2 == 0) CHECKER_LIGHT else CHECKER_DARK
            else -> continue
        }
        drawRect(color, Offset(topLeft.x + x * cell, topLeft.y + y * cell), pixel)
    }
    return cell to topLeft
}
