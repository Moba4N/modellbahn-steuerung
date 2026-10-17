package de.moba.modules.symbols

import de.moba.core.runtime.LicenseInfo
import de.moba.core.runtime.MobaModule
import de.moba.core.runtime.ModuleContext
import de.moba.core.runtime.SemVer
import de.moba.core.runtime.provide
import de.moba.modules.symbols.api.PixelImage
import de.moba.modules.symbols.api.SYMBOLS_MODULE_ID
import de.moba.modules.symbols.api.SymbolCategory
import de.moba.modules.symbols.api.SymbolDef
import de.moba.modules.symbols.api.SymbolLicenseException
import de.moba.modules.symbols.api.SymbolService
import de.moba.modules.symbols.api.SymbolState
import de.moba.modules.symbols.api.SymbolStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Haelt die Symbolbibliothek ueber Zentralenwechsel hinweg. Ohne gespeicherte Datei gibt es zwei Beispielsymbole. */
class SymbolRepository(private val store: SymbolStore?) {
    internal val symbols = MutableStateFlow(runCatching { store?.load() }.getOrNull() ?: ExampleSymbols.all())
    internal val unsaved = MutableStateFlow(false)
    private var next = symbols.value.size + 1

    internal fun newId(): String {
        while (true) {
            val id = "sym-${next++}"
            if (symbols.value.none { it.id == id }) return id
        }
    }

    internal fun saveAll() {
        store?.save(symbols.value)
        unsaved.value = false
    }
}

/**
 * Modul "Symbole": eigene Pixelsymbole zeichnen wie im Symbol-Editor von TrainController,
 * dazu Licht- und Formsignale aus Deutschland, der Schweiz und den Niederlanden. Zeichnen ist kostenlos, Speichern gibt es ab Pro.
 */
class SymbolModule(private val repository: SymbolRepository) : MobaModule {
    override val id = SYMBOLS_MODULE_ID
    override val displayName = "Symbole"
    override val version = SemVer(1, 0)
    override val apiVersion = SemVer(1, 0)
    override val licenseKey: String? = null

    override suspend fun start(ctx: ModuleContext) {
        ctx.provide<SymbolService>(SymbolServiceImpl(repository, ctx.license))
    }

    companion object {
        /** Eigene Symbole dauerhaft speichern: ab Pro (Club enthaelt Pro). */
        val SAVE_LICENSE_KEYS = listOf("pro", "club")
    }
}

internal class SymbolServiceImpl(private val repository: SymbolRepository, license: LicenseInfo) : SymbolService {
    override val symbols: StateFlow<List<SymbolDef>> = repository.symbols
    override val unsaved: StateFlow<Boolean> = repository.unsaved
    override val canSave = SymbolModule.SAVE_LICENSE_KEYS.any(license::isUnlocked)

    override fun update(symbol: SymbolDef) {
        repository.symbols.update { list ->
            if (list.any { it.id == symbol.id }) list.map { if (it.id == symbol.id) symbol else it } else list + symbol
        }
        repository.unsaved.value = true
    }

    override fun remove(id: String) {
        repository.symbols.update { list -> list.filterNot { it.id == id } }
        repository.unsaved.value = true
    }

    override fun newId() = repository.newId()

    override fun save() {
        if (!canSave) throw SymbolLicenseException("Eigene Symbole speichern gibt es ab der Pro-Lizenz.")
        repository.saveAll()
    }
}

/** Zwei kleine Beispiele, damit der Editor nicht leer startet. */
internal object ExampleSymbols {
    private const val OUTLINE = 0xFF2B2F33.toInt()
    private const val WALL = 0xFFD9C7A7.toInt()
    private const val ROOF = 0xFFB0413E.toInt()
    private const val WINDOW = 0xFF6FA8DC.toInt()
    private const val LIT = 0xFFFFD54F.toInt()
    private const val POLE = 0xFF8A9096.toInt()

    fun all() = listOf(lamp(), house())

    private fun lamp(): SymbolDef {
        val base = PixelImage(32)
            .rect(15, 12, 16, 30, POLE, filled = true)
            .rect(11, 30, 20, 31, POLE, filled = true)
            .ellipse(9, 2, 22, 13, OUTLINE, filled = true)
        return SymbolDef(
            id = "sym-1",
            name = "Bahnsteiglampe",
            category = SymbolCategory.ACCESSORY,
            states = listOf(
                SymbolState("aus", base.ellipse(11, 4, 20, 11, 0xFF5B6168.toInt(), filled = true)),
                SymbolState("an", base.ellipse(11, 4, 20, 11, LIT, filled = true)),
            ),
        )
    }

    private fun house(): SymbolDef {
        var img = PixelImage(32)
            .rect(5, 14, 26, 30, WALL, filled = true)
            .rect(5, 14, 26, 30, OUTLINE, filled = false)
        for (i in 0..10) img = img.line(5 + i, 14 - i, 26 - i, 14 - i, ROOF)
        img = img.rect(9, 18, 13, 22, WINDOW, filled = true)
            .rect(18, 18, 22, 22, WINDOW, filled = true)
            .rect(14, 24, 17, 30, OUTLINE, filled = true)
        return SymbolDef("sym-2", "Bahnhofsgebäude", SymbolCategory.BUILDING, listOf(SymbolState("Grundstellung", img)))
    }
}
