package de.moba.modules.trackplan.api

/**
 * Richtungen auf einer Rasterkachel, im Uhrzeigersinn ab Norden:
 * gerade Zahlen sind Kantenmitten (0 = oben, 2 = rechts, 4 = unten, 6 = links),
 * ungerade die Ecken (1 = oben rechts, 3 = unten rechts, 5 = unten links, 7 = oben links).
 * Ein Gleis verbindet zwei Richtungen ueber die Kachelmitte; [CENTER] ist ein Gleisende.
 */
object TrackDir {
    const val CENTER = -1

    fun rotate(dir: Int, steps: Int) = if (dir == CENTER) CENTER else (dir + steps).mod(8)
}

/** Ein Gleisstueck auf der Kachel. [branch] markiert den abzweigenden Strang einer Weiche. */
data class TrackSegment(val from: Int, val to: Int, val branch: Branch = Branch.MAIN) {
    fun rotated(steps: Int) = TrackSegment(TrackDir.rotate(from, steps), TrackDir.rotate(to, steps), branch)
}

/** Welcher Weichenstellung ein Gleisstueck angehoert. */
enum class Branch { MAIN, LEFT, RIGHT }

/** Was eine Kachel ausser Gleis noch traegt. */
enum class TileKind { TRACK, TURNOUT, SIGNAL, FEEDBACK, UNCOUPLER, BUFFER, TEXT, SYMBOL }

/**
 * Alle Gleisformen des Gleisbilds. Die Segmente gelten fuer Drehung 0;
 * jede Form laesst sich in 45-Grad-Schritten drehen.
 */
enum class TrackShape(val label: String, val kind: TileKind, val segments: List<TrackSegment>) {
    STRAIGHT("Gerade", TileKind.TRACK, listOf(TrackSegment(0, 4))),
    CURVE("Kurve 45°", TileKind.TRACK, listOf(TrackSegment(0, 5))),
    CURVE_90("Bogen 90°", TileKind.TRACK, listOf(TrackSegment(0, 2))),
    TURNOUT_LEFT(
        "Weiche links", TileKind.TURNOUT,
        listOf(TrackSegment(4, 0), TrackSegment(4, 7, Branch.LEFT)),
    ),
    TURNOUT_RIGHT(
        "Weiche rechts", TileKind.TURNOUT,
        listOf(TrackSegment(4, 0), TrackSegment(4, 1, Branch.RIGHT)),
    ),
    TURNOUT_Y(
        "Y-Weiche", TileKind.TURNOUT,
        listOf(TrackSegment(4, 7), TrackSegment(4, 1, Branch.RIGHT)),
    ),
    TURNOUT_THREE_WAY(
        "Dreiwegweiche", TileKind.TURNOUT,
        listOf(TrackSegment(4, 0), TrackSegment(4, 7, Branch.LEFT), TrackSegment(4, 1, Branch.RIGHT)),
    ),
    CROSSING(
        "Kreuzung 90°", TileKind.TRACK,
        listOf(TrackSegment(0, 4), TrackSegment(2, 6)),
    ),
    CROSSING_45(
        "Kreuzung 45°", TileKind.TRACK,
        listOf(TrackSegment(0, 4), TrackSegment(1, 5)),
    ),
    SINGLE_SLIP(
        "Einfache Kreuzungsweiche", TileKind.TURNOUT,
        listOf(TrackSegment(0, 4), TrackSegment(1, 5), TrackSegment(4, 1, Branch.RIGHT)),
    ),
    DOUBLE_SLIP(
        "Doppelte Kreuzungsweiche", TileKind.TURNOUT,
        listOf(
            TrackSegment(0, 4), TrackSegment(1, 5),
            TrackSegment(4, 1, Branch.RIGHT), TrackSegment(0, 5, Branch.RIGHT),
        ),
    ),
    BUFFER_STOP("Prellbock", TileKind.BUFFER, listOf(TrackSegment(4, TrackDir.CENTER))),
    SIGNAL("Signal", TileKind.SIGNAL, listOf(TrackSegment(0, 4))),
    FEEDBACK("Rückmelder", TileKind.FEEDBACK, listOf(TrackSegment(0, 4))),
    UNCOUPLER("Entkuppler", TileKind.UNCOUPLER, listOf(TrackSegment(0, 4))),
    TEXT("Text", TileKind.TEXT, emptyList()),
    SYMBOL("Symbol", TileKind.SYMBOL, emptyList()),
    ;

    /** Braucht eine Digitaladresse (Weichen, Signale, Entkuppler); bei Symbolen schaltet sie den Zustand. */
    val hasAddress: Boolean get() = kind == TileKind.TURNOUT || kind == TileKind.SIGNAL || kind == TileKind.UNCOUPLER || kind == TileKind.SYMBOL

