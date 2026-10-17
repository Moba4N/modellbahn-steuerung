package de.moba.core.model

/** Eine Lok mit ihrer Digitaladresse. */
data class Loco(
    val id: String,
    val name: String,
    val address: Int,
    val functionLabels: Map<Int, String> = emptyMap(),
    /** Fahreigenschaften aus der Fahrzeugverwaltung (Fuehrerstand, Traegheit, Haltepunkt). */
    val dynamics: LocoDynamics = LocoDynamics(),
    /** Hoechstgeschwindigkeit des Vorbilds in km/h, fuer den Tacho im Fuehrerstand. */
    val vmaxKmh: Int? = null,
)

/**
 * Fahreigenschaften einer Lok. Das Fahrpult nutzt Fuehrerstand und Traegheit, die Automatik
 * (Pendelstrecken, Zugrouten) den Rueckmelde-Offset und die Reaktionsverzoegerung fuers genaue Halten.
 */
data class LocoDynamics(
    /** Fahrpult als Fuehrerstand zeigen: Tacho in km/h, Fahrhebel, Betriebs- und Schnellbremse. */
    val cab: Boolean = false,
    /** Traegheitssimulation: die App faehrt die Fahrstufe nach und nach hoch und runter, wie bei einem schweren Zug. */
    val inertia: Boolean = false,
    /** Sekunden vom Stillstand bis zur Hoechststufe. */
    val accelerationSeconds: Double = 10.0,
    /** Sekunden von der Hoechststufe bis zum Stillstand (Betriebsbremse). */
    val brakingSeconds: Double = 8.0,
    /** Zeitraum zwischen zwei Fahrstufen der Traegheitssimulation in ms. */
    val periodMs: Int = 250,
    /** Rueckmelde-Offset: so viele mm faehrt die Lok nach dem Haltemelder noch, bevor sie halten soll. */
    val feedbackOffsetMm: Int = 0,
    /** Reaktionsverzoegerung des Decoders in ms; die Automatik schickt den Halt um so viel frueher. */
    val reactionDelayMs: Int = 0,
) {
    /** Werte in sinnvolle Grenzen bringen, z. B. nach dem Laden einer Datei. */
    fun validated() = copy(
        accelerationSeconds = accelerationSeconds.coerceIn(0.0, MAX_SECONDS),
        brakingSeconds = brakingSeconds.coerceIn(0.0, MAX_SECONDS),
        periodMs = periodMs.coerceIn(MIN_PERIOD_MS, MAX_PERIOD_MS),
        feedbackOffsetMm = feedbackOffsetMm.coerceIn(0, MAX_OFFSET_MM),
        reactionDelayMs = reactionDelayMs.coerceIn(0, MAX_REACTION_MS),
    )

    companion object {
        const val MAX_SECONDS = 120.0
        const val MIN_PERIOD_MS = 50
        const val MAX_PERIOD_MS = 2000
        const val MAX_OFFSET_MM = 2000
        const val MAX_REACTION_MS = 3000
    }
}

/** Eine Weiche oder ein anderer Magnetartikel mit seiner Zubehoeradresse. */
data class Turnout(
    val id: String,
    val name: String,
    val address: Int,
)

/** Ein Gleisabschnitt, der belegt oder frei sein kann. */
data class TrackSection(
    val id: String,
    val name: String,
)

/**
 * Ein Rueckmeldekontakt, z. B. am s88-Bus: Modul 2, Kontakt 5.
 * [sectionId] ordnet den Kontakt einem Gleisabschnitt im Stellpult zu.
 */
data class FeedbackContact(
    val module: Int,
    val contact: Int,
    val sectionId: String? = null,
) {
    init {
        require(module >= 1) { "Modulnummer beginnt bei 1" }
        require(contact in 1..16) { "Ein s88-Modul hat die Kontakte 1 bis 16" }
    }
}

/** Die gesamte Anlage: alles, was die App ueber die Modellbahn weiss. */
data class Layout(
    val name: String = "Neue Anlage",
    val locos: List<Loco> = emptyList(),
    val turnouts: List<Turnout> = emptyList(),
    val sections: List<TrackSection> = emptyList(),
    val feedback: List<FeedbackContact> = emptyList(),
) {
    fun sectionFor(module: Int, contact: Int): TrackSection? {
        val id = feedback.firstOrNull { it.module == module && it.contact == contact }?.sectionId
        return sections.firstOrNull { it.id == id }
    }
}
