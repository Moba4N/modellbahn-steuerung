package de.moba.core.model

/** Eine Lok mit ihrer Digitaladresse. */
data class Loco(
    val id: String,
    val name: String,
    val address: Int,
    val functionLabels: Map<Int, String> = emptyMap(),
)

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
