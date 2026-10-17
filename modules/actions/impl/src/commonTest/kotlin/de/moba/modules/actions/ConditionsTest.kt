package de.moba.modules.actions

import de.moba.core.station.Direction
import de.moba.core.station.LocoState
import de.moba.modules.actions.api.Condition
import de.moba.modules.actions.api.ConditionType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConditionsTest {
    private val world = World(
        now = 60_000,
        modelMinutes = 23 * 60.0 + 30,
        wallMinutes = 8 * 60,
        connected = true,
        trackPower = true,
        feedback = setOf(1 to 1, 2 to 4),
        turnouts = mapOf(3 to true, 4 to false),
        reservedAddresses = setOf(3),
        reservedBlocks = setOf(1 to 2),
        trains = mapOf(2 to 4 to 7),
        locos = mapOf(7 to LocoState(7, 30, Direction.REVERSE, setOf(0, 2)), 8 to LocoState(8)),
        automatedLocos = setOf(7),
        runningAutomations = setOf("zr-1"),
    )

    private fun ok(type: ConditionType, vararg p: Pair<String, String>) = Conditions.eval(Condition.of(type, *p), world)

    @Test
    fun stationAndAccessoryStates() {
        assertTrue(ok(ConditionType.TIME, "von" to "22:00", "bis" to "06:00"))
        assertFalse(ok(ConditionType.TIME, "von" to "06:00", "bis" to "22:00"))
        assertTrue(ok(ConditionType.TIME, "von" to "07:00", "bis" to "09:00", "uhr" to "echt"))
        assertTrue(ok(ConditionType.INTERFACE, "zustand" to "verbunden"))
        assertTrue(ok(ConditionType.FEEDBACK, "melder" to "1.1"))
        assertTrue(ok(ConditionType.FEEDBACK, "melder" to "1.2", "zustand" to "frei"))
        assertTrue(ok(ConditionType.ACCESSORY, "adresse" to "3", "stellung" to "abzweigend"))
        assertTrue(ok(ConditionType.ACCESSORY, "adresse" to "4"))
        // Unbekannte Stellung ist weder gerade noch abzweigend
        assertFalse(ok(ConditionType.ACCESSORY, "adresse" to "5"))
        assertTrue(ok(ConditionType.ACCESSORY_RESERVED, "adresse" to "3"))
        assertFalse(ok(ConditionType.ACCESSORY_RESERVED, "adresse" to "4"))
        assertTrue(ok(ConditionType.BOOSTER, "zustand" to "an"))
    }

    @Test
    fun blocksAndTrains() {
        assertTrue(ok(ConditionType.BLOCK_FREE, "block" to "3.1"))
        assertFalse(ok(ConditionType.BLOCK_FREE, "block" to "1.2"))
        assertTrue(ok(ConditionType.BLOCK_STATE, "block" to "1.2", "zustand" to "reserviert"))
        assertTrue(ok(ConditionType.BLOCK_STATE, "block" to "2.4", "zustand" to "zug"))
        assertTrue(ok(ConditionType.BLOCK_POSITION, "block" to "2.4", "position" to "einfahrt"))
        assertTrue(ok(ConditionType.TRAIN, "block" to "2.4", "lok" to "7"))
        assertFalse(ok(ConditionType.TRAIN, "block" to "2.4", "lok" to "8"))
        assertTrue(ok(ConditionType.TRAIN, "lok" to "7"))
        assertTrue(ok(ConditionType.TRAIN_DIRECTION, "lok" to "7", "richtung" to "rueck"))
        assertTrue(ok(ConditionType.TRAIN_STATE, "lok" to "7", "zustand" to "automatik"))
        assertTrue(ok(ConditionType.TRAIN_STATE, "lok" to "8", "zustand" to "steht"))
        assertTrue(ok(ConditionType.TRAIN_ROUTE, "zugroute" to "zr-1"))
        assertTrue(ok(ConditionType.TRAIN_ROUTE, "zugroute" to "zr-2", "zustand" to "steht"))
        assertTrue(ok(ConditionType.LOCO, "lok" to "7", "pruefen" to "ab", "wert" to "30"))
        assertTrue(ok(ConditionType.LOCO, "lok" to "7", "pruefen" to "fn_an", "wert" to "2"))
        assertTrue(ok(ConditionType.LOCO, "lok" to "7", "pruefen" to "fn_aus", "wert" to "1"))
        assertTrue(ok(ConditionType.FUEL, "lok" to "7", "vergleich" to "ab", "prozent" to "100"))
    }

    @Test
    fun operatorsAndNot() {
        val a = Condition.of(ConditionType.BOOSTER, "zustand" to "an")
        val b = Condition.of(ConditionType.BOOSTER, "zustand" to "aus")
        assertTrue(Conditions.eval(Condition.or(a, b), world))
        assertFalse(Conditions.eval(Condition.and(a, b), world))
        assertTrue(Conditions.eval(Condition.and(a, b.copy(negate = true)), world))
        assertTrue(Conditions.eval(Condition.and(), world))
        assertFalse(Conditions.eval(Condition.or(), world))
        // Zufall zaehlt ohne Wuerfel als erfuellt, auch verneint
        val random = Condition.of(ConditionType.RANDOM, "prozent" to "0")
        assertTrue(Conditions.eval(random.copy(negate = true), world))
        assertFalse(Conditions.eval(random, world) { it > 0 })
    }
}
