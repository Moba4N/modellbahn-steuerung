package de.moba.modules.vehicles.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VehicleCodecTest {
    @Test
    fun roundTripKeepsAllFields() {
        val fleet = Fleet(
            locos = listOf(
                LocoRecord(
                    "lok-1", "BR 218 \"Tab\tTest\"", 3, "BR 218", "DB", Epoch.IV, Traction.DIESEL, DecoderProtocol.MFX,
                    "Roco", "73712", 140, 189, mapOf(0 to "Licht", 1 to "Sound"), "Zeile 1\nZeile 2",
                ),
                LocoRecord("lok-2", "V 100", 5),
            ),
            wagons = listOf(
                WagonClass("gw-1", VehicleGroup.FREIGHT, "Eaos", "Hochbordwagen", WagonCategory.OPEN, "DB", Epoch.IV, 162, 6),
                WagonClass("pw-1", VehicleGroup.PASSENGER, "Bm 235", category = WagonCategory.SECOND, count = 4),
            ),
        )
        assertEquals(fleet, VehicleCodec.decode(VehicleCodec.encode(fleet)))
    }

    @Test
    fun skipsBrokenLines() {
        val text = "MOBA-FAHRZEUGE 1\nlok\tx\tOhne Adresse\t\nwagen\tw\tLOCOS\tFalsch\nlok\tok\tGut\t7\n"
        val fleet = VehicleCodec.decode(text)
        assertEquals(listOf("ok"), fleet.locos.map { it.id })
        assertTrue(fleet.wagons.isEmpty())
    }

    @Test
    fun templatesMatchTheirGroup() {
        for (group in listOf(VehicleGroup.FREIGHT, VehicleGroup.PASSENGER)) {
            assertTrue(WagonTemplates.of(group).all { it.group == group && it.category?.group == group })
        }
    }
}
