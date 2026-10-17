package de.moba.modules.trackplan.api

/**
 * Schluessel der Zubehoer-Einstellungen in [TrackTile.props], aufgeteilt wie die Reiter in iTrain:
 * Allgemein (Name, Bilder, Datei), Decoder (Ausgang), Steuerung (Impuls, Melder),
 * bei Drehscheibe und Schiebebuehne ausserdem Zubehoer, Rueckmelder, Block und Steuern.
 */
object AccessoryProps {
    /** Allgemein: Anzeigename, z. B. "BÜ Dorfstraße". */
    const val NAME = "name"

    /** Allgemein: Namen der Bilder eines Zubehoers mit Bildern, durch "|" getrennt, z. B. "Aus|Blinken|Dauerlicht". */
    const val ASPECTS = "bilder"

    /** Allgemein: Klangdatei eines Sounds (Kennung im Klang-Ordner der App). */
    const val FILE = "datei"

    /** Decoder: "1", wenn "ein" an der Zentrale gerade/gruen bedeutet statt abzweigend/rot. */
    const val INVERT = "invert"

    /** Decoder bei Drehscheibe/Schiebebuehne: Adressierung der Gleise, [TableConfig.MODE_PAIR] oder [TableConfig.MODE_SINGLE]. */
    const val MODE = "modus"

    /** Steuerung: Impulsdauer in Millisekunden; nach dieser Zeit schaltet das Zubehoer wieder aus. Leer = Dauer. */
    const val PULSE = "impuls"

    /** Steuerung: Einschalt-Melder als "modul.kontakt", mehrere durch Komma getrennt; schaltet ein, sobald einer belegt wird. */
    const val ON_FEEDBACK = "ein"

    /** Steuerung: Ausschalt-Melder; schaltet aus, sobald einer wieder frei wird. Ohne: aus, sobald alle Einschalt-Melder frei sind. */
    const val OFF_FEEDBACK = "aus"

    /** Zubehoer: Anzahl der Positionen (Drehscheibe z. B. 24 oder 48, Schiebebuehne Anzahl Gleise). */
    const val POSITIONS = "positionen"

    /** Zubehoer: angeschlossene Gleise (Positionen), durch Komma getrennt. */
    const val TRACKS = "gleise"

    /** Zubehoer: Radius der Drehscheibe bzw. Laenge der Schiebebuehne in Rasterzellen. */
    const val SIZE = "groesse"

    /** Rueckmelder: meldet, dass die Buehne steht (Drehung/Fahrt beendet). */
    const val READY_FEEDBACK = "fertig"

    /** Block: Name des Blocks auf der Buehne. */
    const val BLOCK = "block"

    /** Block: Melder fuer "Buehne belegt". */
    const val BLOCK_FEEDBACK = "blockmelder"

    /** Block: "1" = nicht drehen/schieben, solange die Buehne belegt ist. */
    const val BLOCK_LOCK = "sperre"

    /** Steuern: Befehle als "adresse:r" oder "adresse:g". */
    const val CMD_TURN = "befehl.wenden"
    const val CMD_STEP_CW = "befehl.rechts"
    const val CMD_STEP_CCW = "befehl.links"

    /** Steuern: Dauer je Positionsschritt in Millisekunden, solange kein Fertig-Melder gesetzt ist. */
    const val STEP_TIME = "schrittzeit"
}

/** Ein s88-Melder als (Modul, Kontakt). */
typealias FeedbackRef = Pair<Int, Int>

/** Melderlisten "1.3, 2.5" lesen und schreiben; ungueltige Eintraege fallen weg. */
object FeedbackList {
    fun parse(text: String): List<FeedbackRef> = text.split(',', ';', ' ').mapNotNull { part ->
        val p = part.trim().split('.', ':', '/')
        if (p.size != 2) return@mapNotNull null
        val m = p[0].toIntOrNull() ?: return@mapNotNull null
        val c = p[1].toIntOrNull() ?: return@mapNotNull null
        if (m < 1 || c < 1) null else m to c
    }.distinct()

    fun format(list: List<FeedbackRef>): String = list.joinToString(", ") { "${it.first}.${it.second}" }
}

/** Ein Schaltbefehl an die Zentrale: Adresse und Stellung (true = rot/abzweigend). */
data class AccessoryCommand(val address: Int, val thrown: Boolean) {
    fun encode() = "$address:${if (thrown) "r" else "g"}"

    companion object {
        fun parse(text: String): AccessoryCommand? {
            val p = text.trim().split(':')
            val a = p.getOrNull(0)?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: return null
            val thrown = when (p.getOrNull(1)?.trim()?.lowercase()) {
                null, "", "r", "rot" -> true
                "g", "gruen", "grün" -> false
                else -> return null
            }
            return AccessoryCommand(a, thrown)
        }
    }
}

