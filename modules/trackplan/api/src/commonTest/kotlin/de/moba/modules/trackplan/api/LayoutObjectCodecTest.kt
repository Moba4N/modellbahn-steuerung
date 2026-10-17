package de.moba.modules.trackplan.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayoutObjectCodecTest {
    @Test
    fun roundTripAndBoosterState() {
        val objects = LayoutObjects(
            stations = listOf(StationDef("bf-1", "Neustadt\tHbf", "NS", listOf(1 to 3, 1 to 4)), StationDef("bf-2", "Ohne Blöcke")),
            boosters = listOf(BoosterDef("bo-1", "Booster Nord", 90, 4 to 16, listOf(1 to 3)), BoosterDef("bo-2", "Süd")),
        )
        val back = LayoutObjectCodec.decode(LayoutObjectCodec.encode(objects))
        assertEquals(objects.copy(stations = listOf(objects.stations[0].copy(name = "Neustadt Hbf"), objects.stations[1])), back)
        val b = objects.boosters[0]
        assertTrue(b.isOn(emptySet(), emptyMap()))
        assertFalse(b.isOn(setOf(4 to 16), emptyMap()))
        assertFalse(b.isOn(emptySet(), mapOf(90 to true)))
    }
}
