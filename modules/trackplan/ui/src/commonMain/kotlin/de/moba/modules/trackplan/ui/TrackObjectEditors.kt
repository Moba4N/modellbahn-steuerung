package de.moba.modules.trackplan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.moba.modules.trackplan.api.BoosterDef
import de.moba.modules.trackplan.api.FeedbackList
import de.moba.modules.trackplan.api.FeedbackRef
import de.moba.modules.trackplan.api.StationDef
import de.moba.modules.trackplan.api.TRACKPLAN_MODULE_ID
import de.moba.modules.trackplan.api.TileKind
import de.moba.modules.trackplan.api.TrackPlan
import de.moba.modules.trackplan.api.TrackPlanService
import de.moba.modules.trackplan.api.TrackTile
import de.moba.modules.trackplan.api.block
import de.moba.modules.trackplan.api.blockName
import de.moba.modules.trackplan.api.displayName
import de.moba.ui.shell.EditorButtons
import de.moba.ui.shell.Moba
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.ModuleEditor
import de.moba.ui.shell.ObjectEditor
import de.moba.ui.shell.rememberEditorSelection
import de.moba.ui.shell.StatusPill
import kotlinx.coroutines.launch

/** Eine Kachel mit ihrem Gleisbild; [id] ist ueber alle Gleisbilder eindeutig. */
private class Placed(val plan: TrackPlan, val tile: TrackTile) {
    val id = "${plan.id}/${tile.x},${tile.y}"
}

@Composable
private fun rememberPlanService(app: MobaApp): TrackPlanService? {
    val session by app.session.collectAsState()
    return session?.host?.service(TrackPlanService::class)
}

private fun List<TrackPlan>.placed(filter: (TrackTile) -> Boolean) =
    flatMap { p -> p.tiles.filter(filter).sortedWith(compareBy({ it.y }, { it.x })).map { Placed(p, it) } }

