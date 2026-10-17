package de.moba.modules.vehicles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import de.moba.modules.vehicles.api.TrainCategory
import de.moba.modules.vehicles.api.TrainSet
import de.moba.modules.vehicles.api.VEHICLES_MODULE_ID
import de.moba.modules.vehicles.api.VehicleGroup
import de.moba.modules.vehicles.api.VehicleService
import de.moba.ui.shell.EditorButtons
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.ModuleEditor
import de.moba.ui.shell.NEW_OBJECT
import de.moba.ui.shell.ObjectEditor
import de.moba.ui.shell.rememberEditorSelection

@Composable
private fun rememberVehicleService(app: MobaApp): VehicleService? {
    val session by app.session.collectAsState()
    return session?.host?.service(VehicleService::class)
}

/** Bearbeiten > Lokomotiven: dieselbe Liste und dasselbe Formular wie im Reiter Fahrzeuge. */
object LocosEditor : ModuleEditor {
    override val moduleId = VEHICLES_MODULE_ID
    override val key = "lokomotiven"
    override val title = "Lokomotiven"
    override val icon = Icons.Filled.Train
    override val order = 10

    @Composable
    override fun Content(app: MobaApp) {
        LaunchedEffect(Unit) {
            if (VehiclesUiState.group.value != VehicleGroup.LOCOS) {
                VehiclesUiState.group.value = VehicleGroup.LOCOS
                VehiclesUiState.draft.value = null
            }
        }
        VehiclesScreen.Content(app)
    }
}

/** Bearbeiten > Wagen: Gueter- und Personenwagengattungen. */
object WagonsEditor : ModuleEditor {
    override val moduleId = VEHICLES_MODULE_ID
    override val key = "wagen"
    override val title = "Wagen"
    override val icon = Icons.Outlined.Inventory2
    override val order = 20

    @Composable
    override fun Content(app: MobaApp) {
        LaunchedEffect(Unit) {
            if (VehiclesUiState.group.value == VehicleGroup.LOCOS) {
                VehiclesUiState.group.value = VehicleGroup.FREIGHT
                VehiclesUiState.draft.value = null
            }
        }
        VehiclesScreen.Content(app)
    }
}

/** Bearbeiten > Zuggattungen: Kuerzel wie ICE, RE, Gag mit Hoechstgeschwindigkeit. */
object CategoriesEditor : ModuleEditor {
    override val moduleId = VEHICLES_MODULE_ID
    override val key = "zuggattungen"
    override val title = "Zuggattungen"
    override val icon = Icons.Outlined.Category
    override val order = 30

    @Composable
    override fun Content(app: MobaApp) {
        val service = rememberVehicleService(app) ?: return
        val fleet by service.fleet.collectAsState()
        val (selected, select) = rememberEditorSelection(key)
        ObjectEditor(
            items = fleet.categories,
            id = { it.id },
            title = { listOf(it.code, it.name).filter(String::isNotBlank).joinToString(" – ") },
            subtitle = { c -> (if (c.passenger) "Personenzug" else "Güterzug") + (c.maxSpeed?.let { " · bis $it km/h" } ?: "") },
            selected = selected,
            onSelect = select,
            newLabel = "Neue Zuggattung",
            empty = "Noch keine Zuggattung, z. B. ICE, RE oder Gag (Ganzzug).",
        ) {
            val existing = fleet.categories.firstOrNull { it.id == selected }
            androidx.compose.runtime.key(selected) {
                CategoryForm(service, existing ?: TrainCategory(service.newObjectId("zg-"), ""), existing == null, select)
            }
        }
    }
}

