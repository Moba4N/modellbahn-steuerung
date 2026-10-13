package de.moba.modules.symbols

import de.moba.modules.symbols.api.SymbolCodec
import de.moba.modules.symbols.api.SymbolDef
import de.moba.modules.symbols.api.SymbolStore
import java.io.File

/** Speichert die Symbolbibliothek in einer Textdatei; schreibt erst in eine Zwischendatei. */
class FileSymbolStore(private val file: File) : SymbolStore {
    override fun load(): List<SymbolDef>? =
        if (file.exists()) SymbolCodec.decode(file.readText(Charsets.UTF_8)) else null

    override fun save(symbols: List<SymbolDef>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(SymbolCodec.encode(symbols), Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "Symbole konnten nicht gespeichert werden: $file" }
        }
    }
}
