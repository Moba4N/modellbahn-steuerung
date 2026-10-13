package de.moba.modules.trackplan.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.moba.modules.symbols.api.COUNTRY_NAMES
import de.moba.modules.symbols.api.PixelImage
import de.moba.modules.symbols.api.SignalCatalog
import de.moba.modules.symbols.api.SignalSymbols
import de.moba.modules.symbols.api.SymbolCategory
import de.moba.modules.symbols.api.SymbolDef
import de.moba.modules.symbols.ui.SymbolImage
import de.moba.modules.trackplan.api.SignalAddressing
import de.moba.modules.trackplan.api.TileKind
import de.moba.modules.trackplan.api.TrackTile

/** Kennzeichnet in [TrackTile.symbol] ein mitgeliefertes Signalsystem statt eines eigenen Symbols. */
internal const val SIGNAL_PREFIX = "signal:"

/** Die eigenen Symbole aus dem Modul "Symbole"; leer, wenn das Modul fehlt. */
internal val LocalSymbolLibrary = compositionLocalOf<List<SymbolDef>> { emptyList() }

/** Ein schaltbares Bild einer Signal- oder Symbolkachel. */
internal class TileState(val name: String, val meaning: String, val image: PixelImage)

/** Gerenderte Signalbilder, damit sie nicht bei jedem Zeichnen neu entstehen. */
private val signalCache = HashMap<String, List<TileState>>()

private const val SIGNAL_PIXELS = 32

/**
 * Alle Bilder einer Kachel in Schaltreihenfolge: bei Signalen aus dem Katalog Halt, Fahrt, dann die uebrigen
 * Signalbilder; bei eigenen Symbolen die Zustaende. Leer ohne gewaehltes Symbol.
 */
internal fun tileStates(tile: TrackTile, library: List<SymbolDef>): List<TileState> {
    if (tile.symbol.isBlank()) return emptyList()
    if (tile.symbol.startsWith(SIGNAL_PREFIX)) {
        val system = SignalCatalog.system(tile.symbol.removePrefix(SIGNAL_PREFIX)) ?: return emptyList()
        return signalCache.getOrPut(system.id) {
            system.ordered.take(SignalAddressing.MAX_ASPECTS).map { TileState(it.name, it.meaning, SignalSymbols.render(system, it, SIGNAL_PIXELS)) }
        }
    }
    val def = library.firstOrNull { it.id == tile.symbol } ?: return emptyList()
    return def.states.take(SignalAddressing.MAX_ASPECTS).map { TileState(it.name, "", it.image) }
}

/**
 * Bild einer Kachel mit Symbol, passend zum Schaltzustand: Signale zeigen ihr aktuelles Signalbild
 * (unbekannt = Halt), andere Symbole ausgeschaltet den ersten, eingeschaltet den zweiten Zustand.
 */
internal fun symbolImage(
    tile: TrackTile,
    turnouts: Map<Int, Boolean>,
    aspects: Map<Int, Int>,
    library: List<SymbolDef>,
): PixelImage? {
    val states = tileStates(tile, library)
    if (states.isEmpty()) return null
    val address = tile.address
    val index = when {
        address == null -> 0
        tile.shape.kind == TileKind.SIGNAL -> aspects[address] ?: if (turnouts[address] == false) 1 else 0
        else -> if (turnouts[address] == true) 1 else 0
    }
    return states.getOrElse(index) { states.first() }.image
}

/** Auswahl des Signalbilds wie in iTrain: alle Bilder mit Vorschau und Adresse. */
@Composable
internal fun AspectMenu(tile: TrackTile, current: Int?, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val states = tileStates(tile, LocalSymbolLibrary.current)
    val base = tile.address ?: return
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        states.forEachIndexed { i, state ->
            DropdownMenuItem(
                leadingIcon = { SymbolImage(state.image, Modifier.size(36.dp)) },
                text = {
                    Column {
                        Text(state.name, fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal)
                        Text(
                            listOf(state.meaning, outputLabel(base, i)).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = { onPick(i) },
            )
        }
    }
}

internal fun outputLabel(base: Int, aspect: Int): String {
    val (address, red) = SignalAddressing.output(base, aspect)
    return "Adresse $address ${if (red) "rot" else "grün"}"
}

/** Welche Adresse jedes Signalbild schaltet, fuer die Eigenschaften im Editor. */
@Composable
internal fun AspectAddresses(tile: TrackTile) {
    val base = tile.address ?: return
    val states = tileStates(tile, LocalSymbolLibrary.current)
    if (states.size < 2) return
    Text(
        states.mapIndexed { i, s -> "${s.name}: ${outputLabel(base, i).removePrefix("Adresse ")}" }.joinToString("  ·  "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
            symbolImage(tile, emptyMap(), emptyMap(), library)?.let { SymbolImage(it, Modifier.size(22.dp)) }
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
                    "Signalbilder Deutschland, Schweiz, Niederlande",
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

