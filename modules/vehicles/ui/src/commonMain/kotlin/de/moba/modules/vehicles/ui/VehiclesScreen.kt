package de.moba.modules.vehicles.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsRailway
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.moba.modules.vehicles.api.DecoderProtocol
import de.moba.modules.vehicles.api.Epoch
import de.moba.modules.vehicles.api.Fleet
import de.moba.modules.vehicles.api.LocoRecord
import de.moba.modules.vehicles.api.Traction
import de.moba.modules.vehicles.api.VEHICLES_MODULE_ID
import de.moba.modules.vehicles.api.VehicleGroup
import de.moba.modules.vehicles.api.VehicleLimitException
import de.moba.modules.vehicles.api.VehicleService
import de.moba.modules.vehicles.api.WagonCategory
import de.moba.modules.vehicles.api.WagonClass
import de.moba.modules.vehicles.api.WagonTemplates
import de.moba.ui.shell.LocalWideLayout
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.Moba
import de.moba.ui.shell.ModuleScreen
import de.moba.ui.shell.StatusPill
import kotlinx.coroutines.flow.MutableStateFlow

/** Was gerade im Formular bearbeitet wird; [isNew] = noch nicht gespeichert. */
sealed interface VehicleDraft {
    val isNew: Boolean

    data class Loco(val record: LocoRecord, override val isNew: Boolean) : VehicleDraft
    data class Wagon(val record: WagonClass, override val isNew: Boolean) : VehicleDraft
}

/** Zustand der Fahrzeug-Ansicht, bleibt beim Wechsel zwischen den Reitern erhalten. */
object VehiclesUiState {
    val group = MutableStateFlow(VehicleGroup.LOCOS)
    val draft = MutableStateFlow<VehicleDraft?>(null)
    val message = MutableStateFlow<String?>(null)
}

private fun say(text: String?) {
    VehiclesUiState.message.value = text
}

/** Reiter "Fahrzeuge": Loks, Gueterwagen- und Personenwagengattungen anlegen und bearbeiten. */
object VehiclesScreen : ModuleScreen {
    override val moduleId = VEHICLES_MODULE_ID
    override val title = "Fahrzeuge"
    override val icon = Icons.Outlined.DirectionsRailway

    @Composable
    override fun Content(app: MobaApp) {
        val session by app.session.collectAsState()
        val service = session?.host?.service(VehicleService::class) ?: return
        val fleet by service.fleet.collectAsState()
        val group by VehiclesUiState.group.collectAsState()
        val draft by VehiclesUiState.draft.collectAsState()
        val wide = LocalWideLayout.current

        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GroupChips(service, fleet, group)
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    VehicleList(service, fleet, group, Modifier.weight(1f).fillMaxHeight())
                    Surface(
                        Modifier.width(420.dp).fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        val d = draft
                        if (d == null) {
                            EmptyEditor(group)
                        } else {
                            DraftEditor(service, d)
                        }
                    }
                }
            } else {
                val d = draft
                if (d == null) {
                    VehicleList(service, fleet, group, Modifier.weight(1f).fillMaxWidth())
                } else {
                    Surface(
                        Modifier.weight(1f).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.large,
                    ) { DraftEditor(service, d) }
                }
            }
        }
    }
}

