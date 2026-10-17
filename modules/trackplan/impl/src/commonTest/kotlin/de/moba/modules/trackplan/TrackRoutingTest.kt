package de.moba.modules.trackplan

import de.moba.modules.trackplan.api.Cell
import de.moba.modules.trackplan.api.TrackRouting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackRoutingTest {
    private val layout = DemoPlans.layout()

    @Test
    fun findsBothWaysAroundTheOvalShortestFirst() {
        // Von Melder 1.3 oben im Oval zum Ausweichgleis mit Melder 2.5
        val paths = TrackRouting.findPaths(layout, Cell(9, 2), Cell(10, 6))
        assertEquals(2, paths.size)
        val (short, long) = paths
        assertTrue(short.cells.size <= long.cells.size)
        assertEquals(Cell(9, 2), short.cells.first())
        assertEquals(Cell(10, 6), short.cells.last())
        // links herum: Weiche 12 abzweigend; rechts herum: Weiche 14 gerade und Weiche 13 abzweigend
        val ways = paths.map { it.settings.toSet() }.toSet()
        assertEquals(setOf(setOf(12 to true), setOf(14 to false, 13 to true)), ways)
        paths.forEach { assertEquals(listOf(1 to 3, 2 to 5), it.feedbacks) }
    }

    @Test
    fun throughTheMainLineAndIntoTheSiding() {
        // Melder 1.4 (unten) zum Abstellgleis mit Entkuppler: Weiche 14 muss abzweigen
        val paths = TrackRouting.findPaths(layout, Cell(12, 8), Cell(15, 1))
        assertTrue(paths.isNotEmpty())
        paths.forEach { assertTrue(14 to true in it.settings) }
    }

    @Test
    fun threeWayTurnoutSetsBothAddresses() {
        val plan = DemoPlans.industry()
        val left = TrackRouting.findPaths(plan, Cell(0, 3), Cell(7, 1)).first()
        assertEquals(listOf(40 to true, 41 to false), left.settings)
        val right = TrackRouting.findPaths(plan, Cell(0, 3), Cell(8, 5)).first()
        assertEquals(listOf(40 to false, 41 to true), right.settings)
        val straight = TrackRouting.findPaths(plan, Cell(0, 3), Cell(13, 3)).first()
        assertEquals(40 to false, straight.settings.first())
        assertEquals(41 to false, straight.settings[1])
    }

    @Test
    fun noConnectionGivesNothing() {
        val plan = DemoPlans.industry()
        assertTrue(TrackRouting.findPaths(plan, Cell(0, 3), Cell(1, 6)).isEmpty())
        assertTrue(TrackRouting.findPaths(plan, Cell(0, 3), Cell(0, 3)).isEmpty())
    }
}
