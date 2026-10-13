package de.moba.modules.symbols.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.moba.modules.symbols.api.COUNTRY_NAMES
import de.moba.modules.symbols.api.PixelImage
import de.moba.modules.symbols.api.SignalAspect
import de.moba.modules.symbols.api.SignalCatalog
import de.moba.modules.symbols.api.SignalSymbols
import de.moba.modules.symbols.api.SignalSystem
import de.moba.modules.symbols.api.SymbolService
import de.moba.ui.shell.Moba
import kotlinx.coroutines.delay

/** Blinktakt der Signallampen in der Vorschau. */
private const val FLASH_MS = 500L

/**
 * Fertige Signalbilder der EU-Laender (und der Schweiz): Land waehlen, Signalsystem waehlen,
 * alle Signalbilder ansehen und als eigenes Symbol uebernehmen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SignalBrowser(service: SymbolService, modifier: Modifier) {
    val country by SymbolsUiState.country.collectAsState()
    val systemId by SymbolsUiState.system.collectAsState()
    val systems = SignalCatalog.forCountry(country)
    val system = systems.firstOrNull { it.id == systemId } ?: systems.firstOrNull()
    var flashOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(FLASH_MS)
            flashOn = !flashOn
        }
    }

    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Land", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SignalCatalog.countries.forEach { code ->
                    FilterChip(
                        selected = code == country,
                        onClick = {
                            SymbolsUiState.country.value = code
                            SymbolsUiState.system.value = null
                        },
                        label = { Text(COUNTRY_NAMES[code] ?: code) },
                    )
                }
            }
            val without = SignalCatalog.countriesWithoutRailway
            if (without.isNotEmpty()) {
                Text(
                    "Ohne Eisenbahnnetz und daher ohne Signale: ${without.values.joinToString()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (systems.size > 1) {
                Text("Signalsystem", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    systems.forEach { s ->
                        FilterChip(selected = s == system, onClick = { SymbolsUiState.system.value = s.id }, label = { Text(s.name) })
                    }
                }
            }

            if (system != null) SystemDetails(service, system, flashOn)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SystemDetails(service: SymbolService, system: SignalSystem, flashOn: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(system.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${system.kind.label} · ${system.aspects.size} Signalbilder",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = {
            val symbol = SignalSymbols.toSymbol(system, service.newId(), PixelImage.DEFAULT_SIZE)
            service.update(symbol)
            SymbolsUiState.select(symbol.id)
            SymbolsUiState.tab.value = SymbolsTab.OWN
            say("„${system.name}“ als Symbol übernommen, ein Zustand je Signalbild. Du kannst es jetzt anpassen.")
        }) {
            Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Als Symbol übernehmen")
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        system.aspects.forEach { aspect -> AspectCard(system, aspect, flashOn) }
    }
    Text(
        if (system.unchecked) {
            "Farben und Bedeutungen nach Signalbuch, die Lampenanordnung ist vereinfacht und noch nicht anhand von Vorbildfotos geprüft."
        } else {
            "Farben und Bedeutungen nach Signalbuch geprüft, die Lampenanordnung ist vereinfacht."
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (system.unchecked) Moba.colors.occupied else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (system.source.isNotBlank()) {
        Text("Quelle: ${system.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AspectCard(system: SignalSystem, aspect: SignalAspect, flashOn: Boolean) {
    val image = remember(system, aspect, flashOn) { SignalSymbols.render(system, aspect, PixelImage.SIZES.last(), flashOn) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.width(150.dp)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SymbolImage(image, Modifier.size(96.dp))
            Spacer(Modifier.size(6.dp))
            Text(aspect.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(aspect.meaning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val notes = buildList {
                if (aspect.lit.any { it.flash }) add("blinkt")
                aspect.indicator?.let { add("Anzeiger „${it.text}“ ${it.color.label.lowercase()}") }
            }
            if (notes.isNotEmpty()) {
                Text(notes.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