    /** Dreiwegweichen brauchen zwei Adressen: [address] fuer links, [address] + 1 fuer rechts. */
    val addressCount: Int get() = if (this == TURNOUT_THREE_WAY) 2 else 1

    fun segments(rotation: Int) = segments.map { it.rotated(rotation) }
}

/**
 * Eine belegte Rasterzelle. [rotation] in 45-Grad-Schritten (0 bis 7).
 * [feedbackModule]/[feedbackContact] verbinden einen Rueckmelder mit s88-Modul und Kontakt.
 * [symbol] verweist auf ein eigenes Symbol (Kennung) oder bei Signalen auf ein Signalsystem ("signal:<kennung>").
 */
data class TrackTile(
    val x: Int,
    val y: Int,
    val shape: TrackShape,
    val rotation: Int = 0,
    val address: Int? = null,
    val feedbackModule: Int? = null,
    val feedbackContact: Int? = null,
    val text: String = "",
    val symbol: String = "",
) {
    fun segments() = shape.segments(rotation)
}

/** Ein Gleisbild: Name, Rastergroesse und Kacheln. */
data class TrackPlan(
    val id: String,
    val name: String,
    val width: Int = DEFAULT_WIDTH,
    val height: Int = DEFAULT_HEIGHT,
    val tiles: List<TrackTile> = emptyList(),
) {
    fun tileAt(x: Int, y: Int): TrackTile? = tiles.firstOrNull { it.x == x && it.y == y }

    /** Setzt oder ersetzt die Kachel an ihrer Position. Ausserhalb des Rasters passiert nichts. */
    fun put(tile: TrackTile): TrackPlan {
        if (tile.x !in 0 until width || tile.y !in 0 until height) return this
        return copy(tiles = tiles.filterNot { it.x == tile.x && it.y == tile.y } + tile)
    }

    fun remove(x: Int, y: Int) = copy(tiles = tiles.filterNot { it.x == x && it.y == y })

    /** Dreht die Kachel um [steps] mal 45 Grad im Uhrzeigersinn. */
    fun rotate(x: Int, y: Int, steps: Int = 1): TrackPlan {
        val tile = tileAt(x, y) ?: return this
        return put(tile.copy(rotation = (tile.rotation + steps).mod(8)))
    }

    /** Neue Groesse; Kacheln ausserhalb fallen weg. */
    fun resize(newWidth: Int, newHeight: Int): TrackPlan {
        val w = newWidth.coerceIn(MIN_SIZE, MAX_SIZE)
        val h = newHeight.coerceIn(MIN_SIZE, MAX_SIZE)
        return copy(width = w, height = h, tiles = tiles.filter { it.x < w && it.y < h })
    }

    /**
     * Passt das Raster an eine Flaeche von [columns] x [rows] Kacheln an, z. B. an das Fenster.
     * Anders als [resize] fallen dabei nie Gleise weg: das Raster bleibt mindestens so gross wie der Plan.
     */
    fun fitTo(columns: Int, rows: Int): TrackPlan {
        val usedWidth = (tiles.maxOfOrNull { it.x } ?: -1) + 1
        val usedHeight = (tiles.maxOfOrNull { it.y } ?: -1) + 1
        return resize(maxOf(columns, usedWidth), maxOf(rows, usedHeight))
    }

    companion object {
        const val DEFAULT_WIDTH = 40
        const val DEFAULT_HEIGHT = 24
        const val MIN_SIZE = 4
        const val MAX_SIZE = 200
    }
}

/**
 * Welcher Strang einer Weiche gerade befahren wird, nach den gemeldeten Stellungen
 * ([turnouts]: Adresse zu abzweigend). Null, solange die Zentrale nichts gemeldet hat.
 */
fun TrackTile.activeBranch(turnouts: Map<Int, Boolean>): Branch? {
    if (shape.kind != TileKind.TURNOUT) return null
    val a = address ?: return null
    return when (shape) {
        TrackShape.TURNOUT_THREE_WAY -> {
            val left = turnouts[a]
            val right = turnouts[a + 1]
            when {
                left == null && right == null -> null
                left == true -> Branch.LEFT
                right == true -> Branch.RIGHT
                else -> Branch.MAIN
            }
        }
        TrackShape.TURNOUT_LEFT -> turnouts[a]?.let { if (it) Branch.LEFT else Branch.MAIN }
        else -> turnouts[a]?.let { if (it) Branch.RIGHT else Branch.MAIN }
    }
}
