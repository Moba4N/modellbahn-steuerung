package de.moba.modules.symbols

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileSymbolStoreTest {
    @Test
    fun savesAndLoads() {
        val dir = Files.createTempDirectory("symbole").toFile()
        val store = FileSymbolStore(dir.resolve("symbole.txt"))
        assertNull(store.load())
        val symbols = ExampleSymbols.all()
        store.save(symbols)
        assertEquals(symbols, store.load())
        dir.deleteRecursively()
    }
}
