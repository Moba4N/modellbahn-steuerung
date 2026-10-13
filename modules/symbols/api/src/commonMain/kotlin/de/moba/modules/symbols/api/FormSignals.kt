package de.moba.modules.symbols.api

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Zeichnet Formsignale (Tagesbild): Hauptsignal mit Fluegeln, Vorsignal mit Scheibe und Zusatzfluegel,
 * Formsperrsignal mit Balkenscheibe. Alle Masse als Anteil der Kantenlaenge.
 */
internal object FormSignals {
    private const val MAST = 0xFF7D848C.toInt()
    private const val RED = 0xFFD32F2F.toInt()
    private const val WHITE = 0xFFF4F4F0.toInt()
    private const val BLACK = 0xFF1B1E21.toInt()
    private const val YELLOW = 0xFFFBC02D.toInt()

    private val UP = 0.7071 to -0.7071
    private val RIGHT = 1.0 to 0.0
    private val DOWN = 0.0 to 1.0
    private val DOWN_RIGHT = 0.7071 to 0.7071

    fun render(aspect: SignalAspect, size: Int): PixelImage {
        val c = Sketch(size)
        val parts = aspect.form
        val shunt = parts.filterIsInstance<FormPart.ShuntDisc>().firstOrNull()
        val disc = parts.filterIsInstance<FormPart.Disc>().firstOrNull()
        val wing = parts.filterIsInstance<FormPart.Wing>().firstOrNull()
        when {
            shunt != null -> shunting(c, shunt)
            disc != null || wing != null -> distant(c, disc, wing)
            else -> main(c, parts.filterIsInstance<FormPart.Arm>())
        }
        return PixelImage(size, c.px)
    }

    private fun main(c: Sketch, arms: List<FormPart.Arm>) {
        c.rect(0.27, 0.36, 0.33, 1.0, MAST)
        c.dot(0.30, 0.36, 0.04, MAST)
        for (arm in arms) {
            val y = if (arm.slot == 0) 0.42 else 0.68
            when (arm.position) {
                ArmPosition.HORIZONTAL -> mainArm(c, 0.30, y, RIGHT, 0.5)
                ArmPosition.UP -> mainArm(c, 0.30, y, UP, 0.5)
                ArmPosition.HANGING -> mainArm(c, 0.37, y, DOWN, 0.25)
            }
        }
    }

    /** Fluegel weiss mit rotem Rand und runder Kelle am Ende. */
    private fun mainArm(c: Sketch, x: Double, y: Double, dir: Pair<Double, Double>, length: Double) {
        val (dx, dy) = dir
        val ex = x + dx * length
        val ey = y + dy * length
        c.line(x, y, ex, ey, 0.055, RED)
        c.line(x + dx * 0.08, y + dy * 0.08, ex - dx * 0.07, ey - dy * 0.07, 0.022, WHITE)
        c.dot(ex, ey, 0.09, RED)
        c.dot(ex, ey, 0.045, WHITE)
    }

    private fun distant(c: Sketch, disc: FormPart.Disc?, wing: FormPart.Wing?) {
        c.rect(0.47, 0.30, 0.53, 1.0, MAST)
        if (disc != null) {
            if (disc.facing) {
                c.dot(0.5, 0.30, 0.25, WHITE)
                c.dot(0.5, 0.30, 0.215, BLACK)
                c.dot(0.5, 0.30, 0.18, YELLOW)
            } else {
                c.rect(0.25, 0.27, 0.75, 0.35, BLACK)
                c.rect(0.27, 0.29, 0.73, 0.33, YELLOW)
            }
        }
        if (wing != null) {
            val (x, y) = 0.5 to 0.62
            if (wing.diagonal) {
                c.line(x, y, x + DOWN_RIGHT.first * 0.42, y + DOWN_RIGHT.second * 0.42, 0.06, BLACK)
                c.line(x + 0.03, y + 0.03, x + DOWN_RIGHT.first * 0.38, y + DOWN_RIGHT.second * 0.38, 0.03, YELLOW)
            } else {
                c.line(0.57, y, 0.57, y + 0.30, 0.05, BLACK)
                c.line(0.57, y + 0.03, 0.57, y + 0.27, 0.02, YELLOW)
            }
        }
    }

    private fun shunting(c: Sketch, disc: FormPart.ShuntDisc) {
        c.rect(0.44, 0.75, 0.56, 1.0, MAST)
        c.dot(0.5, 0.45, 0.38, BLACK)
        c.dot(0.5, 0.45, 0.33, WHITE)
        if (disc.clear) {
            c.line(0.31, 0.64, 0.69, 0.26, 0.06, BLACK)
        } else {
            c.line(0.24, 0.45, 0.76, 0.45, 0.06, BLACK)
        }
    }
}

/** Kleine Zeichenflaeche mit Koordinaten von 0 bis 1. */
private class Sketch(val n: Int) {
    val px = IntArray(n * n)

    fun dot(x: Double, y: Double, r: Double, color: Int) {
        val cx = x * n
        val cy = y * n
        val rr = max(r * n, 0.5)
        val x0 = (cx - rr).toInt().coerceAtLeast(0)
        val x1 = (cx + rr).toInt().coerceAtMost(n - 1)
        val y0 = (cy - rr).toInt().coerceAtLeast(0)
        val y1 = (cy + rr).toInt().coerceAtMost(n - 1)
        for (py in y0..y1) for (pxi in x0..x1) {
            val dx = pxi + 0.5 - cx
            val dy = py + 0.5 - cy
            if (sqrt(dx * dx + dy * dy) <= rr) px[py * n + pxi] = color
        }
    }

    /** Linie mit halber Breite [half], aus Punkten zusammengesetzt. */
    fun line(x0: Double, y0: Double, x1: Double, y1: Double, half: Double, color: Int) {
        val len = sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0)) * n
        val steps = max(1, (len * 3).toInt())
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            dot(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, half, color)
        }
    }

    fun rect(x0: Double, y0: Double, x1: Double, y1: Double, color: Int) {
        val l = (x0 * n).toInt().coerceIn(0, n - 1)
        val r = max((x1 * n).toInt() - 1, l).coerceIn(0, n - 1)
        val t = (y0 * n).toInt().coerceIn(0, n - 1)
        val b = max((y1 * n).toInt() - 1, t).coerceIn(0, n - 1)
        for (y in t..b) for (x in l..r) px[y * n + x] = color
    }
}
