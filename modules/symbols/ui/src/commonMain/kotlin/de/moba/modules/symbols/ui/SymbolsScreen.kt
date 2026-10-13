package de.moba.modules.symbols.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Interests
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.moba.modules.symbols.api.PixelImage
import de.moba.modules.symbols.api.SYMBOLS_MODULE_ID
import de.moba.modules.symbols.api.SymbolCategory
import de.moba.modules.symbols.api.SymbolDef
import de.moba.modules.symbols.api.SymbolService
import de.moba.modules.symbols.api.SymbolState
import de.moba.ui.shell.LocalWideLayout
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.Moba
import de.moba.ui.shell.ModuleScreen
import de.moba.ui.shell.StatusPill
import kotlinx.coroutines.flow.MutableStateFlow

enum class SymbolsTab(val label: String) {
    OWN("Eigene Symbole"),
    SIGNALS("Signalbilder EU"),
}

/** Zustand des Symbol-Editors, bleibt beim Wechsel zwischen den Reitern erhalten. */
object SymbolsUiState {
    val tab = MutableStateFlow(SymbolsTab.OWN)
    val selected = MutableStateFlow<String?>(null)
    val stateIndex = MutableStateFlow(0)
    val tool = MutableStateFlow(DrawTool.PENCIL)
    val color = MutableStateFlow(0xFF000000.toInt())
    val grid = MutableStateFlow(true)
    val undo = MutableStateFlow<List<SymbolDef>>(emptyList())
    val redo = MutableStateFlow<List<SymbolDef>>(emptyList())
    val message = MutableStateFlow<String?>(null)
    val country = MutableStateFlow("DE")
    val system = MutableStateFlow<String?>(null)

    fun select(id: String?) {
        selected.value = id
        stateIndex.value = 0
        undo.value = emptyList()
        redo.value = emptyList()
    }
}

internal fun say(text: String?) {
    SymbolsUiState.message.value = text
}

private const val UNDO_LIMIT = 100

/** Aendert ein Symbol und merkt sich den alten Stand fuer "Rückgängig". */
internal fun SymbolService.edit(before: SymbolDef, after: SymbolDef) {
    if (before == after) return
    SymbolsUiState.undo.value = (SymbolsUiState.undo.value + before).takeLast(UNDO_LIMIT)
    SymbolsUiState.redo.value = emptyList()
    update(after)
}

/** Reiter "Symbole": eigene Pixelsymbole zeichnen (wie TrainController) und fertige Signalbilder der EU-Laender. */
object SymbolsScreen : ModuleScreen {
    override val moduleId = SYMBOLS_MODULE_ID
    override val title = "Symbole"
    override val icon = Icons.Outlined.Interests

