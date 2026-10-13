package de.moba.modules.trackplan.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackPlanTest {
    @Test
    fun rotationTurnsAllConnections() {
        assertEquals(listOf(TrackSegment(2, 6)), TrackShape.STRAIGHT.segments(2))
        assertEquals(listOf(TrackSegment(7, 4)), TrackShape.CURVE.segments(7))
        assertEquals(listOf(TrackSegment(6, TrackDir.CENTER)), TrackShape.BUFFER_STOP.segments(2))
    }

    @Test
    fun putReplacesAndIgnoresOutside() {
        val plan = TrackPlan("p", "Test", width = 5, height = 5)
            .put(TrackTile(1, 1, TrackShape.STRAIGHT))
            .put(TrackTile(1, 1, TrackShape.CURVE))
            .put(TrackTile(9, 9, TrackShape.STRAIGHT))
        assertEquals(1, plan.tiles.size)
        assertEquals(TrackShape.CURVE, plan.tileAt(1, 1)!!.shape)
        assertEquals(7, plan.rotate(1, 1, -1).tileAt(1, 1)!!.rotation)
        assertNull(plan.remove(1, 1).tileAt(1, 1))
        assertEquals(4, plan.resize(1, 1).width) // Mindestgroesse
        val big = TrackPlan("q", "Gross", 10, 10).put(TrackTile(8, 8, TrackShape.STRAIGHT))
        assertEquals(0, big.resize(5, 5).tiles.size)
    }

    @Test
    fun activeBranchFollowsReportedTurnouts() {
        val left = TrackTile(0, 0, TrackShape.TURNOUT_LEFT, address = 5)
        assertNull(left.activeBranch(emptyMap()))
        assertEquals(Branch.LEFT, left.activeBranch(mapOf(5 to true)))
        assertEquals(Branch.MAIN, left.activeBranch(mapOf(5 to false)))

        val threeWay = TrackTile(0, 0, TrackShape.TURNOUT_THREE_WAY, address = 7)
        assertEquals(Branch.RIGHT, threeWay.activeBranch(mapOf(7 to false, 8 to true)))
        assertEquals(Branch.MAIN, threeWay.activeBranch(mapOf(7 to false, 8 to false)))
    }

    @Test
    fun codecRoundTripKeepsEverything() {
        val plans = listOf(
            TrackPlan(
                "a", "Bf.\tMitte\nNord", 30, 12,
                listOf(
                    TrackTile(3, 4, TrackShape.TURNOUT_THREE_WAY, 2, address = 12),
                    TrackTile(5, 4, TrackShape.FEEDBACK, 6, feedbackModule = 2, feedbackContact = 16),
                    TrackTile(0, 0, TrackShape.TEXT, text = "Gleis 1 \\ 2"),
                ),
            ),
            TrackPlan("b", "Leer"),
        )
        val back = TrackPlanCodec.decode(TrackPlanCodec.encode(plans))
        assertEquals(plans.map { it.copy(tiles = it.tiles.toSet().toList()) }.map { it.tiles.toSet() }, back.map { it.tiles.toSet() })
        assertEquals(plans.map { it.copy(tiles = emptyList()) }, back.map { it.copy(tiles = emptyList()) })
    }

    @Test
    fun fitToGrowsAndShrinksWithoutLosingTracks() {
        val plan = TrackPlan("p", "Test", 40, 24)
            .put(TrackTile(30, 10, TrackShape.STRAIGHT))
            .put(TrackTile(5, 20, TrackShape.STRAIGHT))
        val grown = plan.fitTo(60, 30)
        assertEquals(60, grown.width)
        assertEquals(30, grown.height)
        val shrunk = plan.fitTo(12, 8)
        assertEquals(31, shrunk.width)
        assertEquals(21, shrunk.height)
        assertEquals(2, shrunk.tiles.size)
        assertEquals(TrackPlan.MIN_SIZE, TrackPlan("leer", "Leer").fitTo(1, 1).width)
    }
}
