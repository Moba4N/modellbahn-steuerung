package de.moba.modules.trackplan.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.moba.modules.trackplan.api.AccessoryCommand
import de.moba.modules.trackplan.api.AccessoryProps
import de.moba.modules.trackplan.api.FeedbackList
import de.moba.modules.trackplan.api.FeedbackRef
import de.moba.modules.trackplan.api.MeasureConfig
import de.moba.modules.trackplan.api.SignalAddressing
import de.moba.modules.trackplan.api.TRACKPLAN_MODULE_ID
import de.moba.modules.trackplan.api.TableCommand
import de.moba.modules.trackplan.api.TableConfig
import de.moba.modules.trackplan.api.TileKind
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.api.TrackTile
import de.moba.modules.trackplan.api.aspectNames
import de.moba.modules.trackplan.api.displayName
import de.moba.ui.shell.LocalSoundSupport
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.ModuleEffect
import kotlinx.coroutines.launch

/** Reiter der Zubehoer-Eigenschaften wie in iTrain. */
private enum class AccessoryTab(val label: String) {
    GENERAL("Allgemein"),
    FILE("Datei"),
    DECODER("Decoder"),
    CONTROL("Steuerung"),
    ACCESSORY("Zubehör"),
    FEEDBACK("Rückmelder"),
    BLOCK("Block"),
    COMMANDS("Steuern"),
    MEASURE("Messung"),
}

private fun tabsFor(tile: TrackTile): List<AccessoryTab> = when (tile.shape.kind) {
    TileKind.SOUND -> listOf(AccessoryTab.FILE, AccessoryTab.DECODER, AccessoryTab.CONTROL)
    TileKind.ASPECT -> listOf(AccessoryTab.GENERAL, AccessoryTab.DECODER)
    TileKind.RELAY, TileKind.LIGHT, TileKind.GATE, TileKind.UNCOUPLER ->
        listOf(AccessoryTab.GENERAL, AccessoryTab.DECODER, AccessoryTab.CONTROL)
    TileKind.TURNTABLE, TileKind.TRANSFER -> listOf(
        AccessoryTab.GENERAL, AccessoryTab.DECODER, AccessoryTab.ACCESSORY,
        AccessoryTab.FEEDBACK, AccessoryTab.BLOCK, AccessoryTab.COMMANDS,
    )
    TileKind.MEASURE -> listOf(AccessoryTab.GENERAL, AccessoryTab.MEASURE)
    TileKind.CLOCK -> listOf(AccessoryTab.GENERAL)
    else -> emptyList()
}

/** Hat die Kachel Zubehoer-Reiter? Dann steht die Adresse im Reiter Decoder. */
internal fun hasAccessoryTabs(tile: TrackTile) = tabsFor(tile).isNotEmpty()

/** Zubehoer-Einstellungen der ausgewaehlten Kachel in Reitern. [set] speichert die geaenderte Kachel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccessoryTabs(tile: TrackTile, set: (TrackTile) -> Unit) {
    val tabs = tabsFor(tile)
    val selected by TrackPlanUiState.accessoryTab.collectAsState()
    val index = selected.coerceIn(0, tabs.lastIndex)
    Column(Modifier.fillMaxWidth()) {
        PrimaryScrollableTabRow(selectedTabIndex = index, edgePadding = 0.dp, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            tabs.forEachIndexed { i, tab ->
                Tab(selected = i == index, onClick = { TrackPlanUiState.accessoryTab.value = i }, text = { Text(tab.label) })
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tabs[index]) {
                AccessoryTab.GENERAL -> GeneralTab(tile, set)
                AccessoryTab.FILE -> FileTab(tile, set)
                AccessoryTab.DECODER -> DecoderTab(tile, set)
                AccessoryTab.CONTROL -> ControlTab(tile, set)
                AccessoryTab.ACCESSORY -> TableAccessoryTab(tile, set)
                AccessoryTab.FEEDBACK -> TableFeedbackTab(tile, set)
                AccessoryTab.BLOCK -> TableBlockTab(tile, set)
                AccessoryTab.COMMANDS -> TableCommandsTab(tile, set)
                AccessoryTab.MEASURE -> MeasureTab(tile, set)
            }
        }
    }
}

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Textfeld fuer eine Einstellung in [TrackTile.props]. */
@Composable
private fun PropField(
    label: String,
    tile: TrackTile,
    key: String,
    set: (TrackTile) -> Unit,
    modifier: Modifier = Modifier.width(220.dp),
    number: Boolean = false,
    placeholder: String = "",
) {
    OutlinedTextField(
        value = tile.prop(key),
        onValueChange = { v -> set(tile.withProp(key, (if (number) v.filter(Char::isDigit) else v).take(120))) },
        label = { Text(label, maxLines = 1) },
        placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder) }),
        singleLine = true,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = modifier,
    )
}

