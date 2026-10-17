package de.moba.modules.symbols.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SymbolTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    @Test
    fun lineAndRect() {
        val img = PixelImage(16).line(0, 0, 15, 15, red)
        assertEquals(red, img[0, 0])
        assertEquals(red, img[7, 7])
        assertEquals(red, img[15, 15])
        assertEquals(TRANSPARENT, img[15, 0])
        val r = PixelImage(16).rect(2, 2, 5, 5, blue, filled = false)
        assertEquals(blue, r[2, 4])
        assertEquals(TRANSPARENT, r[3, 3])
        assertEquals(16, PixelImage(16).rect(5, 5, 2, 2, blue, filled = true).pixels.count { it == blue })
    }

    @Test
    fun fillStopsAtBorder() {
        val img = PixelImage(16).rect(0, 0, 7, 7, red, filled = false).fill(3, 3, blue)
        assertEquals(36, img.pixels.count { it == blue })
        assertEquals(TRANSPARENT, img[10, 10])
    }

    @Test
    fun transforms() {
        val img = PixelImage(16).set(1, 0, red)
        assertEquals(red, img.mirrorHorizontal()[14, 0])
        assertEquals(red, img.mirrorVertical()[1, 15])
        assertEquals(red, img.rotateRight()[15, 1])
        assertEquals(red, img.shift(-2, 0)[15, 0])
        assertEquals(blue, img.replace(red, blue)[1, 0])
        assertEquals(red, img.resized(32)[2, 0])
    }

    @Test
    fun ellipseIsSymmetric() {
        val img = PixelImage(16).ellipse(2, 2, 13, 13, red, filled = true)
        assertEquals(img, img.mirrorHorizontal())
        assertEquals(img, img.mirrorVertical())
        assertTrue(img.pixels.count { it == red } > 80)
    }

    @Test
    fun codecRoundTrip() {
        val symbols = listOf(
            SymbolDef(
                "sym-1", "Lampe\tmit Tab", SymbolCategory.ACCESSORY,
                listOf(SymbolState("aus", PixelImage(24)), SymbolState("an", PixelImage(24).ellipse(4, 4, 19, 19, red, true))),
            ),
            SymbolDef("sym-2", "Haus", SymbolCategory.BUILDING),
        )
        val text = SymbolCodec.encode(symbols)
        assertEquals(symbols, SymbolCodec.decode(text))
        assertTrue(text.length < 2000, "Lauflaengen-Kodierung sollte kurz sein: ${text.length}")
    }

    @Test
    fun statesMustMatchInSize() {
        assertFailsWith<IllegalArgumentException> {
            SymbolDef("x", "x", states = listOf(SymbolState("a", PixelImage(16)), SymbolState("b", PixelImage(32))))
        }
    }

    @Test
    fun signalSymbolHasOneStatePerAspect() {
        val system = SignalSystem(
            "xx-test", "DE", "Test", SignalKind.MAIN, 1, 2,
            listOf(Lamp(0, 0), Lamp(0, 1)),
            listOf(
                SignalAspect("hp0", "Hp 0", "Halt", listOf(LitLamp(0, 0, LampColor.RED))),
                SignalAspect("hp1", "Hp 1", "Fahrt", listOf(LitLamp(0, 1, LampColor.GREEN))),
            ),
        )
        val symbol = SignalSymbols.toSymbol(system, "s")
        assertEquals(2, symbol.states.size)
        assertTrue(LampColor.RED.argb in symbol.states[0].image.pixels)
        assertTrue(LampColor.GREEN.argb !in symbol.states[0].image.pixels)
        assertTrue(LampColor.GREEN.argb in symbol.states[1].image.pixels)
    }
}
