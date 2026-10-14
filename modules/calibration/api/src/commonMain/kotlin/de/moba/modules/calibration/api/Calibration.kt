package de.moba.modules.calibration.api

import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

const val CALIBRATION_MODULE_ID = "moba.einmessen"

/**
 * Fahrstufenmodus des Decoders. Die App faehrt intern immer mit 126 Stufen (128er-Modus);
 * fuer 14 und 28 Fahrstufen wird jede Stufe auf den passenden Wert dieser Skala umgerechnet.
 */
enum class SpeedStepMode(val steps: Int, val label: String) {
    STEPS_14(14, "14 Fahrstufen"),
    STEPS_28(28, "28 Fahrstufen"),
    STEPS_128(126, "128 Fahrstufen"),
    ;

    /** Wert auf der 126er-Skala der Zentrale fuer Fahrstufe [step] dieses Modus. */
    fun toStationSpeed(step: Int): Int = (step.coerceIn(0, steps) * 126.0 / steps).roundToInt()

    /** Die Stufen, die beim Einmessen gemessen werden. Bei 128 Fahrstufen jede [stride]-te. */
    fun measurePoints(stride: Int = DEFAULT_STRIDE_128): List<Int> = when (this) {
        STEPS_128 -> ((stride..steps step stride).toList() + steps).distinct()
        else -> (1..steps).toList()
    }

    companion object {
        const val DEFAULT_STRIDE_128 = 8
    }
}

/** Baugroesse mit Massstab, z. B. H0 = 1:87. */
enum class Scale(val label: String, val factor: Double) {
    H0("H0 (1:87)", 87.0),
    TT("TT (1:120)", 120.0),
    N("N (1:160)", 160.0),
    Z("Z (1:220)", 220.0),
    ZERO("0 (1:45)", 45.0),
    ONE("1 (1:32)", 32.0),
    G("G (1:22,5)", 22.5),
}

/** Ein Rueckmelder: s88-Modul und Kontakt (1 bis 16). */
data class Contact(val module: Int, val contact: Int) {
    override fun toString() = "$module.$contact"
}

/**
 * Messstrecke zwischen zwei Rueckmeldern im Abstand [lengthMm], gemessen von Kontaktanfang zu Kontaktanfang.
 * Die Lok faehrt dabei auf einem Rundkurs immer wieder durch die Strecke.
 */
data class MeasuringTrack(val start: Contact, val end: Contact, val lengthMm: Int, val scale: Scale = Scale.H0)

data class CalibrationSetup(
    val address: Int,
    val name: String,
    val mode: SpeedStepMode,
    val track: MeasuringTrack,
    val forward: Boolean = true,
    /** Wartezeit nach jedem Fahrstufenwechsel, bis die Lok gleichmaessig faehrt. */
    val settleSeconds: Int = 5,
    /** Laengste Wartezeit je Melder; danach gilt die Stufe als zu langsam. */
    val timeoutSeconds: Int = 180,
    /** Bei 128 Fahrstufen jede wievielte Stufe gemessen wird. */
    val stride: Int = SpeedStepMode.DEFAULT_STRIDE_128,
)

/** Messwert einer Fahrstufe; [kmh] ist die Vorbildgeschwindigkeit, `null` = nicht gemessen (zu langsam). */
data class SpeedPoint(val step: Int, val kmh: Double?)

