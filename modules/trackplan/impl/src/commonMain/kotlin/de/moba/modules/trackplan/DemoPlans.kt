package de.moba.modules.trackplan

import de.moba.modules.trackplan.api.TrackPlan
import de.moba.modules.trackplan.api.TrackShape
import de.moba.modules.trackplan.api.TrackShape.BUFFER_STOP
import de.moba.modules.trackplan.api.TrackShape.CURVE
import de.moba.modules.trackplan.api.TrackShape.DOUBLE_SLIP
import de.moba.modules.trackplan.api.TrackShape.FEEDBACK
import de.moba.modules.trackplan.api.TrackShape.SIGNAL
import de.moba.modules.trackplan.api.TrackShape.STRAIGHT
import de.moba.modules.trackplan.api.TrackShape.TEXT
import de.moba.modules.trackplan.api.TrackShape.TURNOUT_LEFT
import de.moba.modules.trackplan.api.TrackShape.TURNOUT_RIGHT
import de.moba.modules.trackplan.api.TrackShape.TURNOUT_THREE_WAY
import de.moba.modules.trackplan.api.TrackShape.UNCOUPLER
import de.moba.modules.trackplan.api.TrackTile

/**
 * Beispiel-Gleisbilder: eine Anlage zum ersten Ausprobieren und zwei weitere fuer die Vorschaubilder.
 * Drehung in 45-Grad-Schritten: 0 = senkrecht, 1 = Diagonale unten links nach oben rechts,
 * 2 = waagerecht, 3 = Diagonale oben links nach unten rechts.
 */
object DemoPlans {
    private class Builder(val id: String, val name: String, val width: Int, val height: Int) {
        val tiles = mutableListOf<TrackTile>()

        fun tile(x: Int, y: Int, shape: TrackShape, rotation: Int, address: Int? = null) {
            tiles += TrackTile(x, y, shape, rotation, address = address)
        }

        fun horizontal(y: Int, xs: IntRange) = xs.forEach { tile(it, y, STRAIGHT, 2) }

        fun feedback(x: Int, y: Int, rotation: Int, module: Int, contact: Int) {
            tiles.removeAll { it.x == x && it.y == y }
            tiles += TrackTile(x, y, FEEDBACK, rotation, feedbackModule = module, feedbackContact = contact)
        }

        fun replace(x: Int, y: Int, shape: TrackShape, rotation: Int, address: Int? = null) {
            tiles.removeAll { it.x == x && it.y == y }
            tile(x, y, shape, rotation, address)
        }

        fun text(x: Int, y: Int, text: String) {
            tiles += TrackTile(x, y, TEXT, text = text)
        }

        fun build() = TrackPlan(id, name, width, height, tiles.toList())
    }

    /** Oval mit Bahnhof, Ausweichgleis und Abstellgleis. */
    fun layout(): TrackPlan = Builder("beispiel", "Beispielanlage", 22, 11).apply {
        // Oval: oben y = 2, unten y = 8, Kurven aus 45-Grad-Stuecken und Diagonalen
        horizontal(2, 4..15)
        horizontal(8, 4..15)
        tile(16, 2, CURVE, 6); tile(17, 3, STRAIGHT, 3); tile(18, 4, CURVE, 7)
        tile(18, 5, STRAIGHT, 0)
        tile(18, 6, CURVE, 0); tile(17, 7, STRAIGHT, 1); tile(16, 8, CURVE, 1)
        tile(3, 8, CURVE, 2); tile(2, 7, STRAIGHT, 3); tile(1, 6, CURVE, 3)
        tile(1, 5, STRAIGHT, 0)
        tile(1, 4, CURVE, 4); tile(2, 3, STRAIGHT, 1); tile(3, 2, CURVE, 5)
        // Ausweichgleis im Bahnhof
        replace(6, 8, TURNOUT_LEFT, 2, address = 12)
        tile(7, 7, STRAIGHT, 1); tile(8, 6, CURVE, 5)
        horizontal(6, 9..12)
        tile(13, 6, CURVE, 6); tile(14, 7, STRAIGHT, 3)
        replace(15, 8, TURNOUT_RIGHT, 6, address = 13)
        // Abstellgleis mit Entkuppler
        replace(12, 2, TURNOUT_LEFT, 2, address = 14)
        tile(13, 1, CURVE, 5); tile(14, 1, UNCOUPLER, 2, address = 20); tile(15, 1, STRAIGHT, 2)
        tile(16, 1, BUFFER_STOP, 2)
        // Signale und Rueckmelder
        replace(5, 2, SIGNAL, 2, address = 30)
        replace(10, 8, SIGNAL, 6, address = 31)
        feedback(9, 2, 2, module = 1, contact = 3)
        feedback(10, 6, 2, module = 2, contact = 5)
        feedback(12, 8, 2, module = 1, contact = 4)
        text(9, 4, "Bf. Neustadt")
    }.build()

    /** Schattenbahnhof mit Weichenstrasse und drei Abstellgleisen. */
    fun stagingYard(): TrackPlan = Builder("schattenbahnhof", "Schattenbahnhof", 20, 9).apply {
        horizontal(2, 0..2)
        tile(3, 2, TURNOUT_RIGHT, 2, address = 21)
        horizontal(2, 4..16)
        tile(4, 3, STRAIGHT, 3)
        tile(5, 4, TURNOUT_LEFT, 3, address = 22)
        horizontal(4, 6..16)
        tile(6, 5, STRAIGHT, 3)
        tile(7, 6, CURVE, 2)
        horizontal(6, 8..16)
        for ((i, y) in listOf(2, 4, 6).withIndex()) {
            tile(17, y, BUFFER_STOP, 2)
            feedback(12, y, 2, module = 3, contact = i + 1)
        }
        text(1, 0, "Schattenbahnhof Süd")
    }.build()

    /** Industriegleise mit Dreiwegweiche und Doppelkreuzungsweiche. */
    fun industry(): TrackPlan = Builder("industrie", "Industrie", 16, 8).apply {
        horizontal(3, 0..2)
        tile(3, 3, TURNOUT_THREE_WAY, 2, address = 40)
        horizontal(3, 4..7)
        tile(8, 3, DOUBLE_SLIP, 2, address = 42)
        horizontal(3, 9..13)
        tile(14, 3, BUFFER_STOP, 2)
        tile(4, 2, STRAIGHT, 1); tile(5, 1, CURVE, 5); horizontal(1, 6..9); tile(10, 1, BUFFER_STOP, 2)
        tile(4, 4, STRAIGHT, 3); tile(5, 5, CURVE, 2); horizontal(5, 6..8)
        tile(9, 5, UNCOUPLER, 2, address = 43); tile(10, 5, BUFFER_STOP, 2)
        tile(7, 2, BUFFER_STOP, 7); tile(9, 4, BUFFER_STOP, 3)
        text(1, 6, "Industriegebiet")
    }.build()
}