@Composable
private fun CategoryForm(service: VehicleService, initial: TrainCategory, isNew: Boolean, onSelect: (String?) -> Unit) {
    var c by remember { mutableStateOf(initial) }
    var vmax by remember { mutableStateOf(initial.maxSpeed?.toString().orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }
    Text(if (isNew) "Neue Zuggattung" else c.code, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextInput("Kürzel", c.code, Modifier.width(140.dp)) { c = c.copy(code = it.take(8)) }
        TextInput("Name", c.name, Modifier.weight(1f)) { c = c.copy(name = it) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(c.passenger, { c = c.copy(passenger = true) }, label = { Text("Personenzug") })
        FilterChip(!c.passenger, { c = c.copy(passenger = false) }, label = { Text("Güterzug") })
    }
    NumberInput("Höchstgeschwindigkeit (km/h)", vmax, Modifier.fillMaxWidth()) { vmax = it }
    EditorButtons(
        message,
        onSave = {
            message = runCatching { service.saveCategory(c.copy(code = c.code.trim(), name = c.name.trim(), maxSpeed = vmax.toIntOrNull())) }
                .exceptionOrNull()?.message
            if (message == null) onSelect(c.id)
        },
        onDelete = if (isNew) null else ({
            service.deleteCategory(c.id)
            onSelect(null)
        }),
    )
}

/** Bearbeiten > Zuege: Lok(s) und Wagen mit Zuggattung; die Laenge ergibt sich aus den Fahrzeugen. */
object TrainsEditor : ModuleEditor {
    override val moduleId = VEHICLES_MODULE_ID
    override val key = "zuege"
    override val title = "Züge"
    override val icon = Icons.Outlined.ViewTimeline
    override val order = 40

    @Composable
    override fun Content(app: MobaApp) {
        val service = rememberVehicleService(app) ?: return
        val fleet by service.fleet.collectAsState()
        val (selected, select) = rememberEditorSelection(key)
        ObjectEditor(
            items = fleet.trains,
            id = { it.id },
            title = { it.name },
            subtitle = { t ->
                listOfNotNull(
                    fleet.categories.firstOrNull { it.id == t.categoryId }?.code,
                    t.locos.mapNotNull { id -> fleet.locos.firstOrNull { it.id == id }?.name }.joinToString(" + ").ifEmpty { null },
                    "${t.wagons.size} Wagen",
                    fleet.lengthMm(t).takeIf { it > 0 }?.let { "${it / 10} cm" },
                ).joinToString(" · ")
            },
            selected = selected,
            onSelect = select,
            newLabel = "Neuer Zug",
            empty = "Noch kein Zug. Ein Zug fasst Lok(s) und Wagen unter einer Zuggattung zusammen.",
        ) {
            val existing = fleet.trains.firstOrNull { it.id == selected }
            androidx.compose.runtime.key(selected) {
                TrainForm(service, existing ?: TrainSet(service.newObjectId("zug-"), ""), existing == null, select)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrainForm(service: VehicleService, initial: TrainSet, isNew: Boolean, onSelect: (String?) -> Unit) {
    val fleet by service.fleet.collectAsState()
    var t by remember { mutableStateOf(initial) }
    var message by remember { mutableStateOf<String?>(null) }
    Text(if (isNew) "Neuer Zug" else t.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    TextInput("Name", t.name) { t = t.copy(name = it) }
    Choice("Zuggattung", fleet.categories, fleet.categories.firstOrNull { it.id == t.categoryId }, { "${it.code} ${it.name}".trim() }, Modifier.fillMaxWidth(), optional = true) {
        t = t.copy(categoryId = it?.id.orEmpty())
    }
    Text("Lokomotiven", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (fleet.locos.isEmpty()) Text("Noch keine Lok unter Lokomotiven.", style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        fleet.locos.forEach { l ->
            val on = l.id in t.locos
            FilterChip(on, { t = t.copy(locos = if (on) t.locos - l.id else t.locos + l.id) }, label = { Text("${l.name} (${l.address})") })
        }
    }
    Text("Wagen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (fleet.wagons.isEmpty()) Text("Noch keine Wagengattung unter Wagen.", style = MaterialTheme.typography.bodySmall)
    fleet.wagons.forEach { w ->
        val n = t.wagons.count { it == w.id }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(listOf(w.code, w.name).filter(String::isNotBlank).joinToString(" "), Modifier.weight(1f))
            IconButton(onClick = { t = t.copy(wagons = t.wagons.toMutableList().also { it.remove(w.id) }) }, enabled = n > 0) {
                Icon(Icons.Outlined.Remove, "Einen ${w.code} weniger")
            }
            Text("$n", Modifier.width(28.dp), fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { t = t.copy(wagons = t.wagons + w.id) }) { Icon(Icons.Outlined.Add, "Einen ${w.code} mehr") }
        }
    }
    val length = fleet.lengthMm(t)
    if (length > 0) Text("Zuglänge: ${length / 10} cm", style = MaterialTheme.typography.bodyMedium)
    EditorButtons(
        message,
        onSave = {
            message = runCatching { service.saveTrain(t.copy(name = t.name.trim())) }.exceptionOrNull()?.message
            if (message == null) onSelect(t.id)
        },
        onDelete = if (isNew) null else ({
            service.deleteTrain(t.id)
            onSelect(null)
        }),
    )
}

/** Alle Editoren der Fahrzeugverwaltung fuer das Menue "Bearbeiten". */
val VehicleEditors = listOf(LocosEditor, WagonsEditor, CategoriesEditor, TrainsEditor)
