package de.moba.modules.symbols.api

/** Alle mitgelieferten Signalsysteme (Deutschland, Schweiz, Niederlande), Licht- und Formsignale. */
object SignalCatalog {
    val systems: List<SignalSystem> by lazy { SignalData.systems + FormSignalData.systems }

    /** Laendercodes mit Signalsystemen, nach deutschem Namen sortiert. */
    val countries: List<String> get() = systems.map { it.country }.distinct().sortedBy { COUNTRY_NAMES[it] ?: it }

    fun forCountry(country: String) = systems.filter { it.country == country }

    fun system(id: String) = systems.firstOrNull { it.id == id }
}
