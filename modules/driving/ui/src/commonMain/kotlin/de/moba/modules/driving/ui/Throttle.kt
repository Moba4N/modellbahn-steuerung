package de.moba.modules.driving.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.moba.core.model.Loco
import de.moba.core.model.LocoDynamics
import de.moba.core.station.Direction
import de.moba.core.station.LocoState
import de.moba.modules.driving.api.DrivingService
import de.moba.ui.shell.FunctionKeys
import de.moba.ui.shell.FunctionKeysState
import de.moba.ui.shell.FunctionRangeChooser
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.Moba
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MAX_SPEED = 126

/**
 * Fahrpult fuer eine Lok: Tacho, Fahrregler, Richtung, Halt und Funktionstasten.
 * Mit [LocoDynamics.cab] wird es zum Fuehrerstand: Tacho in km/h, Fahrhebel, Betriebs- und Schnellbremse.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Throttle(app: MobaApp, name: String, state: LocoState, service: DrivingService, loco: Loco? = null, onClose: () -> Unit) {
    val address = state.address
    val dynamics = loco?.dynamics ?: LocoDynamics()
    val cab = dynamics.cab
    val targets by service.targets.collectAsState()
    // Fahrhebel: bei Traegheit das Ziel, auf das die Lok zulaeuft, sonst die gemeldete Fahrstufe
    val lever = targets[address]
    val scope = rememberCoroutineScope()

    fun run(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                DrivingUiState.message.value = null
            } catch (e: Exception) {
                DrivingUiState.message.value = e.message
            }
        }
    }

    // Der Regler folgt beim Ziehen dem Finger, sonst dem von der Zentrale bestaetigten Wert.
    var dragging by remember(address) { mutableStateOf<Float?>(null) }

    // Fahrbefehle der Reihe nach senden; Zwischenwerte beim schnellen Ziehen werden uebersprungen.
    val target = remember(address) { MutableStateFlow<Pair<Int, Direction>?>(null) }
    LaunchedEffect(address) {
        target.filterNotNull().collect { (speed, direction) ->
            try {
                service.drive(address, speed, direction)
                DrivingUiState.message.value = null
            } catch (e: Exception) {
                DrivingUiState.message.value = e.message
            }
        }
    }

    // Mit Traegheit zeigt der Tacho die echte Fahrstufe, der Regler das Ziel
    val shownSpeed = if (dynamics.inertia) state.speed else dragging?.toInt() ?: state.speed
    val leverValue = dragging ?: (lever ?: state.speed).toFloat()
    val vmax = loco?.vmaxKmh?.takeIf { cab && it > 0 }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.align(Alignment.Center).padding(horizontal = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (cab) "Führerstand · Adresse $address" else "Adresse $address",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Filled.Close, "Fahrpult schließen")
            }
        }

        if (vmax != null) {
            SpeedGauge(
                speed = kmh(shownSpeed, vmax),
                direction = state.direction,
                max = vmax,
                unit = "km/h",
                target = lever?.let { kmh(it, vmax) },
            )
        } else {
            SpeedGauge(speed = shownSpeed, direction = state.direction, target = lever)
        }
        if (lever != null) {
            Text(
                if (vmax != null) "Ziel ${kmh(lever, vmax)} km/h" else "Ziel: Fahrstufe $lever",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (cab) Text("Fahrhebel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = leverValue,
            onValueChange = { value ->
                dragging = value
                target.value = value.toInt() to state.direction
            },
            onValueChangeFinished = { dragging = null },
            valueRange = 0f..MAX_SPEED.toFloat(),
            colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledIconToggleButton(
                checked = state.direction == Direction.REVERSE,
                onCheckedChange = { run { service.drive(address, 0, Direction.REVERSE) } },
                modifier = Modifier.size(56.dp),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Rückwärts") }
            Button(
                onClick = { run { service.stop(address) } },
                modifier = Modifier.height(56.dp).width(if (cab || dynamics.inertia) 156.dp else 132.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(Icons.Filled.Stop, null)
                Spacer(Modifier.width(6.dp))
                Text(if (cab || dynamics.inertia) "Bremsen" else "Halt", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
            FilledIconToggleButton(
                checked = state.direction == Direction.FORWARD,
                onCheckedChange = { run { service.drive(address, 0, Direction.FORWARD) } },
                modifier = Modifier.size(56.dp),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Vorwärts") }
        }
        if (cab || dynamics.inertia) {
            // Schnellbremse: sofort Fahrstufe 0, ohne Bremsweg
            Button(
                onClick = { run { service.emergencyBrake(address) } },
                colors = ButtonDefaults.buttonColors(containerColor = Moba.colors.alarm, contentColor = MaterialTheme.colorScheme.onError),
                modifier = Modifier.height(48.dp),
            ) {
                Icon(Icons.Filled.Block, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Schnellbremse", fontWeight = FontWeight.SemiBold)
            }
        }

        val range = FunctionKeysState.range(app)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Funktionen",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        FunctionRangeChooser(app, range)
        FunctionKeys(range, state.functions) { f -> run { service.setFunction(address, f, f !in state.functions) } }
    }
}

/** Fahrstufe 0 bis 126 in km/h des Vorbilds, gleichmaessig bis [vmax]. */
private fun kmh(speed: Int, vmax: Int) = (speed.coerceIn(0, MAX_SPEED) * vmax / MAX_SPEED.toDouble()).roundToInt()

/**
 * Tacho: Bogen von 0 bis [max] (Fahrstufen oder km/h), darin der aktuelle Wert und die Richtung.
 * [target] markiert den Fahrhebel, wenn die Lok mit Traegheit noch dorthin unterwegs ist.
 */
@Composable
private fun SpeedGauge(speed: Int, direction: Direction, max: Int = MAX_SPEED, unit: String = "Fahrstufe", target: Int? = null) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val value = MaterialTheme.colorScheme.primary
    val tick = MaterialTheme.colorScheme.outline
    val marker = MaterialTheme.colorScheme.tertiary
    val fraction = speed.coerceIn(0, max) / max.toFloat()

    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp)) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2 + 6.dp.toPx()
            val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
            val topLeft = Offset(inset, inset)
            drawArc(track, startAngle = 150f, sweepAngle = 240f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (fraction > 0f) {
                drawArc(value, startAngle = 150f, sweepAngle = 240f * fraction, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            // Teilstriche alle 1/6 der Skala, ausserhalb des Bogens.
            val center = Offset(size.width / 2, size.height / 2)
            val outer = size.width / 2
            for (i in 0..6) {
                val angle = (150.0 + 240.0 * i / 6) * PI / 180.0
                val from = Offset(center.x + (outer - 3.dp.toPx()) * cos(angle).toFloat(), center.y + (outer - 3.dp.toPx()) * sin(angle).toFloat())
                val to = Offset(center.x + outer * cos(angle).toFloat(), center.y + outer * sin(angle).toFloat())
                drawLine(tick, from, to, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            // Fahrhebel-Marke quer ueber den Bogen
            if (target != null) {
                val angle = (150.0 + 240.0 * target.coerceIn(0, max) / max) * PI / 180.0
                val r = arcSize.width / 2
                val from = Offset(center.x + (r - stroke) * cos(angle).toFloat(), center.y + (r - stroke) * sin(angle).toFloat())
                val to = Offset(center.x + (r + stroke) * cos(angle).toFloat(), center.y + (r + stroke) * sin(angle).toFloat())
                drawLine(marker, from, to, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$speed", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Text(unit, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Icon(
                if (direction == Direction.FORWARD) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack,
                null,
                tint = if (speed > 0) value else Color.Gray,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
