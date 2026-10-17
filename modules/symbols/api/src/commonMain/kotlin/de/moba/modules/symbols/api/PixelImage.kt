package de.moba.modules.symbols.api

import kotlin.math.abs
import kotlin.math.sqrt

/** Ein durchsichtiger Bildpunkt. */
const val TRANSPARENT = 0

/**
 * Quadratisches Pixelbild eines Symbols, wie im Symbol-Editor von TrainController.
 * Farben als ARGB (0xAARRGGBB), [TRANSPARENT] = leer. Unveraenderlich: jede Aenderung liefert ein neues Bild.
 */
class PixelImage(val size: Int, pixels: IntArray = IntArray(size * size)) {
    private val data: IntArray = pixels.copyOf()

    init {
        require(size in SIZES) { "Symbolgröße $size nicht erlaubt, nur $SIZES" }
        require(data.size == size * size)
    }

    val pixels: IntArray get() = data.copyOf()

    operator fun get(x: Int, y: Int): Int = if (inside(x, y)) data[y * size + x] else TRANSPARENT

    fun inside(x: Int, y: Int) = x in 0 until size && y in 0 until size

    val isEmpty: Boolean get() = data.all { it == TRANSPARENT }

    private inline fun edit(block: (IntArray) -> Unit): PixelImage {
        val copy = data.copyOf()
        block(copy)
        return PixelImage(size, copy)
    }

    private fun IntArray.put(x: Int, y: Int, color: Int) {
        if (inside(x, y)) this[y * this@PixelImage.size + x] = color
    }

    fun set(x: Int, y: Int, color: Int): PixelImage = edit { it.put(x, y, color) }

    fun clear(): PixelImage = PixelImage(size)

    /** Linie von (x0, y0) nach (x1, y1), Bresenham. */
    fun line(x0: Int, y0: Int, x1: Int, y1: Int, color: Int): PixelImage = edit { d ->
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            d.put(x, y, color)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    fun rect(x0: Int, y0: Int, x1: Int, y1: Int, color: Int, filled: Boolean): PixelImage = edit { d ->
        val (l, r) = minOf(x0, x1) to maxOf(x0, x1)
        val (t, b) = minOf(y0, y1) to maxOf(y0, y1)
        for (y in t..b) for (x in l..r) {
            if (filled || x == l || x == r || y == t || y == b) d.put(x, y, color)
        }
    }

    /** Ellipse im Rechteck (x0, y0)-(x1, y1). */
    fun ellipse(x0: Int, y0: Int, x1: Int, y1: Int, color: Int, filled: Boolean): PixelImage = edit { d ->
        val l = minOf(x0, x1)
        val r = maxOf(x0, x1)
        val t = minOf(y0, y1)
        val b = maxOf(y0, y1)
        val cx = (l + r) / 2.0
        val cy = (t + b) / 2.0
        val rx = (r - l) / 2.0 + 0.5
        val ry = (b - t) / 2.0 + 0.5
        for (y in t..b) for (x in l..r) {
            val nx = (x - cx) / rx
            val ny = (y - cy) / ry
            val dist = nx * nx + ny * ny
            if (dist > 1.0) continue
            if (filled) {
                d.put(x, y, color)
            } else {
                // Rand: ein Nachbar liegt ausserhalb
                val outside = listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1).any { (px, py) ->
                    val qx = (px - cx) / rx
                    val qy = (py - cy) / ry
                    qx * qx + qy * qy > 1.0
                }
                if (outside) d.put(x, y, color)
            }
        }
    }

    /** Fuellt die zusammenhaengende Flaeche gleicher Farbe ab (x, y). */
    fun fill(x: Int, y: Int, color: Int): PixelImage {
        if (!inside(x, y)) return this
        val target = this[x, y]
        if (target == color) return this
        return edit { d ->
            val stack = ArrayDeque<Int>()
            stack.addLast(y * size + x)
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                if (d[i] != target) continue
                d[i] = color
                val px = i % size
                val py = i / size
                if (px > 0) stack.addLast(i - 1)
                if (px < size - 1) stack.addLast(i + 1)
                if (py > 0) stack.addLast(i - size)
                if (py < size - 1) stack.addLast(i + size)
            }
        }
    }

    fun mirrorHorizontal(): PixelImage = map { x, y -> this[size - 1 - x, y] }
    fun mirrorVertical(): PixelImage = map { x, y -> this[x, size - 1 - y] }

    /** Dreht um 90 Grad im Uhrzeigersinn. */
    fun rotateRight(): PixelImage = map { x, y -> this[y, size - 1 - x] }

    /** Verschiebt um (dx, dy); was hinausfaellt, kommt auf der anderen Seite wieder herein. */
    fun shift(dx: Int, dy: Int): PixelImage = map { x, y -> this[(x - dx).mod(size), (y - dy).mod(size)] }

    /** Ersetzt eine Farbe durch eine andere, z. B. um ein Signal von Rot auf Gruen umzufaerben. */
    fun replace(from: Int, to: Int): PixelImage = map { x, y -> this[x, y].let { if (it == from) to else it } }

    /** Skaliert auf eine andere Groesse (naechster Nachbar). */
    fun resized(newSize: Int): PixelImage {
        if (newSize == size) return this
        val out = IntArray(newSize * newSize)
        for (y in 0 until newSize) for (x in 0 until newSize) {
            out[y * newSize + x] = this[x * size / newSize, y * size / newSize]
        }
        return PixelImage(newSize, out)
    }

    private inline fun map(pixel: (Int, Int) -> Int): PixelImage {
        val out = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) out[y * size + x] = pixel(x, y)
        return PixelImage(size, out)
    }

    override fun equals(other: Any?) = other is PixelImage && other.size == size && other.data.contentEquals(data)
    override fun hashCode() = 31 * size + data.contentHashCode()

    companion object {
        /** Erlaubte Kantenlaengen in Pixeln. */
        val SIZES = listOf(16, 24, 32, 48)
        const val DEFAULT_SIZE = 32

        /** Kreisscheibe als Hilfe fuer Signallampen. */
        fun disc(image: PixelImage, cx: Double, cy: Double, radius: Double, color: Int): PixelImage {
            val out = image.pixels
            val s = image.size
            for (y in 0 until s) for (x in 0 until s) {
                val dx = x + 0.5 - cx
                val dy = y + 0.5 - cy
                if (sqrt(dx * dx + dy * dy) <= radius) out[y * s + x] = color
            }
            return PixelImage(s, out)
        }
    }
}
