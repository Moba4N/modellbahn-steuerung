package de.moba.modules.symbols.api

/** Alle mitgelieferten Signalsysteme, nach Land sortiert. Die Daten stehen in [SignalData]. */
object SignalCatalog {
    val systems: List<SignalSystem> get() = SignalData.systems

    /** Laender ohne eigenes Eisenbahnnetz (keine Signale noetig). */
    val countriesWithoutRailway: Map<String, String> get() = SignalData.countriesWithoutRailway

    /** Laendercodes mit Signalsystemen, nach deutschem Namen sortiert. */
    val countries: List<String> get() = systems.map { it.country }.distinct().sortedBy { COUNTRY_NAMES[it] ?: it }

    fun forCountry(country: String) = systems.filter { it.country == country }

    fun system(id: String) = systems.firstOrNull { it.id == id }
}