    @Composable
    override fun Content(app: MobaApp) {
        val session by app.session.collectAsState()
        val service = session?.host?.service(SymbolService::class) ?: return
        val tab by SymbolsUiState.tab.collectAsState()
        val message by SymbolsUiState.message.collectAsState()

        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TopRow(service, tab)
            message?.let { text ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { say(null) }) { Text("OK") }
                    }
                }
            }
            when (tab) {
                SymbolsTab.OWN -> OwnSymbols(service, Modifier.weight(1f).fillMaxWidth())
                SymbolsTab.SIGNALS -> SignalBrowser(service, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopRow(service: SymbolService, tab: SymbolsTab) {
    val unsaved by service.unsaved.collectAsState()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SymbolsTab.entries.forEach { t ->
            FilterChip(selected = t == tab, onClick = { SymbolsUiState.tab.value = t }, label = { Text(t.label) })
        }
        Spacer(Modifier.width(8.dp))
        if (unsaved) StatusPill("Nicht gespeichert", Moba.colors.occupied)
        if (service.canSave) {
            Button(
                onClick = {
                    runCatching { service.save() }
                        .onSuccess { say("Symbole gespeichert.") }
                        .onFailure { say("Speichern fehlgeschlagen: ${it.message}") }
                },
                enabled = unsaved,
            ) {
                Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Speichern")
            }
        } else {
            OutlinedButton(onClick = {
                say("Eigene Symbole speichern gibt es ab der Pro-Lizenz (Burger-Menü, „Lizenz …“). Zeichnen und die Signalbilder bleiben kostenlos.")
            }) {
                Icon(Icons.Filled.Lock, null, tint = Moba.colors.locked, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Speichern ab Pro")
            }
        }
    }
}

@Composable
private fun OwnSymbols(service: SymbolService, modifier: Modifier) {
    val symbols by service.symbols.collectAsState()
    val selectedId by SymbolsUiState.selected.collectAsState()
    val symbol = symbols.firstOrNull { it.id == selectedId }
    val wide = LocalWideLayout.current

    if (wide) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(Modifier.width(250.dp).fillMaxHeight()) { SymbolList(service, symbols, selectedId) }
            if (symbol == null) {
                Panel(Modifier.weight(1f).fillMaxHeight()) { EmptyHint() }
            } else {
                Panel(Modifier.weight(1f).fillMaxHeight()) { DrawingArea(service, symbol, Modifier.fillMaxSize()) }
                Panel(Modifier.width(300.dp).fillMaxHeight()) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { SymbolProperties(service, symbol) }
                }
            }
        }
    } else if (symbol == null) {
        Panel(modifier) { SymbolList(service, symbols, selectedId) }
    } else {
        Panel(modifier) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { SymbolsUiState.select(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                    Text(symbol.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DrawingArea(service, symbol, Modifier.fillMaxWidth())
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                SymbolProperties(service, symbol)
            }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Box(Modifier.padding(12.dp)) { content() }
    }
}

@Composable
private fun EmptyHint() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Interests, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(8.dp))
        Text("Symbol links auswählen oder neu anlegen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Fertige Signale gibt es unter „Signalbilder EU“.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SymbolList(service: SymbolService, symbols: List<SymbolDef>, selectedId: String?) {
    Column(Modifier.fillMaxSize()) {
        Button(onClick = {
            val symbol = SymbolDef(service.newId(), "Neues Symbol")
            service.update(symbol)
            SymbolsUiState.select(symbol.id)
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Neues Symbol")
        }
        Spacer(Modifier.size(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(symbols, key = { it.id }) { symbol ->
                val selected = symbol.id == selectedId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { SymbolsUiState.select(symbol.id) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SymbolImage(symbol.states.first().image, Modifier.size(36.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(symbol.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(
                            "${symbol.category.label} · ${symbol.states.size} Zustände · ${symbol.size} px",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Werkzeugleiste, Zeichenflaeche und Farben. */
@Composable
private fun DrawingArea(service: SymbolService, symbol: SymbolDef, modifier: Modifier) {
    val tool by SymbolsUiState.tool.collectAsState()
    val color by SymbolsUiState.color.collectAsState()
    val grid by SymbolsUiState.grid.collectAsState()
    val undo by SymbolsUiState.undo.collectAsState()
    val redo by SymbolsUiState.redo.collectAsState()
    val index = SymbolsUiState.stateIndex.collectAsState().value.coerceIn(0, symbol.states.lastIndex)
    val state = symbol.states[index]
    val wide = LocalWideLayout.current

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ToolBar(tool) { SymbolsUiState.tool.value = it } }
            FilledIconToggleButton(checked = grid, onCheckedChange = { SymbolsUiState.grid.value = it }) {
                Icon(Icons.Outlined.GridOn, "Raster")
            }
            IconButton(enabled = undo.isNotEmpty(), onClick = {
                val previous = undo.last()
                SymbolsUiState.undo.value = undo.dropLast(1)
                SymbolsUiState.redo.value = redo + symbol
                service.update(previous)
            }) { Icon(Icons.AutoMirrored.Filled.Undo, "Rückgängig") }
            IconButton(enabled = redo.isNotEmpty(), onClick = {
                val next = redo.last()
                SymbolsUiState.redo.value = redo.dropLast(1)
                SymbolsUiState.undo.value = undo + symbol
                service.update(next)
            }) { Icon(Icons.AutoMirrored.Filled.Redo, "Wiederholen") }
        }
        Text(
            "Zustand „${state.name}“ · ${tool.label} · ${symbol.size} × ${symbol.size} Pixel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PixelCanvas(
            image = state.image,
            tool = tool,
            color = color,
            showGrid = grid,
            onCommit = { image -> service.edit(symbol, symbol.withState(index, state.copy(image = image))) },
            onPick = { picked ->
                SymbolsUiState.color.value = picked
                SymbolsUiState.tool.value = DrawTool.PENCIL
            },
            modifier = if (wide) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Palette(color) { SymbolsUiState.color.value = it }
    }
}

/** Name, Kategorie, Groesse, Zustaende, Spiegeln/Drehen/Verschieben und Vorschau. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SymbolProperties(service: SymbolService, symbol: SymbolDef) {
    val index = SymbolsUiState.stateIndex.collectAsState().value.coerceIn(0, symbol.states.lastIndex)
    val state = symbol.states[index]
    fun change(after: SymbolDef) = service.edit(symbol, after)
    fun changeImage(transform: (PixelImage) -> PixelImage) = change(symbol.withState(index, state.copy(image = transform(state.image))))

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = symbol.name,
            onValueChange = { change(symbol.copy(name = it)) },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Caption("Kategorie")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SymbolCategory.entries.forEach { c ->
                FilterChip(selected = c == symbol.category, onClick = { change(symbol.copy(category = c)) }, label = { Text(c.label) })
            }
        }
        Caption("Größe in Pixeln")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PixelImage.SIZES.forEach { s ->
                FilterChip(selected = s == symbol.size, onClick = { change(symbol.resized(s)) }, label = { Text("$s × $s") })
            }
        }

        Caption("Zustände")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            symbol.states.forEachIndexed { i, s ->
                val selected = i == index
                Column(
                    Modifier
                        .width(64.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { SymbolsUiState.stateIndex.value = i }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SymbolImage(s.image, Modifier.size(44.dp), checkerboard = true)
                    Text(s.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        OutlinedTextField(
            value = state.name,
            onValueChange = { change(symbol.withState(index, state.copy(name = it))) },
            label = { Text("Name des Zustands") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = {
                change(symbol.copy(states = symbol.states + SymbolState("Zustand ${symbol.states.size + 1}", PixelImage(symbol.size))))
                SymbolsUiState.stateIndex.value = symbol.states.size
            }) { Text("Neu") }
            TextButton(onClick = {
                val copy = state.copy(name = "${state.name} (Kopie)")
                change(symbol.copy(states = symbol.states.toMutableList().apply { add(index + 1, copy) }))
                SymbolsUiState.stateIndex.value = index + 1
            }) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Kopieren")
            }
            TextButton(enabled = symbol.states.size > 1, onClick = {
                change(symbol.copy(states = symbol.states.filterIndexed { i, _ -> i != index }))
                SymbolsUiState.stateIndex.value = (index - 1).coerceAtLeast(0)
            }) { Text("Entfernen") }
        }

        Caption("Bearbeiten")
        FlowRow {
            Action(Icons.Outlined.Flip, "Waagrecht spiegeln") { changeImage { it.mirrorHorizontal() } }
            Action(Icons.Outlined.Flip, "Senkrecht spiegeln", rotated = true) { changeImage { it.mirrorVertical() } }
            Action(Icons.Outlined.RotateRight, "Rechts drehen") { changeImage { it.rotateRight() } }
            Action(Icons.Filled.ArrowUpward, "Nach oben schieben") { changeImage { it.shift(0, -1) } }
            Action(Icons.Filled.ArrowDownward, "Nach unten schieben") { changeImage { it.shift(0, 1) } }
            Action(Icons.AutoMirrored.Filled.ArrowBack, "Nach links schieben") { changeImage { it.shift(-1, 0) } }
            Action(Icons.AutoMirrored.Filled.ArrowForward, "Nach rechts schieben") { changeImage { it.shift(1, 0) } }
            Action(Icons.Outlined.LayersClear, "Zustand leeren") { changeImage { it.clear() } }
        }

        Caption("Vorschau in Originalgröße")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            listOf(Color(0xFFF2F2F2), Color(0xFF2A2D31)).forEach { bg ->
                Box(Modifier.background(bg, MaterialTheme.shapes.small).padding(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        SymbolImage(state.image, Modifier.size(symbol.size.dp))
                        SymbolImage(state.image, Modifier.size((symbol.size * 2).dp))
                    }
                }
            }
        }

        HorizontalDivider()
        TextButton(onClick = {
            service.remove(symbol.id)
            SymbolsUiState.select(null)
            say("„${symbol.name}“ gelöscht.")
        }) {
            Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
            Text("Symbol löschen", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Action(icon: ImageVector, label: String, rotated: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, label, if (rotated) Modifier.rotate(90f) else Modifier)
    }
}
