package de.moba.modules.calibration.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalibrationTest {
    @Test
    fun speedStepsMapOntoTheStationScale() {
        assertEquals(126, SpeedStepMode.STEPS_14.toStationSpeed(14))
        assertEquals(9, SpeedStepMode.STEPS_14.toStationSpeed(1))
        assertEquals(63, SpeedStepMode.STEPS_28.toStationSpeed(14))
        assertEquals(40, SpeedStepMode.STEPS_128.toStationSpeed(40))
        assertEquals((1..14).toList(), SpeedStepMode.STEPS_14.measurePoints())
        assertEquals(28, SpeedStepMode.STEPS_28.measurePoints().size)
        val points128 = SpeedStepMode.STEPS_128.measurePoints()
        assertEquals(8, points128.first())
        assertEquals(126, points128.last())
        assertEquals(16, points128.size)
    }

    @Test
    fun scaleSpeedFromTime() {
        // 1 m in 10 s in H0: 100 mm/s * 87 = 8,7 m/s = 31,32 km/h
        assertEquals(31.32, SpeedMath.scaleKmh(1000, 10_000, Scale.H0), 0.001)
    }

    @Test
    fun stepForSpeedInterpolates() {
        val p = SpeedProfile(3, "BR 218", SpeedStepMode.STEPS_28, Scale.H0, listOf(SpeedPoint(1, null), SpeedPoint(10, 40.0), SpeedPoint(20, 100.0)))
        assertEquals(5, p.stepFor(20.0))
        assertEquals(15, p.stepFor(70.0))
        assertNull(p.stepFor(150.0))
        assertEquals(100.0, p.maxKmh)
    }

    @Test
    fun codecRoundTrip() {
        val profiles = listOf(
            SpeedProfile(3, "BR\t218", SpeedStepMode.STEPS_128, Scale.N, listOf(SpeedPoint(8, 12.34), SpeedPoint(16, null)), "2026-10-14"),
        )
        val back = CalibrationCodec.decode(CalibrationCodec.encode(profiles))
        assertEquals(1, back.size)
        assertEquals("BR 218", back[0].name)
        assertEquals(SpeedStepMode.STEPS_128, back[0].mode)
        assertEquals(Scale.N, back[0].scale)
        assertEquals(listOf(SpeedPoint(8, 12.3), SpeedPoint(16, null)), back[0].points)
    }
}
