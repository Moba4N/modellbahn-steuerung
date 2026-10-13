package de.moba.modules.trackplan.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.moba.modules.symbols.api.COUNTRY_NAMES
import de.moba.modules.symbols.api.PixelImage
import de.moba.modules.symbols.api.SignalCatalog
import de.moba.modules.symbols.api.SignalSymbols
import de.moba.modules.symbols.api.SymbolCategory
import de.moba.modules.symbols.api.SymbolDef
import de.moba.modules.symbols.ui.SymbolImage
import de.moba.modules.trackplan.api.TileKind
import de.moba.modules.trackplan.api.TrackTile

/** Kennzeichnet in [TrackTile.symbol] ein mitgeliefertes Signalsystem statt eines eigenen Symbols. */
internal const val SIGNAL_PREFIX = "signal:"

/** Die eigenen Symbole aus dem Modul "Symbole"; leer, wenn das Modul fehlt. */
internal val LocalSymbolLibrary = compositionLocalOf<List<SymbolDef>> { emptyList() }

/** Gerenderte Signalbilder, damit sie nicht bei jedem Zeichnen neu entstehen. */
private val signalCache = HashMap<Pair<String, Int>, PixelImage>()

private const val SIGNAL_PIXELS = 32

/**
 * Bild einer Kachel mit Symbol, passend zum Schaltzustand:
 * Signale zeigen bei "abzweigend" oder unbekannt den Haltbegriff, bei "gerade" den Fahrbegriff;
 * andere Symbole zeigen ausgeschaltet den ersten, eingeschaltet den zweiten Zustand.
 */
internal fun symbolImage(tile: TrackTile, turnouts: Map<Int, Boolean>, library: List<SymbolDef>): PixelImage? {
    if (tile.symbol.isBlank()) return null
    val thrown = tile.address?.let { turnouts[it] }
    val signal = tile.shape.kind == TileKind.SIGNAL
    val index = if (signal) (if (thrown == false) 1 else 0) else (if (thrown == true) 1 else 0)
    if (tile.symbol.startsWith(SIGNAL_PREFIX)) {
        val system = SignalCatalog.system(tile.symbol.removePrefix(SIGNAL_PREFIX)) ?: return null
        val aspect = if (index == 1) system.clear else system.stop
        return signalCache.getOrPut(system.id to index) { SignalSymbols.render(system, aspect, SIGNAL_PIXELS) }
    }
    val def = library.firstOrNull { it.id == tile.symbol } ?: return null
    return def.states.getOrElse(index) { def.states.first() }.image
}

private fun label(symbol: String, library: List<SymbolDef>): String = when {
    symbol.isBlank() -> "Einfacher Lichtpunkt"
    symbol.startsWith(SIGNAL_PREFIX) -> SignalCatalog.system(symbol.removePrefix(SIGNAL_PREFIX))
        ?.let { "${COUNTRY_NAMES[it.country] ?: it.country} · ${it.name}" } ?: "Unbekanntes Signal"
    else -> library.firstOrNull { it.id == symbol }?.name ?: "Gelöschtes Symbol"
}

/** Auswahl des Bildes fuer Signal- und Symbolkacheln. */
@Composable
internal fun SymbolChooser(tile: TrackTile, onPick: (String) -> Unit) {
    val library = LocalSymbolLibrary.current
    val signal = tile.shape.kind == TileKind.SIGNAL
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            symbolImage(tile, emptyMap(), library)?.let { SymbolImage(it, Modifier.size(22.dp)) }
            Spacer(Modifier.width(6.dp))
            Text(
                (if (signal) "Signalbild: " else "Symbol: ") + label(tile.symbol, library),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun pick(value: String) {
                onPick(value)
                open = false
            }
            val own = if (signal) library.filter { it.category == SymbolCategory.SIGNAL } else library
            if (signal) DropdownMenuItem(text = { Text("Einfacher Lichtpunkt") }, onClick = { pick("") })
            if (own.isEmpty() && !signal) {
                DropdownMenuItem(text = { Text("Noch keine eigenen Symbole, im Reiter „Symbole“ zeichnen") }, onClick = { open = false })
            }
            own.forEach { def ->
                DropdownMenuItem(
                    text = { Text(def.name) },
                    leadingIcon = { SymbolImage(def.states.first().image, Modifier.size(24.dp)) },
                    onClick = { pick(def.id) },
                )
            }
            if (signal) {
                HorizontalDivider()
                Text(
                    "Signalbilder EU",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
                SignalCatalog.countries.forEach { country ->
                    SignalCatalog.forCountry(country).forEach { system ->
                        DropdownMenuItem(
                            text = { Text("${COUNTRY_NAMES[country] ?: country} · ${system.name}") },
                            onClick = { pick(SIGNAL_PREFIX + system.id) },
                        )
                    }
                }
            }
        }
    }
}

