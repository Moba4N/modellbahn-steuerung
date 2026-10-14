package de.moba.modules.throttles.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.moba.core.station.Direction
import de.moba.core.station.LocoState
import de.moba.modules.driving.api.DrivingService
import de.moba.modules.throttles.api.THROTTLES_MODULE_ID
import de.moba.modules.throttles.api.ThrottleBarService
import de.moba.ui.shell.LocalWideLayout
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.ModuleDock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

private const val MAX_SPEED = 126

/** Zustand der Leiste, bleibt beim Wechsel der Reiter erhalten. */
object ThrottleDockState {
    val expanded = MutableStateFlow(true)
    val message = MutableStateFlow<String?>(null)
}

/** Leiste "Fahrregler" am unteren Bildrand: mehrere Loks gleichzeitig fahren (ab Pro). */
object ThrottleDock : ModuleDock {
    override val moduleId = THROTTLES_MODULE_ID

    @Composable
    override fun Content(app: MobaApp) {
        val session by app.session.collectAsState()
        val host = session?.host ?: return
        val bar = host.service(ThrottleBarService::class) ?: return
        val driving = host.service(DrivingService::class) ?: return
        val open by bar.open.collectAsState()
        val locos by driving.locos.collectAsState()
        val layout by app.layout.layout.collectAsState()
        val expanded by ThrottleDockState.expanded.collectAsState()
        val message by ThrottleDockState.message.collectAsState()
        val wide = LocalWideLayout.current
        fun nameOf(address: Int) = layout.locos.firstOrNull { it.address == address }?.name ?: "Lok $address"

        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(horizontal = if (wide) 16.dp else 8.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Tune, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Fahrregler", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${open.size} von ${bar.maxThrottles}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AddThrottleButton(bar, layout.locos.map { it.address to it.name }, enabled = open.size < bar.maxThrottles)
                    message?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } ?: Spacer(Modifier.weight(1f))
                    IconButton(onClick = { ThrottleDockState.expanded.value = !expanded }) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                            if (expanded) "Fahrregler einklappen" else "Fahrregler ausklappen",
                        )
                    }
                }
                if (expanded) {
                    if (open.isEmpty()) {
                        Text(
                            "Mit „Fahrregler“ eine Lok hinzufügen. Bis zu ${bar.maxThrottles} Loks lassen sich hier gleichzeitig fahren.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            open.forEach { address ->
                                MiniThrottle(
                                    name = nameOf(address),
                                    state = locos[address] ?: LocoState(address),
                                    service = driving,
                                    width = if (wide) 250 else 210,
                                    onClose = { bar.close(address) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddThrottleButton(bar: ThrottleBarService, locos: List<Pair<Int, String>>, enabled: Boolean) {
    var menu by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    fun add(a: Int) {
        runCatching { bar.open(a) }
            .onSuccess { ThrottleDockState.message.value = null }
            .onFailure { ThrottleDockState.message.value = it.message }
        ThrottleDockState.expanded.value = true
        menu = false
        address = ""
    }
    Box {
        FilledTonalButton(onClick = { menu = true }, enabled = enabled, modifier = Modifier.height(32.dp)) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Fahrregler", style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            locos.forEach { (a, name) ->
                DropdownMenuItem(text = { Text("$name  ·  Adresse $a") }, onClick = { add(a) })
            }
            if (locos.isNotEmpty()) HorizontalDivider()
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.filter(Char::isDigit).take(5) },
                    label = { Text("Adresse") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                )
                TextButton(onClick = { address.toIntOrNull()?.let(::add) }, enabled = address.isNotEmpty()) { Text("Öffnen") }
            }
        }
    }
}

/** Kleiner Fahrregler: Name, Regler, Richtung, Halt und Licht. */
@Composable
private fun MiniThrottle(name: String, state: LocoState, service: DrivingService, width: Int, onClose: () -> Unit) {
    val address = state.address
    val scope = rememberCoroutineScope()
    var dragging by remember(address) { mutableStateOf<Float?>(null) }
    // Fahrbefehle der Reihe nach senden; Zwischenwerte beim schnellen Ziehen fallen weg.
    val target = remember(address) { MutableStateFlow<Pair<Int, Direction>?>(null) }
    LaunchedEffect(address) {
        target.filterNotNull().collect { (speed, direction) ->
            runCatching { service.drive(address, speed, direction) }
                .onSuccess { ThrottleDockState.message.value = null }
                .onFailure { ThrottleDockState.message.value = it.message }
        }
    }
    fun run(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess { ThrottleDockState.message.value = null }
                .onFailure { ThrottleDockState.message.value = it.message }
        }
    }
    val speed = dragging?.toInt() ?: state.speed
    val light = 0 in state.functions

    Card(
        Modifier.width(width.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Adresse $address", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "$speed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (speed > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, "Fahrregler schließen", modifier = Modifier.size(18.dp))
                }
            }
            Slider(
                value = dragging ?: state.speed.toFloat(),
                onValueChange = {
                    dragging = it
                    target.value = it.toInt() to state.direction
                },
                onValueChangeFinished = { dragging = null },
                valueRange = 0f..MAX_SPEED.toFloat(),
                modifier = Modifier.fillMaxWidth().height(32.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledIconToggleButton(
                    checked = state.direction == Direction.REVERSE,
                    onCheckedChange = { run { service.drive(address, 0, Direction.REVERSE) } },
                    modifier = Modifier.size(36.dp),
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Rückwärts", modifier = Modifier.size(18.dp)) }
                FilledTonalButton(onClick = { run { service.stop(address) } }, modifier = Modifier.height(36.dp).weight(1f)) {
                    Icon(Icons.Filled.Stop, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Halt")
                }
                FilledIconToggleButton(
                    checked = state.direction == Direction.FORWARD,
                    onCheckedChange = { run { service.drive(address, 0, Direction.FORWARD) } },
                    modifier = Modifier.size(36.dp),
                ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Vorwärts", modifier = Modifier.size(18.dp)) }
                FilledIconToggleButton(
                    checked = light,
                    onCheckedChange = { run { service.setFunction(address, 0, !light) } },
                    modifier = Modifier.size(36.dp),
                ) { Icon(Icons.Filled.Lightbulb, "Licht", modifier = Modifier.size(18.dp)) }
            }
        }
    }
}