/** Gelesene Einstellungen einer Drehscheibe oder Schiebebuehne mit Grenzen und Vorgaben. */
data class TableConfig(
    val positions: Int,
    val tracks: List<Int>,
    /** Drehscheibe: Radius, Schiebebuehne: Gleislaenge in Zellen. */
    val size: Int,
    val pairMode: Boolean,
    val turn: AccessoryCommand?,
    val stepCw: AccessoryCommand?,
    val stepCcw: AccessoryCommand?,
    val ready: FeedbackRef?,
    val blockFeedback: FeedbackRef?,
    val lockWhileOccupied: Boolean,
    val stepMillis: Long,
    /** Schiebebuehne: Gleise laufen senkrecht, die Buehne faehrt waagerecht (Drehung 90 oder 270 Grad). */
    val vertical: Boolean,
) {
    val radius get() = size
    val length get() = size

    /** Adresse und Stellung, die die Buehne an Gleis [track] faehrt. */
    fun output(base: Int, track: Int): AccessoryCommand =
        if (pairMode) SignalAddressing.output(base, track).let { (a, t) -> AccessoryCommand(a, t) }
        else AccessoryCommand(base + track, thrown = false)

    /** Welche Position eine gemeldete Stellung bedeutet, `null`, wenn sie nicht zu den Gleisadressen gehoert. */
    fun positionFor(base: Int, address: Int, thrown: Boolean): Int? {
        val n = if (pairMode) SignalAddressing.aspect(base, address, thrown) else if (!thrown) address - base else return null
        return n.takeIf { address >= base && it in 0 until positions }
    }

    /** Hoechste Adresse, die die Gleise belegen. */
    fun lastAddress(base: Int) = if (pairMode) base + (positions - 1) / 2 else base + positions - 1

    companion object {
        const val MODE_PAIR = "paar"
        const val MODE_SINGLE = "einzeln"
        const val MAX_POSITIONS = 48
        const val MAX_SIZE = 8

        fun of(tile: TrackTile): TableConfig {
            val turntable = tile.shape.kind == TileKind.TURNTABLE
            val positions = tile.prop(AccessoryProps.POSITIONS).toIntOrNull()?.coerceIn(2, MAX_POSITIONS) ?: if (turntable) 24 else 4
            val size = tile.prop(AccessoryProps.SIZE).toIntOrNull()?.coerceIn(1, MAX_SIZE) ?: if (turntable) 2 else 4
            val tracks = tile.prop(AccessoryProps.TRACKS).split(',', ' ', ';').mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 0 until positions }.distinct().sorted()
            return TableConfig(
                positions = positions,
                tracks = tracks.ifEmpty { if (turntable) listOf(0, positions / 2) else (0 until positions).toList() },
                size = size,
                pairMode = tile.prop(AccessoryProps.MODE) != MODE_SINGLE,
                turn = AccessoryCommand.parse(tile.prop(AccessoryProps.CMD_TURN)),
                stepCw = AccessoryCommand.parse(tile.prop(AccessoryProps.CMD_STEP_CW)),
                stepCcw = AccessoryCommand.parse(tile.prop(AccessoryProps.CMD_STEP_CCW)),
                ready = FeedbackList.parse(tile.prop(AccessoryProps.READY_FEEDBACK)).firstOrNull(),
                blockFeedback = FeedbackList.parse(tile.prop(AccessoryProps.BLOCK_FEEDBACK)).firstOrNull(),
                lockWhileOccupied = tile.prop(AccessoryProps.BLOCK_LOCK) == "1",
                stepMillis = tile.prop(AccessoryProps.STEP_TIME).toLongOrNull()?.coerceIn(0, 60_000) ?: 0,
                vertical = !turntable && tile.rotation % 4 == 2,
            )
        }
    }
}

/** Namen der Bilder eines Zubehoers mit Bildern; ohne Eintrag zwei Bilder "Aus" und "Ein". */
fun TrackTile.aspectNames(): List<String> =
    prop(AccessoryProps.ASPECTS).split('|').map { it.trim() }.filter { it.isNotEmpty() }
        .take(SignalAddressing.MAX_ASPECTS).ifEmpty { listOf("Aus", "Ein") }

/** Anzeigename aus dem Reiter Allgemein, sonst die Form. */
fun TrackTile.displayName(): String = prop(AccessoryProps.NAME).ifBlank { shape.label }

/** Stellung an der Zentrale fuer "ein" bzw. "aus", nach dem Decoder-Reiter. */
fun TrackTile.outputFor(on: Boolean): Boolean = if (prop(AccessoryProps.INVERT) == "1") !on else on

/** Ist das Zubehoer nach den gemeldeten Stellungen eingeschaltet? */
fun TrackTile.isOn(turnouts: Map<Int, Boolean>): Boolean {
    val a = address ?: return false
    val thrown = turnouts[a] ?: return false
    return thrown == outputFor(true)
}

/** Impulsdauer aus dem Reiter Steuerung; beim Entkuppler 500 ms als Vorgabe. */
fun TrackTile.pulseMillis(): Long? = prop(AccessoryProps.PULSE).toLongOrNull()?.takeIf { it > 0 }?.coerceAtMost(60_000)
    ?: if (shape.kind == TileKind.UNCOUPLER) 500L else null
