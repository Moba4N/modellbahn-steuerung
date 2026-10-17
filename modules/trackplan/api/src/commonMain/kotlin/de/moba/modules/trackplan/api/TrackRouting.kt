package de.moba.modules.trackplan.api

/** Eine Rasterzelle im Gleisbild. */
data class Cell(val x: Int, val y: Int)

/**
 * Ein befahrbarer Weg durch das Gleisbild von einer Kachel zur anderen.
 * [settings] sind die noetigen Weichenstellungen (Adresse zu abzweigend) in Fahrtrichtung,
 * [feedbacks] die ueberfahrenen Rueckmelder als (s88-Modul, Kontakt) in Fahrtrichtung.
 */
data class TrackPath(
    val cells: List<Cell>,
    val settings: List<Pair<Int, Boolean>>,
    val feedbacks: List<Pair<Int, Int>>,
    /** Weichen auf dem Weg, die noch keine Adresse haben. */
    val missingAddresses: List<Cell> = emptyList(),
) {
    val turnoutCount: Int get() = settings.map { it.first }.distinct().size
}

/**
 * Sucht Wege im Gleisbild, so wie ein Zug fahren kann: von Kachel zu Kachel ueber die Gleisenden,
 * an Weichen ueber jeden Strang (aber jede Weiche nur in einer Stellung), an Kreuzungen geradeaus.
 */
object TrackRouting {
    /** Versatz zur Nachbarkachel je Richtung (0 = oben, im Uhrzeigersinn). */
    private val DX = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
    private val DY = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)

    /** Hoechstens so viele Teilwege werden untersucht, damit grosse Anlagen mit Schleifen schnell bleiben. */
    private const val MAX_EXPANSIONS = 50_000

    private class Partial(
        val tile: TrackTile,
        /** Richtung, aus der der Zug in [tile] einfaehrt. */
        val entry: Int,
        val cells: List<Cell>,
        val used: Set<Triple<Int, Int, Int>>,
        val branches: Map<Cell, Branch>,
    )

    /**
     * Bis zu [max] Wege von [from] nach [to], kuerzeste zuerst. Leer, wenn keine Verbindung besteht.
     * Der Zug darf die Startkachel in beide Richtungen verlassen.
     */
    fun findPaths(plan: TrackPlan, from: Cell, to: Cell, max: Int = 5): List<TrackPath> {
        val start = plan.tileAt(from.x, from.y) ?: return emptyList()
        if (plan.tileAt(to.x, to.y) == null || from == to) return emptyList()
        val results = mutableListOf<TrackPath>()
        val queue = ArrayDeque<Partial>()
        // Start: jedes Gleisstueck der Startkachel in beide Richtungen
        start.segments().forEachIndexed { i, seg ->
            for (exit in listOf(seg.to, seg.from)) {
                if (exit == TrackDir.CENTER) continue
                val branches = if (start.shape.kind == TileKind.TURNOUT) mapOf(from to seg.branch) else emptyMap()
                step(plan, start, exit, listOf(from), setOf(Triple(from.x, from.y, i)), branches)?.let(queue::addLast)
            }
        }
        var expansions = 0
        while (queue.isNotEmpty() && results.size < max && expansions < MAX_EXPANSIONS) {
            val p = queue.removeFirst()
            expansions++
            val here = Cell(p.tile.x, p.tile.y)
            if (here == to) {
                results += toPath(plan, p)
                continue
            }
            p.tile.segments().forEachIndexed { i, seg ->
                val exit = when (p.entry) {
                    seg.from -> seg.to
                    seg.to -> seg.from
                    else -> return@forEachIndexed
                }
                if (exit == TrackDir.CENTER) return@forEachIndexed
                val key = Triple(here.x, here.y, i)
                if (key in p.used) return@forEachIndexed
                var branches = p.branches
                if (p.tile.shape.kind == TileKind.TURNOUT) {
                    // Eine Weiche kann auf einem Weg nur eine Stellung haben
                    val before = branches[here]
                    if (before != null && before != seg.branch) return@forEachIndexed
                    branches = branches + (here to seg.branch)
                }
                step(plan, p.tile, exit, p.cells, p.used + key, branches)?.let(queue::addLast)
            }
        }
        return results
    }

    /** Faehrt aus [tile] in Richtung [exit] in die Nachbarkachel; null, wenn dort kein passendes Gleis anschliesst. */
    private fun step(
        plan: TrackPlan,
        tile: TrackTile,
        exit: Int,
        cells: List<Cell>,
        used: Set<Triple<Int, Int, Int>>,
        branches: Map<Cell, Branch>,
    ): Partial? {
        val next = plan.tileAt(tile.x + DX[exit], tile.y + DY[exit]) ?: return null
        val entry = (exit + 4) % 8
        if (next.segments().none { it.from == entry || it.to == entry }) return null
        return Partial(next, entry, cells + Cell(next.x, next.y), used, branches)
    }

    private fun toPath(plan: TrackPlan, p: Partial): TrackPath {
        val settings = mutableListOf<Pair<Int, Boolean>>()
        val missing = mutableListOf<Cell>()
        val feedbacks = mutableListOf<Pair<Int, Int>>()
        val seen = mutableSetOf<Cell>()
        for (cell in p.cells) {
            if (!seen.add(cell)) continue
            val tile = plan.tileAt(cell.x, cell.y) ?: continue
            if (tile.shape.kind == TileKind.FEEDBACK && tile.feedbackModule != null && tile.feedbackContact != null) {
                feedbacks += tile.feedbackModule to tile.feedbackContact
            }
            val branch = p.branches[cell] ?: continue
            if (tile.address == null) missing += cell else settings += settingsFor(tile, branch)
        }
        return TrackPath(p.cells, settings, feedbacks, missing)
    }

    /** Welche Adressen eine Weiche fuer [branch] braucht (true = abzweigend), passend zu [activeBranch]. */
    fun settingsFor(tile: TrackTile, branch: Branch): List<Pair<Int, Boolean>> {
        val a = tile.address ?: return emptyList()
        return when (tile.shape) {
            TrackShape.TURNOUT_THREE_WAY -> listOf(a to (branch == Branch.LEFT), (a + 1) to (branch == Branch.RIGHT))
            else -> listOf(a to (branch != Branch.MAIN))
        }
    }
}
