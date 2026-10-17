package de.moba.modules.trackplan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.moba.modules.trackplan.api.ModelClockState
import de.moba.modules.trackplan.api.ModelTime
import de.moba.modules.trackplan.api.SpeedMeasurement
import de.moba.modules.trackplan.api.TrackPlanService
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Zeitraffer zur Auswahl wie in iTrain. */
private val FACTORS = listOf(1, 2, 3, 4, 6, 8, 10, 12, 15, 20, 30, 60)

/** Modellzeit in Minuten seit Mitternacht; tickt, solange die Uhr laeuft, etwa jede echte halbe Sekunde. */
@Composable
internal fun rememberModelMinutes(clock: ModelClockState): Double {
    val minutes by produceState(clock.minutesAt(ModelTime.realMillis()), clock) {
        value = clock.minutesAt(ModelTime.realMillis())
        while (clock.running) {
            delay(500)
            value = clock.minutesAt(ModelTime.realMillis())
        }
    }
    return minutes
}

/** "78 km/h · 250 mm/s · vorwärts · BR 218" */
internal fun measurementText(m: SpeedMeasurement, names: Map<Int, String>): String = listOfNotNull(
    "${m.kmh.roundToInt()} km/h",
    "${m.modelMmPerSecond.roundToInt()} mm/s",
    "${oneDecimal(m.millis / 1000.0)} s",
    if (m.forward) "vorwärts" else "rückwärts",
    m.address?.let { trainLabel(it, names) },
).joinToString(" · ")

private fun oneDecimal(v: Double): String {
    val tenths = (v * 10).roundToInt()
    return "${tenths / 10},${tenths % 10}"
}

/** Modellzeit im Betrieb: zeigt die Uhrzeit, ein Klick oeffnet [ClockDialog]. */
@Composable
internal fun ClockChip(service: TrackPlanService) {
    val clock by service.clock.collectAsState()
    val minutes = rememberModelMinutes(clock)
    AssistChip(
        onClick = { TrackPlanUiState.clockDialog.value = true },
        label = {
            Text(
                ModelClockState.format(minutes) + if (clock.running) "  ×${clock.factor}" else "  angehalten",
                fontWeight = FontWeight.SemiBold,
            )
        },
        leadingIcon = {
            Icon(
                Icons.Outlined.Schedule, "Modellzeit", Modifier.size(18.dp),
                tint = if (clock.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
    val open by TrackPlanUiState.clockDialog.collectAsState()
    if (open) ClockDialog(service) { TrackPlanUiState.clockDialog.value = false }
}

/** Uhr stellen, Zeitraffer waehlen, starten und anhalten. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClockDialog(service: TrackPlanService, onDismiss: () -> Unit) {
    val clock by service.clock.collectAsState()
    val minutes = rememberModelMinutes(clock)
    var hours by remember { mutableStateOf("") }
    var mins by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val m = minutes.toInt()
        hours = (m / 60).toString().padStart(2, '0')
        mins = (m % 60).toString().padStart(2, '0')
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Schedule, null) },
        title = { Text("Modellzeit ${ModelClockState.format(minutes)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField("Stunde", hours) { hours = it }
                    Text(":", style = MaterialTheme.typography.titleLarge)
                    TimeField("Minute", mins) { mins = it }
                    TextButton(
                        enabled = hours.toIntOrNull() in 0..23 && mins.toIntOrNull() in 0..59,
                        onClick = { service.setClock(hours.toInt(), mins.toInt()) },
                    ) { Text("Stellen") }
                }
                Text("Zeitraffer", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FACTORS.forEach { f ->
                        FilterChip(selected = clock.factor == f, onClick = { service.setClockFactor(f) }, label = { Text("×$f") })
                    }
                }
                Text(
                    if (clock.factor == 1) "Die Modellzeit läuft wie die echte Zeit."
                    else "Eine echte Minute sind ${clock.factor} Modellminuten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = { if (clock.running) service.stopClock() else service.startClock() }) {
                    Icon(if (clock.running) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                    Text(if (clock.running) "  Anhalten" else "  Starten")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

@Composable
private fun TimeField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(2)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(88.dp),
    )
}