/** Melderliste mit Hinweis, welche Eintraege erkannt wurden. */
@Composable
private fun FeedbackField(label: String, tile: TrackTile, key: String, set: (TrackTile) -> Unit, single: Boolean = false) {
    Column {
        PropField(label, tile, key, set, Modifier.width(260.dp), placeholder = if (single) "z. B. 3.5" else "z. B. 1.3, 1.4")
        val text = tile.prop(key)
        if (text.isNotBlank()) {
            val parsed = FeedbackList.parse(text).let { if (single) it.take(1) else it }
            Hint(if (parsed.isEmpty()) "Format: Modul.Kontakt" else "Melder " + FeedbackList.format(parsed))
        }
    }
}

@Composable
private fun AddressField(label: String, tile: TrackTile, set: (TrackTile) -> Unit) {
    OutlinedTextField(
        value = tile.address?.toString().orEmpty(),
        onValueChange = { v -> set(tile.copy(address = v.filter(Char::isDigit).take(4).toIntOrNull())) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(220.dp),
    )
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------- einfache Zubehoere

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeneralTab(tile: TrackTile, set: (TrackTile) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PropField("Name", tile, AccessoryProps.NAME, set, Modifier.width(260.dp), placeholder = tile.shape.label)
        if (tile.shape.kind == TileKind.ASPECT) {
            PropField("Bilder, durch | getrennt", tile, AccessoryProps.ASPECTS, set, Modifier.width(360.dp), placeholder = "Aus|Blinken|Dauerlicht")
        }
        if (tile.shape.kind == TileKind.CLOCK) {
            PropField("Größe in Feldern (1–6)", tile, AccessoryProps.SIZE, set, Modifier.width(200.dp), number = true, placeholder = "2")
        }
    }
    when (tile.shape.kind) {
        TileKind.ASPECT -> Hint("${tile.aspectNames().size} Bilder, höchstens ${SignalAddressing.MAX_ASPECTS}. Im Betrieb wählt ein Klick das Bild aus einer Liste.")
        TileKind.GATE -> Hint("Eingeschaltet = Schranken geschlossen. Im Reiter Steuerung schließen Melder ihn automatisch.")
        TileKind.MEASURE -> Hint("Misst die Fahrzeit zwischen Start- und Endmelder (Reiter Messung) in beiden Richtungen und zeigt die Vorbildgeschwindigkeit.")
        TileKind.CLOCK -> Hint("Zeigt die Modellzeit. Im Betrieb stellt ein Klick die Uhr, den Zeitraffer und Start/Stopp.")
        TileKind.UNCOUPLER -> Hint("Ein Klick im Betrieb gibt einen Impuls; die Dauer steht im Reiter Steuerung.")
        else -> Hint("Ein Klick im Betrieb schaltet ein oder aus.")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileTab(tile: TrackTile, set: (TrackTile) -> Unit) {
    val sound = LocalSoundSupport.current
    val file = tile.prop(AccessoryProps.FILE)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PropField("Name", tile, AccessoryProps.NAME, set, Modifier.width(220.dp), placeholder = tile.shape.label)
        OutlinedButton(enabled = sound != null, onClick = {
            sound?.pick { name ->
                if (name != null) set(tile.withProp(AccessoryProps.FILE, name)) else say("Keine Klangdatei übernommen.")
            }
        }) {
            Icon(Icons.Filled.AudioFile, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (file.isEmpty()) "Datei wählen" else file, maxLines = 1)
        }
        FilledTonalButton(enabled = sound != null && file.isNotEmpty(), onClick = { sound?.play(file) }) {
            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Probehören")
        }
    }
    Hint(
        if (sound == null) "Klänge abspielen geht hier nicht."
        else "${sound.formats} Die App legt eine Kopie ab. Mit Adresse schaltet der Sound zusätzlich einen Decoder-Ausgang.",
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DecoderTab(tile: TrackTile, set: (TrackTile) -> Unit) {
    val kind = tile.shape.kind
    val table = tile.shape.isTable
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AddressField(
            when {
                table -> "Grundadresse"
                kind == TileKind.SOUND -> "Adresse (optional)"
                kind == TileKind.ASPECT -> "Grundadresse"
                else -> "Adresse"
            },
            tile, set,
        )
        if (table) {
            val pair = tile.prop(AccessoryProps.MODE) != TableConfig.MODE_SINGLE
            FilterChip(pair, onClick = { set(tile.withProp(AccessoryProps.MODE, "")) }, label = { Text("Adresspaare") })
            FilterChip(!pair, onClick = { set(tile.withProp(AccessoryProps.MODE, TableConfig.MODE_SINGLE)) }, label = { Text("Eine Adresse je Gleis") })
        } else if (kind != TileKind.ASPECT) {
            val inverted = tile.prop(AccessoryProps.INVERT) == "1"
            Text("„Ein“ ist", style = MaterialTheme.typography.bodyMedium)
            FilterChip(!inverted, onClick = { set(tile.withProp(AccessoryProps.INVERT, "")) }, label = { Text("rot / abzweigend") })
            FilterChip(inverted, onClick = { set(tile.withProp(AccessoryProps.INVERT, "1")) }, label = { Text("grün / gerade") })
        }
    }
    val base = tile.address
    when {
        base == null -> Hint("Ohne Adresse schaltet das Zubehör nichts an der Zentrale.")
        table -> {
            val c = TableConfig.of(tile)
            Hint(
                if (c.pairMode) "Gleis 0 = $base rot, Gleis 1 = $base grün, Gleis 2 = ${base + 1} rot … bis Adresse ${c.lastAddress(base)} (z. B. Märklin 7686, Fleischmann)."
                else "Gleis n = Adresse $base + n (grün), bis Adresse ${c.lastAddress(base)}.",
            )
        }
        kind == TileKind.ASPECT -> AspectAddresses(tile)
        else -> Hint("Belegt Adresse $base.")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlTab(tile: TrackTile, set: (TrackTile) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PropField(
            "Impulsdauer ms", tile, AccessoryProps.PULSE, set, Modifier.width(180.dp), number = true,
            placeholder = if (tile.shape.kind == TileKind.UNCOUPLER) "500" else "Dauer",
        )
        if (tile.shape.switchable) {
            FeedbackField("Einschalt-Melder", tile, AccessoryProps.ON_FEEDBACK, set)
            if (tile.shape.kind != TileKind.SOUND) FeedbackField("Ausschalt-Melder", tile, AccessoryProps.OFF_FEEDBACK, set)
        }
    }
    Hint(
        when (tile.shape.kind) {
            TileKind.UNCOUPLER -> "So lange bleibt der Entkuppler eingeschaltet."
            TileKind.SOUND -> "Wird ein Einschalt-Melder belegt, spielt der Sound, z. B. ein Pfiff vor dem Tunnel."
            TileKind.GATE -> "Belegt ein Zug einen Einschalt-Melder, schließen die Schranken. Sie öffnen, wenn er den Ausschalt-Melder verlassen hat " +
                "(ohne Ausschalt-Melder: sobald alle Einschalt-Melder frei sind)."
            else -> "Leer = Dauerbetrieb. Belegt ein Zug einen Einschalt-Melder, schaltet es ein; verlässt er den Ausschalt-Melder, wieder aus."
        },
    )
}

// ---------------------------------------------------------------- Drehscheibe und Schiebebuehne

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableAccessoryTab(tile: TrackTile, set: (TrackTile) -> Unit) {
    val turntable = tile.shape.kind == TileKind.TURNTABLE
    val c = TableConfig.of(tile)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PropField(
            "Positionen", tile, AccessoryProps.POSITIONS, set, Modifier.width(140.dp), number = true,
            placeholder = if (turntable) "24" else "4",
        )
        PropField("Angeschlossene Gleise", tile, AccessoryProps.TRACKS, set, Modifier.width(260.dp), placeholder = c.tracks.joinToString(", "))
        PropField(
            if (turntable) "Radius in Feldern" else "Länge in Feldern", tile, AccessoryProps.SIZE, set, Modifier.width(180.dp),
            number = true, placeholder = c.size.toString(),
        )
    }
    Hint(
        if (turntable) "${c.positions} Positionen, Position 0 oben, im Uhrzeigersinn. Gegenüber von Gleis n liegt Gleis n + ${c.positions / 2}. " +
            "Angeschlossen: ${c.tracks.joinToString(", ")}."
        else "${c.positions} Gleise, Gleis 0 ${if (c.vertical) "links" else "oben"}. Angeschlossen: ${c.tracks.joinToString(", ")}. Drehen um 90° stellt die Bühne quer.",
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableFeedbackTab(tile: TrackTile, set: (TrackTile) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FeedbackField("Fertig-Melder", tile, AccessoryProps.READY_FEEDBACK, set, single = true)
        PropField("Zeit je Position ms", tile, AccessoryProps.STEP_TIME, set, Modifier.width(200.dp), number = true, placeholder = "0")
    }
    Hint("Die Bühne gilt als in Bewegung, bis der Fertig-Melder belegt wird. Ohne Melder zählt die Zeit je Position.")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableBlockTab(tile: TrackTile, set: (TrackTile) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PropField("Blockname", tile, AccessoryProps.BLOCK, set, Modifier.width(220.dp), placeholder = tile.displayName())
        FeedbackField("Melder Bühne belegt", tile, AccessoryProps.BLOCK_FEEDBACK, set, single = true)
        SwitchLine("Nicht bewegen, solange belegt", tile.prop(AccessoryProps.BLOCK_LOCK) == "1") {
            set(tile.withProp(AccessoryProps.BLOCK_LOCK, if (it) "1" else ""))
        }
    }
    Hint("Der Block ist die Bühne selbst. Steht eine Lok darauf, meldet der Melder belegt.")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableCommandsTab(tile: TrackTile, set: (TrackTile) -> Unit) {
    val turntable = tile.shape.kind == TileKind.TURNTABLE
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (turntable) CommandField("180° drehen", tile, AccessoryProps.CMD_TURN, set)
        CommandField(if (turntable) "Schritt rechts" else "Schritt vor", tile, AccessoryProps.CMD_STEP_CW, set)
        CommandField(if (turntable) "Schritt links" else "Schritt zurück", tile, AccessoryProps.CMD_STEP_CCW, set)
    }
    Hint("Befehl als Adresse:r oder Adresse:g, z. B. 241:r. Leer = die Bühne fährt das Zielgleis über seine Gleisadresse direkt an.")
}

@Composable
private fun CommandField(label: String, tile: TrackTile, key: String, set: (TrackTile) -> Unit) {
    Column {
        PropField(label, tile, key, set, Modifier.width(200.dp), placeholder = "Adresse:r")
        val text = tile.prop(key)
        if (text.isNotBlank()) {
            Hint(AccessoryCommand.parse(text)?.let { "Adresse ${it.address} ${if (it.thrown) "rot" else "grün"}" } ?: "Format: Adresse:r oder Adresse:g")
        }
    }
}

// ---------------------------------------------------------------- Betrieb

/** Steuerung einer Drehscheibe/Schiebebuehne im Betrieb: Gleis anwaehlen, 180° drehen, Schritte. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TableControlDialog(service: TrackPlanService, tile: TrackTile, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val positions by service.tablePositions.collectAsState()
    val moving by service.tablesMoving.collectAsState()
    val occupied by service.occupied.collectAsState()
    val config = TableConfig.of(tile)
    val base = tile.address
    val current = base?.let { positions[it] }
    val turntable = tile.shape.kind == TileKind.TURNTABLE
    var error by remember { mutableStateOf<String?>(null) }
    fun run(action: suspend () -> Unit) {
        scope.launch {
            error = runCatching { action() }.exceptionOrNull()?.let { it.message ?: "Schalten nicht möglich" }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tile.displayName()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val busy = base != null && base in moving
                val blocked = config.blockFeedback?.let { it in occupied } == true
                Text(
                    when {
                        base == null -> "Keine Grundadresse eingetragen (Reiter Decoder)."
                        busy -> if (turntable) "Dreht zu Gleis $current …" else "Fährt zu Gleis $current …"
                        current == null -> "Position unbekannt"
                        else -> "Steht an Gleis $current"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (blocked) {
                    Hint(if (config.lockWhileOccupied) "Bühne belegt, bewegen gesperrt." else "Bühne belegt.")
                }
                Text("Gleis anfahren", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    config.tracks.forEach { track ->
                        FilterChip(
                            selected = track == current,
                            enabled = base != null,
                            onClick = { run { service.turnTo(tile, track) } },
                            label = { Text("Gleis $track") },
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (turntable) {
                        FilledTonalButton(enabled = base != null, onClick = { run { service.tableCommand(tile, TableCommand.TURN_180) } }) {
                            Icon(Icons.Filled.Sync, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(TableCommand.TURN_180.label)
                        }
                    }
                    OutlinedButton(enabled = base != null, onClick = { run { service.tableCommand(tile, TableCommand.STEP_CCW) } }) {
                        Icon(Icons.AutoMirrored.Filled.RotateLeft, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (turntable) "Schritt links" else "Schritt zurück")
                    }
                    OutlinedButton(enabled = base != null, onClick = { run { service.tableCommand(tile, TableCommand.STEP_CW) } }) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (turntable) "Schritt rechts" else "Schritt vor")
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

/** Spielt Klaenge des Gleisbilds ab, auch wenn gerade eine andere Ansicht offen ist (z. B. vom Melder ausgeloest). */
object TrackPlanSounds : ModuleEffect {
    override val moduleId = TRACKPLAN_MODULE_ID

    @Composable
    override fun Run(app: MobaApp) {
        val session by app.session.collectAsState()
        val service = session?.host?.service(TrackPlanService::class) ?: return
        val sound = LocalSoundSupport.current ?: return
        LaunchedEffect(service, sound) {
            service.sounds.collect { sound.play(it) }
        }
    }
}

/**
 * Merkt sich, welcher Zug in welchem Block steht, ueber Neustarts hinweg (Einstellung "gleisbild.zuege",
 * z. B. "1.3=12;2.5=7"). Beim Start setzt es die gemerkten Zuege wieder ein.
 */
object TrackPlanTrains : ModuleEffect {
    override val moduleId = TRACKPLAN_MODULE_ID
    private const val PREF = "gleisbild.zuege"

    fun encode(trains: Map<FeedbackRef, Int>) =
        trains.entries.sortedBy { "${it.key.first}.${it.key.second}" }.joinToString(";") { "${it.key.first}.${it.key.second}=${it.value}" }

    fun decode(text: String?): Map<FeedbackRef, Int> = text.orEmpty().split(';').mapNotNull { part ->
        val (block, address) = part.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        val ref = FeedbackList.parse(block).singleOrNull() ?: return@mapNotNull null
        ref to (address.trim().toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null)
    }.toMap()

    @Composable
    override fun Run(app: MobaApp) {
        val session by app.session.collectAsState()
        val service = session?.host?.service(TrackPlanService::class) ?: return
        LaunchedEffect(service) {
            if (service.trains.value.isEmpty()) {
                decode(app.preference(PREF)).forEach { (block, address) -> runCatching { service.placeTrain(block, address) } }
            }
            service.trains.collect { app.setPreference(PREF, encode(it)) }
        }
    }
}

// ---------------------------------------------------------------- Messstrecke

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeasureTab(tile: TrackTile, set: (TrackTile) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FeedbackField("Startmelder", tile, AccessoryProps.MEASURE_START, set, single = true)
        FeedbackField("Endmelder", tile, AccessoryProps.MEASURE_END, set, single = true)
        PropField("Länge in mm", tile, AccessoryProps.MEASURE_LENGTH, set, Modifier.width(160.dp), number = true, placeholder = "1000")
        PropField("Maßstab 1:", tile, AccessoryProps.SCALE, set, Modifier.width(140.dp), number = true, placeholder = "87")
    }
    val config = MeasureConfig.of(tile)
    Hint(
        if (config.start == null || config.end == null) "Start- und Endmelder eintragen, z. B. 1.3 und 1.4."
        else "Messstrecke ${config.lengthMm} mm, Maßstab 1:${config.scale}. Zählt ab dem ersten belegten Melder bis zum anderen, " +
            "höchstens ${MEASURE_LIMIT_S} s.",
    )
}

private const val MEASURE_LIMIT_S = 120
