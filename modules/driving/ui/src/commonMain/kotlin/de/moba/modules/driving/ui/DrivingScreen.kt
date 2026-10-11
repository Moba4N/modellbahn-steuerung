package de.moba.modules.driving.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.moba.core.model.Loco
import de.moba.core.station.Direction
import de.moba.core.station.LocoState
import de.moba.modules.driving.api.DRIVING_MODULE_ID
import de.moba.modules.driving.api.DrivingService
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.ModuleScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Ansicht des Moduls "Fahren": Lok waehlen, Fahrregler, Richtung, Funktionstasten. */
object DrivingScreen : ModuleScreen {
    override val moduleId = DRIVING_MODULE_ID
    override val title = "Fahren"

    @Composable
    override fun Content(app: MobaApp) {
        val session by app.session.collectAsState()
        val service = session?.host?.service(DrivingService::class) ?: return
        DrivingContent(app, service)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrivingContent(app: MobaApp, service: DrivingService) {
    val scope = rememberCoroutineScope()
    val layout by app.layout.layout.collectAsState()
    val states by service.locos.collectAsState()
    var selected by remember { mutableStateOf<Int?>(null) }
    var newAddress by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                message = null
            } catch (e: Exception) {
                message = e.message
            }
        }
    }

    // Loks aus der Anlage plus Loks, die gerade an der Zentrale fahren.
    val addresses = (layout.locos.map { it.address } + states.keys).distinct().sorted()

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        service.locoLimit?.let {
            Text("Kostenlos: bis zu $it Loks steuerbar.", style = MaterialTheme.typography.bodySmall)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            addresses.forEach { address ->
                val name = layout.locos.firstOrNull { it.address == address }?.name ?: "Lok $address"
                FilterChip(selected = selected == address, onClick = { selected = address }, label = { Text(name) })
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newAddress,
                onValueChange = { newAddress = it.filter(Char::isDigit).take(4) },
                label = { Text("Lokadresse") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(160.dp),
            )
            Button(
                onClick = {
                    val address = newAddress.toIntOrNull()?.takeIf { it in 1..9999 } ?: return@Button
                    if (layout.locos.none { it.address == address }) {
                        app.layout.update { it.copy(locos = it.locos + Loco("lok-$address", "Lok $address", address)) }
                    }
                    selected = address
                    newAddress = ""
                },
            ) { Text("Lok hinzufügen") }
        }

        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        val address = selected
        if (address != null) {
            Throttle(address, states[address] ?: LocoState(address), service, ::run, onError = { message = it.message })
        } else {
            Text("Wähle eine Lok oder gib eine Adresse ein.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Throttle(
    address: Int,
    state: LocoState,
    service: DrivingService,
    run: (suspend () -> Unit) -> Unit,
    onError: (Exception) -> Unit,
) {
    // Der Regler folgt beim Ziehen dem Finger, sonst dem von der Zentrale bestaetigten Wert.
    var dragging by remember(address) { mutableStateOf<Float?>(null) }

    // Fahrbefehle der Reihe nach senden; Zwischenwerte beim schnellen Ziehen werden uebersprungen.
    val target = remember(address) { MutableStateFlow<Pair<Int, Direction>?>(null) }
    LaunchedEffect(address) {
        target.filterNotNull().collect { (speed, direction) ->
            try {
                service.drive(address, speed, direction)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Fahrstufe ${dragging?.toInt() ?: state.speed} von 126", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = dragging ?: state.speed.toFloat(),
            onValueChange = { value ->
                dragging = value
                target.value = value.toInt() to state.direction
            },
            onValueChangeFinished = { dragging = null },
            valueRange = 0f..126f,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.direction == Direction.REVERSE,
                onClick = { run { service.drive(address, 0, Direction.REVERSE) } },
                label = { Text("◀ Rückwärts") },
            )
            OutlinedButton(onClick = { run { service.stop(address) } }) { Text("Halt") }
            FilterChip(
                selected = state.direction == Direction.FORWARD,
                onClick = { run { service.drive(address, 0, Direction.FORWARD) } },
                label = { Text("Vorwärts ▶") },
            )
        }
        Text("Funktionen", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (f in 0..12) {
                val on = f in state.functions
                FilterChip(
                    selected = on,
                    onClick = { run { service.setFunction(address, f, !on) } },
                    label = { Text(if (f == 0) "F0 Licht" else "F$f") },
                )
            }
        }
    }
}
