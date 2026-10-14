package de.moba.modules.driving.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.moba.core.model.Loco
import de.moba.core.station.Direction
import de.moba.core.station.LocoState
import de.moba.modules.driving.api.DRIVING_MODULE_ID
import de.moba.modules.driving.api.DrivingService
import de.moba.ui.shell.LocalPanelVisible
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.ModulePanel
import de.moba.ui.shell.ModuleScreen
import de.moba.ui.shell.SectionCard
import kotlinx.coroutines.flow.MutableStateFlow

/** Gemeinsamer Zustand von Lokliste und Fahrpult: welche Lok gerade gesteuert wird. */
object DrivingUiState {
    val selected = MutableStateFlow<Int?>(null)
    val message = MutableStateFlow<String?>(null)
}

/** Reiter "Fahren": alle Loks als Karten. Auf dem Handy steht das Fahrpult direkt darunter. */
object DrivingScreen : ModuleScreen {
    override val moduleId = DRIVING_MODULE_ID
    override val title = "Fahren"
    override val icon = Icons.Outlined.Speed

    @Composable
    override fun Content(app: MobaApp) {
        val session by app.session.collectAsState()
        val service = session?.host?.service(DrivingService::class) ?: return
        val panelVisible = LocalPanelVisible.current
        val selected by DrivingUiState.selected.collectAsState()
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Ohne rechte Leiste (Handy oder ausgeblendet): Fahrpult oben zeigen.
            if (!panelVisible) selected?.let { ThrottleFor(app, it, service) }
            LocoGrid(app, service)
        }
    }
}

/** Fahrpult in der rechten Leiste (Tablet quer, PC). */
object DrivingPanel : ModulePanel {
    override val moduleId = DRIVING_MODULE_ID

    @Composable
    override fun Content(app: MobaApp) {
        val session by app.session.collectAsState()
        val service = session?.host?.service(DrivingService::class) ?: return
        val selected by DrivingUiState.selected.collectAsState()
        Text("Fahrpult", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        val address = selected
        if (address == null) {
            Text(
                "Wähle links eine Lok aus, um sie hier zu steuern.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ThrottleFor(app, address, service)
        }
    }
}

@Composable
private fun ThrottleFor(app: MobaApp, address: Int, service: DrivingService) {
    val states by service.locos.collectAsState()
    val layout by app.layout.layout.collectAsState()
    Throttle(
        name = layout.locos.firstOrNull { it.address == address }?.name ?: "Lok $address",
        state = states[address] ?: LocoState(address),
        service = service,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocoGrid(app: MobaApp, service: DrivingService) {
    val layout by app.layout.layout.collectAsState()
    val states by service.locos.collectAsState()
    val selected by DrivingUiState.selected.collectAsState()
    val message by DrivingUiState.message.collectAsState()
    var newAddress by remember { mutableStateOf("") }

    // Loks aus der Anlage plus Loks, die gerade an der Zentrale fahren.
    val addresses = (layout.locos.map { it.address } + states.keys).distinct().sorted()
    val names = layout.locos.associate { it.address to it.name }

    SectionCard(
        title = "Loks",
        icon = Icons.Filled.Train,
        trailing = {
            service.locoLimit?.let {
                Text(
                    "Kostenlos: ${minOf(addresses.size, it)} von $it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            addresses.forEach { address ->
                LocoCard(
                    name = names[address] ?: "Lok $address",
                    state = states[address] ?: LocoState(address),
                    selected = selected == address,
                    onClick = { DrivingUiState.selected.value = address },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = newAddress,
                onValueChange = { newAddress = it.filter(Char::isDigit).take(4) },
                label = { Text("Lokadresse") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(160.dp),
            )
            FilledTonalButton(
                onClick = {
                    val address = newAddress.toIntOrNull()?.takeIf { it in 1..9999 } ?: return@FilledTonalButton
                    if (layout.locos.none { it.address == address }) {
                        app.layout.update { it.copy(locos = it.locos + Loco("lok-$address", "Lok $address", address)) }
                    }
                    DrivingUiState.selected.value = address
                    newAddress = ""
                },
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Lok hinzufügen")
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun LocoCard(name: String, state: LocoState, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Card(
        onClick = onClick,
        modifier = Modifier.width(220.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = if (selected) BorderStroke(2.dp, primary) else null,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Train, null, tint = primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        "Adresse ${state.address}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (0 in state.functions) {
                    Icon(Icons.Filled.Lightbulb, "Licht an", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.direction == Direction.FORWARD) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { state.speed / 126f },
                    modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Text("${state.speed}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
