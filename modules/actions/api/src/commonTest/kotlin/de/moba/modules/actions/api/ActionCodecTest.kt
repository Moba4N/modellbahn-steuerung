package de.moba.modules.actions.api

import kotlin.test.Test
import kotlin.test.assertEquals

class ActionCodecTest {
    @Test
    fun roundTripKeepsTreeCommandsAndFuel() {
        val rule = ActionRule(
            "ak-1", "Bahnhof\t„Nord“ | 1=2", enabled = false,
            condition = Condition.and(
                Condition.of(ConditionType.BLOCK_OCCUPIED, "block" to "1.3"),
                Condition.or(
                    Condition.of(ConditionType.TIME, "von" to "22:00", "bis" to "06:00", "uhr" to "echt"),
                    Condition(ConditionType.RANDOM, mapOf("prozent" to "30"), negate = true),
                    Condition.and(),
                ),
                Condition.of(ConditionType.CATEGORY, "block" to "1.3", "gattung" to "STEAM"),
            ),
            commands = listOf(
                Command.of(CommandType.LOCO_FUNCTION, "lok" to "3", "funktion" to "2", "zustand" to "an"),
                Command.of(CommandType.MESSAGE, "text" to "100 % = voll|leer\nZeile 2"),
            ),
        )
        val book = ActionBook(listOf(rule, ActionRule("ak-2", "Leer")), mapOf(3 to 42.5, 5 to 100.0))
        assertEquals(book, ActionCodec.decode(ActionCodec.encode(book)))
    }

    @Test
    fun skipsBrokenLinesAndUnknownTypes() {
        val text = "# aktionen v1\nAK\tak-1\tA\t1\nB\tak-1\t0\tOPERATOR\t0\top=oder\nB\tak-1\t1\tGIBTSNICHT\t0\t\n" +
            "B\tak-1\t1\tFEEDBACK\t0\tmelder=1.2\nB\tak-1\t5\tFEEDBACK\t0\t\nC\tak-9\tWAIT\t\nC\tak-1\tWAIT\tsekunden=3\nT\tx\t1\n"
        val book = ActionCodec.decode(text)
        val rule = book.rules.single()
        assertEquals(listOf(ConditionType.FEEDBACK), rule.condition.children.map { it.type })
        assertEquals("oder", rule.condition.param("op"))
        assertEquals(listOf(Command.of(CommandType.WAIT, "sekunden" to "3")), rule.commands)
        assertEquals(emptyMap(), book.fuel)
    }

    @Test
    fun describesWithLabels() {
        assertEquals("Rückmeldung: 1.3, belegt", Condition.of(ConditionType.FEEDBACK, "melder" to "1.3").describe())
        assertEquals("nicht oder", Condition.or().copy(negate = true).describe())
        assertEquals("Lokfunktion: Lok 3, Funktion 0, an", Command.of(CommandType.LOCO_FUNCTION, "lok" to "3").describe())
        assertEquals("Zug", Condition.of(ConditionType.TRAIN).describe())
    }
}
