package de.moba.modules.symbols.api

import kotlinx.coroutines.flow.StateFlow

const val SYMBOLS_MODULE_ID = "moba.symbole"

enum class SymbolCategory(val label: String) {
    SIGNAL("Signal"),
    TRACK("Gleis und Weiche"),
    BUILDING("Gebäude"),
    ACCESSORY("Zubehör"),
    OTHER("Sonstiges"),
}

/** Ein Zustand eines Symbols, z. B. "Halt" und "Fahrt" bei einem Signal oder "aus" und "an" bei einer Lampe. */
data class SymbolState(val name: String, val image: PixelImage)

/** Ein selbst gezeichnetes Symbol mit einem oder mehreren Zustaenden gleicher Groesse. */
data class SymbolDef(
    val id: String,
    val name: String,
    val category: SymbolCategory = SymbolCategory.OTHER,
    val states: List<SymbolState> = listOf(SymbolState("Grundstellung", PixelImage(PixelImage.DEFAULT_SIZE))),
) {
    init {
        require(states.isNotEmpty()) { "Ein Symbol braucht mindestens einen Zustand" }
        require(states.all { it.image.size == states.first().image.size }) { "Alle Zustände müssen gleich groß sein" }
    }

    val size: Int get() = states.first().image.size

    fun withState(index: Int, state: SymbolState) = copy(states = states.mapIndexed { i, s -> if (i == index) state else s })

    /** Alle Zustaende auf eine neue Groesse bringen. */
    fun resized(newSize: Int) = copy(states = states.map { it.copy(image = it.image.resized(newSize)) })
}

/** Liest und schreibt die Symbolbibliothek (Datei auf Windows und Android). */
interface SymbolStore {
    fun load(): List<SymbolDef>?
    fun save(symbols: List<SymbolDef>)
}

/**
 * Oeffentliche Schnittstelle des Moduls "Symbole". Zeichnen und Aendern geht immer,
 * dauerhaft speichern nur mit [canSave] (ab Pro).
 */
interface SymbolService {
    val symbols: StateFlow<List<SymbolDef>>
    val unsaved: StateFlow<Boolean>
    val canSave: Boolean

    /** Legt das Symbol an oder ersetzt es (gleiche [SymbolDef.id]). */
    fun update(symbol: SymbolDef)
    fun remove(id: String)
    fun newId(): String

    /** @throws SymbolLicenseException ohne Pro */
    fun save()

    fun symbol(id: String): SymbolDef? = symbols.value.firstOrNull { it.id == id }
}

class SymbolLicenseException(message: String) : Exception(message)
