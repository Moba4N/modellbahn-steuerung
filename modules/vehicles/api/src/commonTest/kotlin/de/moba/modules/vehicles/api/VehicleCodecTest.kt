package de.moba.modules.vehicles.api

import de.moba.core.model.LocoDynamics
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
                LocoRecord("lok-2", "V 100", 5, gauge = Gauge.H0M, consist = listOf("gw-1", "pw-1")),
            ),
            wagons = listOf(
                WagonClass("gw-1", VehicleGroup.FREIGHT, "Eaos", "Hochbordwagen", WagonCategory.OPEN, "DB", Epoch.IV, 162, 6),
                WagonClass("pw-1", VehicleGroup.PASSENGER, "Bm 235", category = WagonCategory.SECOND, count = 4),
            ),
        )
        assertEquals(fleet, VehicleCodec.decode(VehicleCodec.encode(fleet)))
    }

    @Test
    fun keepsDrivingPropertiesAndReadsOldFiles() {
        val dynamics = LocoDynamics(
            cab = true, inertia = true, accelerationSeconds = 12.5, brakingSeconds = 9.0, periodMs = 200,
            feedbackOffsetMm = 80, reactionDelayMs = 120,
        )
        val fleet = Fleet(locos = listOf(LocoRecord("fz-lok-1", "BR 218", 3, dynamics = dynamics), LocoRecord("fz-lok-2", "V 100", 5)))
        val back = VehicleCodec.decode(VehicleCodec.encode(fleet))
        assertEquals(dynamics, back.locos[0].dynamics)
        assertEquals(LocoDynamics(), back.locos[1].dynamics)
        // Unsinnige Werte werden beim Lesen begrenzt
        val wild = VehicleCodec.decode("MOBA-FAHRZEUGE 1\nlok\tl\tLok\t3\nfahr\tl\t0\t1\t999\t-4\t1\t99999\t-1\n")
        val d = wild.locos.single().dynamics
        assertEquals(LocoDynamics.MAX_SECONDS, d.accelerationSeconds)
        assertEquals(0.0, d.brakingSeconds)
        assertEquals(LocoDynamics.MIN_PERIOD_MS, d.periodMs)
        assertEquals(LocoDynamics.MAX_OFFSET_MM, d.feedbackOffsetMm)
        assertEquals(0, d.reactionDelayMs)
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