/** Aenderungen an Kacheln wirken sofort, gespeichert werden sie wie im Gleisbild-Editor. */
@Composable
private fun SavePlansRow(service: TrackPlanService) {
    val unsaved by service.unsaved.collectAsState()
    if (!unsaved) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (service.canSave) {
            FilledTonalButton(onClick = { runCatching { service.save() } }) { Text("Gleisbilder speichern") }
        }
        Text(
            if (service.canSave) "Ungespeicherte Änderungen in den Gleisbildern." else "Speichern der Gleisbilder ab Basis.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Liste der Kacheln einer Art und darunter dieselben Felder wie im Gleisbild-Editor. */
@Composable
private fun TileListEditor(
    app: MobaApp,
    editorKey: String,
    filter: (TrackTile) -> Boolean,
    title: (TrackTile) -> String,
    state: @Composable (TrackPlanService, TrackTile) -> String,
    empty: String,
    extra: @Composable (TrackPlanService, Placed) -> Unit = { _, _ -> },
) {
    val service = rememberPlanService(app) ?: return
    val plans by service.plans.collectAsState()
    val (selected, select) = rememberEditorSelection(editorKey)
    val items = plans.placed(filter)
    // Zustandstexte vorher holen: sie lesen StateFlows und muessen in der Komposition gesammelt werden
    val states = items.associate { it.id to state(service, it.tile) }
    ObjectEditor(
        items = items,
        id = { it.id },
        title = { title(it.tile) },
        subtitle = { p -> listOf(p.plan.name, states[p.id].orEmpty()).filter(String::isNotBlank).joinToString(" · ") },
        selected = selected,
        onSelect = select,
        newLabel = null,
        empty = empty,
    ) {
        val current = items.firstOrNull { it.id == selected } ?: return@ObjectEditor
        Text(title(current.tile), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Gleisbild „${current.plan.name}“", color = MaterialTheme.colorScheme.onSurfaceVariant)
        extra(service, current)
        key(current.id) {
            TileFields(service, current.plan, current.tile) { select(null) }
        }
        SavePlansRow(service)
    }
}

private fun feedbackTitle(t: TrackTile) =
    t.displayName().takeIf { it != t.shape.label } ?: t.block()?.let { (m, c) -> "Rückmelder $m.$c" } ?: "Rückmelder ohne Kontakt"

/** Bearbeiten > Rueckmelder: alle Melder-Kacheln mit s88-Kontakt und Zustand. */
object FeedbackEditor : ModuleEditor {
    override val moduleId = TRACKPLAN_MODULE_ID
    override val key = "rueckmelder"
    override val title = "Rückmelder"
    override val icon = Icons.Outlined.Sensors
    override val order = 60

    @Composable
    override fun Content(app: MobaApp) = TileListEditor(
        app,
        key,
        filter = { it.shape.kind == TileKind.FEEDBACK },
        title = ::feedbackTitle,
        state = { service, tile ->
            val occupied by service.occupied.collectAsState()
            val b = tile.block()
            when {
                b == null -> "kein Kontakt"
                b in occupied -> "${b.first}.${b.second} belegt"
                else -> "${b.first}.${b.second} frei"
            }
        },
        empty = "Noch kein Rückmelder in den Gleisbildern.",
    )
}

/** Bearbeiten > Zubehoer: Weichen, Signale, Relais, Licht, Ton, Bahnuebergaenge, Drehscheiben ... */
object AccessoryEditor : ModuleEditor {
    override val moduleId = TRACKPLAN_MODULE_ID
    override val key = "zubehoer"
    override val title = "Zubehör"
    override val icon = Icons.AutoMirrored.Outlined.CallSplit
    override val order = 70

    @Composable
    override fun Content(app: MobaApp) = TileListEditor(
        app,
        key,
        filter = { it.shape.kind != TileKind.FEEDBACK && (it.shape.hasAddress || hasAccessoryTabs(it)) && it.shape.kind != TileKind.MEASURE && it.shape.kind != TileKind.CLOCK },
        title = { t -> t.displayName() + (t.address?.let { " ($it)" } ?: "") },
        state = { service, tile ->
            val turnouts by service.turnouts.collectAsState()
            val a = tile.address
            when {
                a == null -> "ohne Adresse"
                turnouts[a] == true -> "abzweigend / rot"
                turnouts[a] == false -> "gerade / grün"
                else -> ""
            }
        },
        empty = "Noch kein Zubehör in den Gleisbildern.",
    )
}

/** Bearbeiten > Bloecke: Name, Zug, Reservierung und Bahnhof jedes Blocks. */
object BlocksEditor : ModuleEditor {
    override val moduleId = TRACKPLAN_MODULE_ID
    override val key = "bloecke"
    override val title = "Blöcke"
    override val icon = Icons.Outlined.CropSquare
    override val order = 100

    @Composable
    override fun Content(app: MobaApp) {
        val service = rememberPlanService(app) ?: return
        val trains by service.trains.collectAsState()
        val reservations by service.reservations.collectAsState()
        val objects by service.objects.collectAsState()
        val layout by app.layout.layout.collectAsState()
        val plans by service.plans.collectAsState()
        TileListEditor(
            app,
            key,
            filter = { it.block() != null },
            title = { plans.blockName(it.block()!!) },
            state = { _, tile ->
                val b = tile.block()!!
                listOfNotNull(
                    trains[b]?.let { a -> layout.locos.firstOrNull { it.address == a }?.name ?: "Lok $a" },
                    reservations[b]?.let { "reserviert" },
                    objects.stations.firstOrNull { b in it.blocks }?.name,
                ).joinToString(" · ").ifEmpty { "frei" }
            },
            empty = "Noch kein Block. Jeder Rückmelder mit s88-Kontakt ist ein Block.",
        ) { s, placed ->
            val b = placed.tile.block() ?: return@TileListEditor
            BlockState(app, s, b)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockState(app: MobaApp, service: TrackPlanService, block: FeedbackRef) {
    val trains by service.trains.collectAsState()
    val reservations by service.reservations.collectAsState()
    val occupied by service.occupied.collectAsState()
    val objects by service.objects.collectAsState()
    val layout by app.layout.layout.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (block in occupied) StatusPill("belegt", Moba.colors.occupied) else StatusPill("frei", Moba.colors.signalGreen)
        reservations[block]?.let { StatusPill("reserviert: ${it.label}", Moba.colors.locked) }
        objects.stations.firstOrNull { block in it.blocks }?.let { StatusPill("Bahnhof ${it.name}", MaterialTheme.colorScheme.primary) }
        objects.boosters.firstOrNull { block in it.blocks }?.let { StatusPill("Booster ${it.name}", MaterialTheme.colorScheme.primary) }
    }
    Text("Zug im Block", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        layout.locos.forEach { loco ->
            FilterChip(trains[block] == loco.address, {
                message = runCatching {
                    if (trains[block] == loco.address) service.removeTrain(block) else {
                        if (trains[block] != null) service.removeTrain(block)
                        service.placeTrain(block, loco.address)
                    }
                }.exceptionOrNull()?.message
            }, label = { Text(loco.name) })
        }
        if (reservations[block] != null) {
            TextButton(onClick = { service.release(block) }) { Text("Reservierung aufheben") }
        }
    }
    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

/** Alle Bloecke aller Gleisbilder mit Namen, zum Zuordnen an Bahnhof oder Booster. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockChips(service: TrackPlanService, selected: List<FeedbackRef>, onChange: (List<FeedbackRef>) -> Unit) {
    val plans by service.plans.collectAsState()
    val blocks = plans.flatMap { p -> p.tiles.mapNotNull { it.block() } }.distinct().sortedWith(compareBy({ it.first }, { it.second }))
    Text("Blöcke", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (blocks.isEmpty()) Text("Noch kein Block in den Gleisbildern.", style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { b ->
            val on = b in selected
            FilterChip(on, { onChange(if (on) selected - b else selected + b) }, label = { Text(plans.blockName(b)) })
        }
    }
}

/** Bearbeiten > Bahnhoefe: Name, Kuerzel und Bloecke. */
object StationsEditor : ModuleEditor {
    override val moduleId = TRACKPLAN_MODULE_ID
    override val key = "bahnhoefe"
    override val title = "Bahnhöfe"
    override val icon = Icons.Outlined.Store
    override val order = 110

    @Composable
    override fun Content(app: MobaApp) {
        val service = rememberPlanService(app) ?: return
        val objects by service.objects.collectAsState()
        val plans by service.plans.collectAsState()
        val (selected, select) = rememberEditorSelection(key)
        ObjectEditor(
            items = objects.stations,
            id = { it.id },
            title = { s -> if (s.code.isBlank()) s.name else "${s.name} (${s.code})" },
            subtitle = { s -> s.blocks.joinToString(", ") { plans.blockName(it) }.ifEmpty { "ohne Blöcke" } },
            selected = selected,
            onSelect = select,
            newLabel = "Neuer Bahnhof",
            empty = "Noch kein Bahnhof. Ein Bahnhof fasst Blöcke unter einem Namen zusammen.",
        ) {
            val existing = objects.stations.firstOrNull { it.id == selected }
            key(selected) {
                var s by remember { mutableStateOf(existing ?: StationDef(service.newObjectId("bf-"), "")) }
                var message by remember { mutableStateOf<String?>(null) }
                Text(if (existing == null) "Neuer Bahnhof" else s.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Name", s.name, Modifier.weight(1f)) { s = s.copy(name = it.take(40)) }
                    Field("Kürzel", s.code, Modifier.width(120.dp)) { s = s.copy(code = it.take(6)) }
                }
                BlockChips(service, s.blocks) { s = s.copy(blocks = it) }
                EditorButtons(
                    message,
                    onSave = {
                        message = runCatching { service.saveStation(s.copy(name = s.name.trim(), code = s.code.trim())) }.exceptionOrNull()?.message
                        if (message == null) select(s.id)
                    },
                    onDelete = if (existing == null) null else ({
                        service.deleteStation(s.id)
                        select(null)
                    }),
                )
            }
        }
    }
}

/** Bearbeiten > Booster: Schaltadresse, Zustandsmelder (Kurzschluss) und versorgte Bloecke. */
object BoostersEditor : ModuleEditor {
    override val moduleId = TRACKPLAN_MODULE_ID
    override val key = "booster"
    override val title = "Booster"
    override val icon = Icons.Outlined.BatteryChargingFull
    override val order = 120

    @Composable
    override fun Content(app: MobaApp) {
        val service = rememberPlanService(app) ?: return
        val objects by service.objects.collectAsState()
        val occupied by service.occupied.collectAsState()
        val turnouts by service.turnouts.collectAsState()
        val scope = rememberCoroutineScope()
        val (selected, select) = rememberEditorSelection(key)
        ObjectEditor(
            items = objects.boosters,
            id = { it.id },
            title = { it.name },
            subtitle = { b -> (if (b.isOn(occupied, turnouts)) "an" else "aus / Kurzschluss") + " · ${b.blocks.size} Blöcke" },
            selected = selected,
            onSelect = select,
            newLabel = "Neuer Booster",
            empty = "Noch kein Booster. Ohne Booster gilt die Gleisspannung der Zentrale für die ganze Anlage.",
        ) {
            val existing = objects.boosters.firstOrNull { it.id == selected }
            key(selected) {
                var b by remember { mutableStateOf(existing ?: BoosterDef(service.newObjectId("bo-"), "")) }
                var address by remember { mutableStateOf(b.address?.toString().orEmpty()) }
                var state by remember { mutableStateOf(b.stateFeedback?.let { FeedbackList.format(listOf(it)) }.orEmpty()) }
                var message by remember { mutableStateOf<String?>(null) }
                Text(if (existing == null) "Neuer Booster" else b.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (existing != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (existing.isOn(occupied, turnouts)) StatusPill("an", Moba.colors.signalGreen) else StatusPill("aus / Kurzschluss", Moba.colors.alarm)
                        if (existing.address != null) {
                            OutlinedButton(onClick = { scope.launch { message = runCatching { service.setBooster(existing.id, true) }.exceptionOrNull()?.message } }) { Text("Einschalten") }
                            OutlinedButton(onClick = { scope.launch { message = runCatching { service.setBooster(existing.id, false) }.exceptionOrNull()?.message } }) { Text("Ausschalten") }
                        }
                    }
                }
                Field("Name", b.name, Modifier.fillMaxWidth()) { b = b.copy(name = it.take(40)) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Schaltadresse (optional)", address, Modifier.weight(1f), KeyboardType.Number) { address = it.filter(Char::isDigit).take(5) }
                    Field("Zustandsmelder, z. B. 4.16", state, Modifier.weight(1f), KeyboardType.Decimal) { state = it.filter { c -> c.isDigit() || c == '.' }.take(7) }
                }
                Text(
                    "Der Zustandsmelder meldet „belegt“, wenn der Booster abgeschaltet hat (Kurzschluss). Mit Schaltadresse lässt er sich hier und über Aktionen ein- und ausschalten (gerade = an).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BlockChips(service, b.blocks) { b = b.copy(blocks = it) }
                EditorButtons(
                    message,
                    onSave = {
                        val next = b.copy(name = b.name.trim(), address = address.toIntOrNull(), stateFeedback = FeedbackList.parse(state).firstOrNull())
                        message = runCatching { service.saveBooster(next) }.exceptionOrNull()?.message
                        if (message == null) select(next.id)
                    },
                    onDelete = if (existing == null) null else ({
                        service.deleteBooster(b.id)
                        select(null)
                    }),
                )
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) =
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier,
    )

/** Alle Editoren des Gleisbilds fuer das Menue "Bearbeiten". */
val TrackObjectEditors = listOf(FeedbackEditor, AccessoryEditor, BlocksEditor, StationsEditor, BoostersEditor)
