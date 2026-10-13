package de.moba.modules.decoder.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DecoderTest {
    @Test
    fun shortAndLongAddresses() {
        assertEquals(mapOf(1 to 3, 29 to 6), DecoderAddress.cvsFor(3, cv29 = 38))
        // 2018 = 0x07E2: CV 17 = 0xC7, CV 18 = 0xE2, Bit 5 in CV 29
        assertEquals(mapOf(17 to 0xC7, 18 to 0xE2, 29 to 38), DecoderAddress.cvsFor(2018, cv29 = 6))
        assertEquals(2018, DecoderAddress.from(mapOf(1 to 3, 17 to 0xC7, 18 to 0xE2, 29 to 38)))
        assertEquals(3, DecoderAddress.from(mapOf(1 to 3, 29 to 6)))
        assertFailsWith<IllegalArgumentException> { DecoderAddress.cvsFor(0, 6) }
    }

    @Test
    fun cv29Bits() {
        assertEquals(true, Cv29Bit.SPEED_STEPS.isSet(6))
        assertEquals(false, Cv29Bit.REVERSED.isSet(6))
        assertEquals(7, Cv29Bit.REVERSED.set(6, true))
        assertEquals(4, Cv29Bit.SPEED_STEPS.set(6, false))
    }

    @Test
    fun codecRoundTrip() {
        val profiles = listOf(
            DecoderProfile("p1", "BR 218\tSound", "BR 218", 3, "ESU LokSound 5", mapOf(1 to 3, 8 to 151, 29 to 6), "Werkseinstellung\nvon Roco"),
            DecoderProfile("p2", "Leer"),
        )
        assertEquals(profiles, DecoderCodec.decode(DecoderCodec.encode(profiles)))
        assertEquals("ESU", profiles[0].manufacturer)
    }
}
