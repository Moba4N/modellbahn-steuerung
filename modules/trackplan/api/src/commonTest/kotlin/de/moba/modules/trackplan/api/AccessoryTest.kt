package de.moba.modules.trackplan.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessoryTest {
    @Test
    fun settingsSurviveSavingWithSpecialCharacters() {
        val crossing = TrackTile(3, 2, TrackShape.LEVEL_CROSSING, address = 30)
            .withProp(AccessoryProps.NAME, "BÜ a=b; c\\d\tx")
            .withProp(AccessoryProps.ON_FEEDBACK, "1.3, 1.4")
        val table = TrackTile(10, 10, TrackShape.TURNTABLE, address = 225)
            .withProp(AccessoryProps.POSITIONS, "48")
            .withProp(AccessoryProps.CMD_TURN, "241:r")
        val plain = TrackTile(0, 0, TrackShape.STRAIGHT)
        val plan = TrackPlan("p", "Test", tiles = listOf(crossing, table, plain))

        val text = TrackPlanCodec.encode(listOf(plan))
        val back = TrackPlanCodec.decode(text).single()
        assertEquals(plan.tiles.toSet(), back.tiles.toSet())
        // Kacheln ohne Einstellungen bleiben im alten Format
        assertTrue(text.lines().first { it.startsWith("tile\t0\t0") }.split('\t').size == 10)
    }

    @Test
    fun oldFilesWithoutSettingsStillLoad() {
        val text = "MOBA-GLEISBILD 1\nplan\tp\tAlt\t10\t10\ntile\t1\t1\tSTRAIGHT\t0\t\t\t\t\t\n"
        assertEquals(emptyMap(), TrackPlanCodec.decode(text).single().tiles.single().props)
    }

    @Test
    fun feedbackListsAndCommandsParse() {
        assertEquals(listOf(1 to 3, 2 to 16), FeedbackList.parse("1.3, 2.16, x, 0.4, 1.3"))
        assertEquals("1.3, 2.16", FeedbackList.format(listOf(1 to 3, 2 to 16)))
        assertEquals(AccessoryCommand(241, true), AccessoryCommand.parse("241"))
        assertEquals(AccessoryCommand(242, false), AccessoryCommand.parse("242:g"))
        assertNull(AccessoryCommand.parse("242:blau"))
        assertEquals("242:g", AccessoryCommand(242, false).encode())
    }

    @Test
    fun onOffFollowsDecoderSetting() {
        val light = TrackTile(0, 0, TrackShape.LIGHT, address = 5)
        assertTrue(light.isOn(mapOf(5 to true)))
        assertFalse(light.isOn(emptyMap()))
        val inverted = light.withProp(AccessoryProps.INVERT, "1")
        assertTrue(inverted.isOn(mapOf(5 to false)))
        assertEquals(false, inverted.outputFor(true))
        assertEquals(listOf("Aus", "Ein"), light.aspectNames())
        assertEquals(500L, TrackTile(0, 0, TrackShape.UNCOUPLER).pulseMillis())
        assertNull(light.pulseMillis())
    }

    @Test
    fun turntableAddressesAndFootprint() {
        val table = TrackTile(10, 10, TrackShape.TURNTABLE, address = 225)
            .withProp(AccessoryProps.POSITIONS, "24")
            .withProp(AccessoryProps.SIZE, "3")
        val c = TableConfig.of(table)
        assertEquals(listOf(0, 12), c.tracks)
        assertEquals(AccessoryCommand(226, false), c.output(225, 3))
        assertEquals(3, c.positionFor(225, 226, false))
        assertNull(c.positionFor(225, 240, true))
        assertEquals(Footprint(7, 7, 7, 7), table.footprint())
        val plan = TrackPlan("p", "P", tiles = listOf(table))
        assertEquals(table, plan.objectAt(8, 12))
        assertNull(plan.objectAt(6, 10))

        val single = TableConfig.of(table.withProp(AccessoryProps.MODE, TableConfig.MODE_SINGLE))
        assertEquals(AccessoryCommand(228, false), single.output(225, 3))
        assertEquals(3, single.positionFor(225, 228, false))

        val transfer = TrackTile(2, 2, TrackShape.TRANSFER_TABLE, rotation = 2, address = 50)
            .withProp(AccessoryProps.POSITIONS, "5")
        assertEquals(Footprint(2, 2, 5, 4), transfer.footprint())
        assertEquals(listOf(0, 1, 2, 3, 4), TableConfig.of(transfer).tracks)
    }
}