/** Ergebnis des Einmessens einer Lok in einem Fahrstufenmodus. */
data class SpeedProfile(
    val address: Int,
    val name: String,
    val mode: SpeedStepMode,
    val scale: Scale,
    val points: List<SpeedPoint>,
    /** Datum der Messung, z. B. "2026-10-14". */
    val date: String = "",
) {
    val maxKmh: Double? get() = points.mapNotNull { it.kmh }.maxOrNull()

    /** Kleinste Fahrstufe, die mindestens [kmh] erreicht (linear zwischen den Messpunkten). `null` = zu schnell. */
    fun stepFor(kmh: Double): Int? {
        val measured = points.filter { it.kmh != null }.sortedBy { it.step }
        if (measured.isEmpty()) return null
        var prev = SpeedPoint(0, 0.0)
        for (p in measured) {
            val v = p.kmh!!
            if (v >= kmh) {
                val pv = prev.kmh!!
                if (v == pv) return p.step
                val step = prev.step + (kmh - pv) / (v - pv) * (p.step - prev.step)
                return kotlin.math.ceil(step - 1e-9).toInt().coerceIn(1, p.step)
            }
            prev = p
        }
        return null
    }
}

/** Speed in Vorbild-km/h aus Streckenlaenge und Fahrzeit. */
object SpeedMath {
    fun scaleKmh(lengthMm: Int, millis: Long, scale: Scale): Double {
        require(millis > 0) { "Fahrzeit muss positiv sein" }
        val mmPerSecond = lengthMm * 1000.0 / millis
        return mmPerSecond * scale.factor * 3.6 / 1000.0
    }
}

/** Was gerade passiert. */
sealed interface CalibrationProgress {
    data object Idle : CalibrationProgress

    data class Running(
        val setup: CalibrationSetup,
        /** Index der aktuellen Stufe in den Messpunkten. */
        val index: Int,
        val total: Int,
        val step: Int,
        val phase: String,
        val points: List<SpeedPoint>,
    ) : CalibrationProgress

    data class Finished(val profile: SpeedProfile) : CalibrationProgress
    data class Stopped(val reason: String, val points: List<SpeedPoint>) : CalibrationProgress
}

/** Liest und schreibt alle Messergebnisse. */
interface CalibrationStore {
    fun load(): List<SpeedProfile>
    fun save(profiles: List<SpeedProfile>)
}

/**
 * Oeffentliche Schnittstelle des Moduls "Einmessen": eine Lok faehrt jede Fahrstufe durch eine
 * Messstrecke zwischen zwei Rueckmeldern; daraus ergibt sich ihre Vorbildgeschwindigkeit.
 */
interface CalibrationService {
    val profiles: StateFlow<List<SpeedProfile>>
    val progress: StateFlow<CalibrationProgress>

    /** Startet das Einmessen; ein laufendes wird vorher abgebrochen. */
    fun start(setup: CalibrationSetup)

    /** Bricht ab und haelt die Lok an. */
    fun cancel()

    fun delete(address: Int, mode: SpeedStepMode)
}

/** Textformat der Messergebnisse: eine Zeile je Profil, Felder mit Tab getrennt, Punkte als "Stufe:kmh". */
object CalibrationCodec {
    private const val HEADER = "# einmessen v1"

    fun encode(profiles: List<SpeedProfile>): String = buildString {
        appendLine(HEADER)
        for (p in profiles) {
            val points = p.points.joinToString(" ") { "${it.step}:${it.kmh?.let { v -> (v * 10).roundToInt() / 10.0 } ?: "-"}" }
            appendLine(listOf(p.address, clean(p.name), p.mode.name, p.scale.name, p.date, points).joinToString("\t"))
        }
    }

    fun decode(text: String): List<SpeedProfile> = text.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 6) return@mapNotNull null
            val mode = SpeedStepMode.entries.firstOrNull { it.name == f[2] } ?: return@mapNotNull null
            val scale = Scale.entries.firstOrNull { it.name == f[3] } ?: Scale.H0
            val points = f[5].split(' ').filter { it.isNotBlank() }.mapNotNull { token ->
                val (s, v) = token.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                SpeedPoint(s.toIntOrNull() ?: return@mapNotNull null, v.toDoubleOrNull())
            }
            SpeedProfile(f[0].toIntOrNull() ?: return@mapNotNull null, f[1], mode, scale, points, f[4])
        }.toList()

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
}