private fun VehicleGroup.icon(): ImageVector = when (this) {
    VehicleGroup.LOCOS -> Icons.Filled.Train
    VehicleGroup.FREIGHT -> Icons.Outlined.Inventory2
    VehicleGroup.PASSENGER -> Icons.Outlined.Weekend
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupChips(service: VehicleService, fleet: Fleet, group: VehicleGroup) {
    val message by VehiclesUiState.message.collectAsState()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        VehicleGroup.entries.forEach { g ->
            FilterChip(
                selected = g == group,
                onClick = {
                    VehiclesUiState.group.value = g
                    VehiclesUiState.draft.value = null
                    say(null)
                },
                label = { Text("${g.label} (${fleet.count(g)})") },
                leadingIcon = { Icon(g.icon(), null, modifier = Modifier.size(18.dp)) },
            )
        }
        Box(Modifier.padding(top = 4.dp)) {
            val limit = service.limit
            if (limit == null) {
                StatusPill("Pro: unbegrenzt viele Fahrzeuge", Moba.colors.signalGreen)
            } else {
                StatusPill("Kostenlos: ${fleet.count(group)} von $limit gespeichert", Moba.colors.locked)
            }
        }
    }
    message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun VehicleList(service: VehicleService, fleet: Fleet, group: VehicleGroup, modifier: Modifier) {
    val draft by VehiclesUiState.draft.collectAsState()
    val selectedId = when (val d = draft) {
        is VehicleDraft.Loco -> d.record.id
        is VehicleDraft.Wagon -> d.record.id
        null -> null
    }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NewButton(service, group)
        if (fleet.count(group) == 0) {
            Text(
                when (group) {
                    VehicleGroup.LOCOS -> "Noch keine Lok angelegt. Angelegte Loks erscheinen mit Namen im Fahrpult."
                    else -> "Noch keine ${group.singular} angelegt. Vorlagen wie Eaos oder Bm 235 helfen beim Start."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (group == VehicleGroup.LOCOS) {
            fleet.locos.forEach { loco ->
                VehicleCard(
                    icon = Icons.Filled.Train,
                    title = loco.name,
                    subtitle = listOf(
                        "Adresse ${loco.address}",
                        loco.series,
                        loco.railway,
                        loco.epoch?.let { "Epoche ${it.name}" },
                        loco.traction?.label,
                    ).filterNot { it.isNullOrBlank() }.joinToString(" · "),
                    trailing = null,
                    selected = loco.id == selectedId,
                ) {
                    say(null)
                    VehiclesUiState.draft.value = VehicleDraft.Loco(loco, isNew = false)
                }
            }
        } else {
            fleet.wagons(group).forEach { wagon ->
                VehicleCard(
                    icon = group.icon(),
                    title = wagon.code + if (wagon.name.isNotBlank()) "  ${wagon.name}" else "",
                    subtitle = listOf(
                        wagon.category?.label,
                        wagon.railway,
                        wagon.epoch?.let { "Epoche ${it.name}" },
                        wagon.lengthMm?.let { "$it mm" },
                    ).filterNot { it.isNullOrBlank() }.joinToString(" · "),
                    trailing = "× ${wagon.count}",
                    selected = wagon.id == selectedId,
                ) {
                    say(null)
                    VehiclesUiState.draft.value = VehicleDraft.Wagon(wagon, isNew = false)
                }
            }
        }
    }
}

@Composable
private fun NewButton(service: VehicleService, group: VehicleGroup) {
    if (service.canAdd(group)) {
        Button(onClick = {
            say(null)
            val id = service.newId(group)
            VehiclesUiState.draft.value = when (group) {
                VehicleGroup.LOCOS -> VehicleDraft.Loco(LocoRecord(id, "", 3), isNew = true)
                else -> VehicleDraft.Wagon(WagonClass(id, group, ""), isNew = true)
            }
        }) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("${group.singular} anlegen")
        }
    } else {
        OutlinedButton(onClick = {
            say("Kostenlos lassen sich ${service.limit} ${group.label} speichern. Mit Pro sind es beliebig viele.")
        }) {
            Icon(Icons.Filled.Lock, null, tint = Moba.colors.locked, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Weitere ${group.label} ab Pro")
        }
    }
}

@Composable
private fun VehicleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = if (selected) BorderStroke(2.dp, primary) else null,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = primary, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun EmptyEditor(group: VehicleGroup) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Wähle links einen Eintrag oder lege eine neue ${group.singular} an.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- Formular

@Composable
private fun DraftEditor(service: VehicleService, draft: VehicleDraft) {
    val key = when (draft) {
        is VehicleDraft.Loco -> draft.record.id
        is VehicleDraft.Wagon -> draft.record.id
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (draft) {
            is VehicleDraft.Loco -> androidx.compose.runtime.key(key) { LocoForm(service, draft) }
            is VehicleDraft.Wagon -> androidx.compose.runtime.key(key) { WagonForm(service, draft) }
        }
    }
}

@Composable
private fun FormHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!LocalWideLayout.current) {
            IconButton(onClick = { VehiclesUiState.draft.value = null }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LocoForm(service: VehicleService, draft: VehicleDraft.Loco) {
    var r by remember { mutableStateOf(draft.record) }
    var address by remember { mutableStateOf(draft.record.address.toString()) }
    var vmax by remember { mutableStateOf(draft.record.vmax?.toString().orEmpty()) }
    var length by remember { mutableStateOf(draft.record.lengthMm?.toString().orEmpty()) }
    var functions by remember {
        mutableStateOf(draft.record.functions.toSortedMap().entries.joinToString("\n") { "F${it.key} ${it.value}" })
    }

    FormHeader(if (draft.isNew) "Neue Lok" else r.name.ifBlank { "Lok" })
    TextInput("Name", r.name) { r = r.copy(name = it) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberInput("Adresse", address, Modifier.weight(1f)) { address = it }
        Choice("Protokoll", DecoderProtocol.entries, r.protocol, { it.label }, Modifier.weight(1f)) {
            r = r.copy(protocol = it ?: DecoderProtocol.DCC)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextInput("Baureihe", r.series, Modifier.weight(1f)) { r = r.copy(series = it) }
        TextInput("Bahnverwaltung", r.railway, Modifier.weight(1f)) { r = r.copy(railway = it) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Choice("Epoche", Epoch.entries, r.epoch, { it.label }, Modifier.weight(1f), optional = true) { r = r.copy(epoch = it) }
        Choice("Antrieb", Traction.entries, r.traction, { it.label }, Modifier.weight(1f), optional = true) {
            r = r.copy(traction = it)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberInput("Vmax (km/h)", vmax, Modifier.weight(1f)) { vmax = it }
        NumberInput("Länge (mm)", length, Modifier.weight(1f)) { length = it }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextInput("Hersteller", r.manufacturer, Modifier.weight(1f)) { r = r.copy(manufacturer = it) }
        TextInput("Artikelnummer", r.articleNumber, Modifier.weight(1f)) { r = r.copy(articleNumber = it) }
    }
    OutlinedTextField(
        value = functions,
        onValueChange = { functions = it },
        label = { Text("Funktionen, je Zeile z. B. „F0 Licht“") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    TextInput("Notiz", r.notes, singleLine = false) { r = r.copy(notes = it) }

    FormButtons(
        isNew = draft.isNew,
        onSave = {
            service.saveLoco(
                r.copy(
                    name = r.name.trim(),
                    address = address.toIntOrNull() ?: 0,
                    vmax = vmax.toIntOrNull(),
                    lengthMm = length.toIntOrNull(),
                    functions = parseFunctions(functions),
                ),
            )
        },
        onDelete = { service.deleteLoco(r.id) },
    )
}

/** "F0 Licht" oder "0 Licht" je Zeile; Zeilen ohne Nummer werden ignoriert. */
internal fun parseFunctions(text: String): Map<Int, String> = text.lines().mapNotNull { line ->
    val m = Regex("""^\s*[Ff]?(\d{1,2})\s*[:=]?\s*(.*)$""").find(line) ?: return@mapNotNull null
    val n = m.groupValues[1].toInt()
    val label = m.groupValues[2].trim()
    if (n > 68 || label.isEmpty()) null else n to label
}.toMap()

@Composable
private fun WagonForm(service: VehicleService, draft: VehicleDraft.Wagon) {
    var r by remember { mutableStateOf(draft.record) }
    var length by remember { mutableStateOf(draft.record.lengthMm?.toString().orEmpty()) }
    var count by remember { mutableStateOf(draft.record.count.toString()) }
    val group = r.group

    FormHeader(if (draft.isNew) "Neue ${group.singular}" else r.code.ifBlank { group.singular })
    if (draft.isNew) {
        Choice("Vorlage", WagonTemplates.of(group), null, { "${it.code} – ${it.name}" }, Modifier.fillMaxWidth()) { t ->
            if (t != null) {
                r = t.copy(id = r.id, count = r.count)
                length = t.lengthMm?.toString().orEmpty()
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextInput("Gattungszeichen", r.code, Modifier.weight(1f)) { r = r.copy(code = it) }
        NumberInput("Anzahl", count, Modifier.width(110.dp)) { count = it }
    }
    TextInput("Bezeichnung", r.name) { r = r.copy(name = it) }
    Choice("Bauart", WagonCategory.of(group), r.category, { "${it.label} (${it.code})" }, Modifier.fillMaxWidth(), optional = true) {
        r = r.copy(category = it)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextInput("Bahnverwaltung", r.railway, Modifier.weight(1f)) { r = r.copy(railway = it) }
        Choice("Epoche", Epoch.entries, r.epoch, { it.label }, Modifier.weight(1f), optional = true) { r = r.copy(epoch = it) }
    }
    NumberInput("Länge über Puffer (mm)", length, Modifier.fillMaxWidth()) { length = it }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextInput("Hersteller", r.manufacturer, Modifier.weight(1f)) { r = r.copy(manufacturer = it) }
        TextInput("Artikelnummer", r.articleNumber, Modifier.weight(1f)) { r = r.copy(articleNumber = it) }
    }
    TextInput("Notiz", r.notes, singleLine = false) { r = r.copy(notes = it) }

    FormButtons(
        isNew = draft.isNew,
        onSave = {
            service.saveWagon(r.copy(code = r.code.trim(), lengthMm = length.toIntOrNull(), count = count.toIntOrNull() ?: 0))
        },
        onDelete = { service.deleteWagon(r.id) },
    )
}

@Composable
private fun FormButtons(isNew: Boolean, onSave: () -> Any, onDelete: () -> Unit) {
    Spacer(Modifier.size(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = {
            try {
                onSave()
                VehiclesUiState.draft.value = null
                say(null)
            } catch (e: VehicleLimitException) {
                say(e.message)
            } catch (e: IllegalArgumentException) {
                say(e.message)
            }
        }) {
            Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Speichern")
        }
        TextButton(onClick = {
            VehiclesUiState.draft.value = null
            say(null)
        }) { Text("Abbrechen") }
        Spacer(Modifier.weight(1f))
        if (!isNew) {
            IconButton(onClick = {
                onDelete()
                VehiclesUiState.draft.value = null
                say(null)
            }) { Icon(Icons.Outlined.Delete, "Löschen", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun TextInput(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(if (singleLine) 60 else 500)) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        modifier = modifier,
    )
}

@Composable
private fun NumberInput(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(5)) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** Auswahlfeld mit Menue; mit [optional] gibt es einen Eintrag "keine Angabe". */
@Composable
private fun <T> Choice(
    label: String,
    options: List<T>,
    selected: T?,
    text: (T) -> String,
    modifier: Modifier,
    optional: Boolean = false,
    onSelect: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = selected?.let(text) ?: "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Das Textfeld ist nur Anzeige; ein Klick darauf oeffnet das Menue.
        Box(Modifier.matchParentSize().clip(MaterialTheme.shapes.extraSmall).clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (optional) {
                DropdownMenuItem(text = { Text("keine Angabe") }, onClick = {
                    expanded = false
                    onSelect(null)
                })
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text(option)) },
                    leadingIcon = {
                        if (option == selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                        else Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
