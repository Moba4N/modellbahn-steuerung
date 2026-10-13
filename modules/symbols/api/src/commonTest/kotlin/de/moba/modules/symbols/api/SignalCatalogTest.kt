package de.moba.modules.symbols.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignalCatalogTest {
    @Test
    fun onlyGermanySwitzerlandAndNetherlands() {
        assertEquals(setOf("DE", "CH", "NL"), SignalCatalog.countries.toSet())
    }

    @Test
    fun everyCountryHasSemaphores() {
        for (country in SignalCatalog.countries) {
            assertTrue(SignalCatalog.forCountry(country).any { it.semaphore }, country)
        }
    }

    @Test
    fun semaphoreArmMovesBetweenStopAndClear() {
        val hp = SignalCatalog.system("de-form-hp2")!!
        val stop = SignalSymbols.render(hp, hp.stop, 32)
        val clear = SignalSymbols.render(hp, hp.clear, 32)
        assertTrue(stop != clear)
        assertEquals("Hp 1", hp.clear.name)
    }

    @Test
    fun idsAreUniqueAndStopComesFirst() {
        val systems = SignalCatalog.systems
        assertEquals(systems.size, systems.map { it.id }.toSet().size)
        for (s in systems) {
            assertTrue(s.aspects.size >= 2, s.id)
            assertTrue(s.clear != s.stop, s.id)
            assertTrue(s.clear.lit.none { it.color == LampColor.RED } || s.kind == SignalKind.SHUNTING, s.id)
        }
    }

    @Test
    fun everySystemBecomesAnEditableSymbol() {
        for (s in SignalCatalog.systems) {
            val symbol = SignalSymbols.toSymbol(s, "x", 32)
            assertEquals(s.aspects.size, symbol.states.size)
            assertTrue(symbol.states.all { !it.image.isEmpty }, s.id)
        }
    }
}
