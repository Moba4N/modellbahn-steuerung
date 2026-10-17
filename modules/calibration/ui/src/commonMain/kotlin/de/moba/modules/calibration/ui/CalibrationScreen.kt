package de.moba.modules.calibration.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.moba.modules.calibration.api.CALIBRATION_MODULE_ID
import de.moba.modules.calibration.api.CalibrationProgress
import de.moba.modules.calibration.api.CalibrationService
import de.moba.modules.calibration.api.CalibrationSetup
import de.moba.modules.calibration.api.Contact
import de.moba.modules.calibration.api.MeasuringTrack
import de.moba.modules.calibration.api.Scale
import de.moba.modules.calibration.api.SpeedProfile
import de.moba.modules.calibration.api.SpeedStepMode
import de.moba.ui.shell.LocalWideLayout
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.ModuleScreen
import de.moba.ui.shell.SectionCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

/** Eingaben des Formulars; die Messstrecke wird in den Einstellungen gemerkt. */
object CalibrationUiState {
    val address = MutableStateFlow("")
    val mode = MutableStateFlow(SpeedStepMode.STEPS_28)
    val startModule = MutableStateFlow("1")
    val startContact = MutableStateFlow("1")
    val endModule = MutableStateFlow("1")
    val endContact = MutableStateFlow("2")
    val lengthMm = MutableStateFlow("1000")
    val scale = MutableStateFlow(Scale.H0)
    val forward = MutableStateFlow(true)
    val settle = MutableStateFlow("5")
    val stride = MutableStateFlow(SpeedStepMode.DEFAULT_STRIDE_128)
    val shown = MutableStateFlow<Pair<Int, SpeedStepMode>?>(null)
    val message = MutableStateFlow<String?>(null)
}

private const val PREF_TRACK = "einmessen.strecke"

/** Reiter "Einmessen": Messstrecke festlegen, Lok fahren lassen, Ergebnis als Kurve und Tabelle. */
object CalibrationScreen : ModuleScreen {
    override val moduleId = CALIBRATION_MODULE_ID
    override val title = "Einmessen"
    override val icon = Icons.Outlined.Timer

    @Composable
    override fun Content(app: MobaApp) {
        val session by app.session.collectAsState()
        val service = session?.host?.service(CalibrationService::class) ?: return
        val wide = LocalWideLayout.current
        LaunchedEffect(app) { loadTrack(app) }
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.width(440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SetupCard(app, service)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProgressCard(service)
                    ResultsCard(service)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProgressCard(service)
                SetupCard(app, service)
                ResultsCard(service)
            }
        }
    }
}

private fun loadTrack(app: MobaApp) {
    val f = app.preference(PREF_TRACK)?.split(',') ?: return
    if (f.size < 6) return
    with(CalibrationUiState) {
        startModule.value = f[0]
        startContact.value = f[1]
        endModule.value = f[2]
        endContact.value = f[3]
        lengthMm.value = f[4]
        scale.value = Scale.entries.firstOrNull { it.name == f[5] } ?: Scale.H0
    }
}

private fun saveTrack(app: MobaApp) = with(CalibrationUiState) {
    app.setPreference(
        PREF_TRACK,
        listOf(startModule.value, startContact.value, endModule.value, endContact.value, lengthMm.value, scale.value.name).joinToString(","),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupCard(app: MobaApp, service: CalibrationService) {
    val s = CalibrationUiState
    val address by s.address.collectAsState()
    val mode by s.mode.collectAsState()
    val startModule by s.startModule.collectAsState()
    val startContact by s.startContact.collectAsState()
    val endModule by s.endModule.collectAsState()
    val endContact by s.endContact.collectAsState()
    val length by s.lengthMm.collectAsState()
    val scale by s.scale.collectAsState()
    val forward by s.forward.collectAsState()
    val settle by s.settle.collectAsState()
    val stride by s.stride.collectAsState()
    val message by s.message.collectAsState()
    val progress by service.progress.collectAsState()
    val layout by app.layout.layout.collectAsState()
    val running = progress is CalibrationProgress.Running

    SectionCard("Messung", Icons.Outlined.Straighten) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("Lok")
            if (layout.locos.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    layout.locos.forEach { loco ->
                        FilterChip(
                            selected = address == loco.address.toString(),
                            onClick = { s.address.value = loco.address.toString() },
                            label = { Text(loco.name) },
                        )
                    }
                }
            }
            NumberField("Adresse", address, Modifier.width(140.dp)) { s.address.value = it }

            Label("Fahrstufen")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SpeedStepMode.entries.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { s.mode.value = m }, label = { Text(m.label) })
                }
            }
            if (mode == SpeedStepMode.STEPS_128) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(4, 8, 16).forEach { n ->
                        FilterChip(
                            selected = stride == n,
                            onClick = { s.stride.value = n },
                            label = { Text("jede $n. Stufe (${SpeedStepMode.STEPS_128.measurePoints(n).size} Messungen)") },
                        )
                    }
                }
            }

            Label("Messstrecke")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Start: s88-Modul", startModule, Modifier.width(150.dp)) { s.startModule.value = it }
                NumberField("Kontakt", startContact, Modifier.width(100.dp)) { s.startContact.value = it }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Ende: s88-Modul", endModule, Modifier.width(150.dp)) { s.endModule.value = it }
                NumberField("Kontakt", endContact, Modifier.width(100.dp)) { s.endContact.value = it }
            }
            NumberField("Länge in mm (Anfang zu Anfang)", length, Modifier.width(260.dp)) { s.lengthMm.value = it }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Scale.entries.forEach { sc ->
                    FilterChip(selected = scale == sc, onClick = { s.scale.value = sc }, label = { Text(sc.label) })
                }
            }

            Label("Fahrt")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = forward, onClick = { s.forward.value = true }, label = { Text("Vorwärts") })
                FilterChip(selected = !forward, onClick = { s.forward.value = false }, label = { Text("Rückwärts") })
                NumberField("Anlaufzeit in s", settle, Modifier.width(150.dp)) { s.settle.value = it }
            }

            Text(
                "Die Lok fährt auf einem Rundkurs immer wieder durch beide Melder. Für jede Stufe misst die App die Zeit " +
                    "vom Start- zum Endmelder. Die Zentrale fährt intern mit 128 Fahrstufen; bei 14 und 28 nimmt die App " +
                    "die passenden Stellen dieser Skala.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            if (running) {
                OutlinedButton(onClick = { service.cancel() }) {
                    Icon(Icons.Filled.Stop, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Abbrechen und Lok anhalten")
                }
            } else {
                Button(onClick = {
                    val a = address.toIntOrNull()
                    if (a == null) {
                        s.message.value = "Bitte eine Lok wählen oder eine Adresse eingeben."
                        return@Button
                    }
                    val track = MeasuringTrack(
                        Contact(startModule.toIntOrNull() ?: 0, startContact.toIntOrNull() ?: 0),
                        Contact(endModule.toIntOrNull() ?: 0, endContact.toIntOrNull() ?: 0),
                        length.toIntOrNull() ?: 0,
                        scale,
                    )
                    val name = layout.locos.firstOrNull { it.address == a }?.name ?: "Lok $a"
                    runCatching {
                        service.start(CalibrationSetup(a, name, mode, track, forward, settle.toIntOrNull() ?: 5, stride = stride))
                    }.onSuccess {
                        s.message.value = null
                        s.shown.value = a to mode
                        saveTrack(app)
                    }.onFailure { s.message.value = it.message }
                }) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Einmessen starten")
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(service: CalibrationService) {
    val progress by service.progress.collectAsState()
    when (val p = progress) {
        CalibrationProgress.Idle, is CalibrationProgress.Finished -> Unit
        is CalibrationProgress.Running -> SectionCard("Einmessen läuft: ${p.setup.name}", Icons.Outlined.Timer) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(progress = { p.index / p.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                Text(
                    "Messung ${p.index + 1} von ${p.total}: Fahrstufe ${p.step} (${p.setup.mode.label})",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(p.phase, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.points.isNotEmpty()) PointTable(p.points.map { it.step to it.kmh })
            }
        }
        is CalibrationProgress.Stopped -> SectionCard("Einmessen beendet", Icons.Outlined.Timer) {
            Text("${p.reason}. Die Lok wurde angehalten. Bis dahin gemessen: ${p.points.size} Stufen, nichts gespeichert.")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultsCard(service: CalibrationService) {
    val profiles by service.profiles.collectAsState()
    val shownKey by CalibrationUiState.shown.collectAsState()
    SectionCard("Ergebnisse", Icons.Outlined.QueryStats) {
        if (profiles.isEmpty()) {
            Text("Noch keine Lok eingemessen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        val shown = profiles.firstOrNull { (it.address to it.mode) == shownKey } ?: profiles.last()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            profiles.forEach { p ->
                FilterChip(
                    selected = p === shown,
                    onClick = { CalibrationUiState.shown.value = p.address to p.mode },
                    label = { Text("${p.name} · ${p.mode.steps}") },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        ProfileDetails(shown) { service.delete(shown.address, shown.mode) }
    }
}

@Composable
private fun ProfileDetails(profile: SpeedProfile, onDelete: () -> Unit) {
    var target by remember(profile) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "${profile.name}, Adresse ${profile.address}, ${profile.mode.label}, ${profile.scale.label}" +
                (if (profile.date.isNotEmpty()) ", gemessen am ${profile.date}" else ""),
            style = MaterialTheme.typography.bodyMedium,
        )
        profile.maxKmh?.let {
            Text("Höchstgeschwindigkeit: ${it.roundToInt()} km/h", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        SpeedChart(profile)
        PointTable(profile.points.map { it.step to it.kmh })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Gewünscht km/h", target, Modifier.width(160.dp)) { target = it }
            val kmh = target.toIntOrNull()
            if (kmh != null) {
                val step = profile.stepFor(kmh.toDouble())
                Text(
                    if (step != null) "ab Fahrstufe $step" else "schneller als gemessen",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        TextButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Messung löschen")
        }
    }
}

/** Kurve Fahrstufe zu km/h. Nicht gemessene Stufen fehlen in der Linie. */
@Composable
private fun SpeedChart(profile: SpeedProfile) {
    val line = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outline
    val grid = MaterialTheme.colorScheme.outlineVariant
    val measured = profile.points.filter { it.kmh != null }
    val maxKmh = ((profile.maxKmh ?: 0.0) / 20.0).let { kotlin.math.ceil(it) * 20.0 }.coerceAtLeast(20.0)
    Column {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val w = size.width
            val h = size.height
            fun x(step: Int) = step / profile.mode.steps.toFloat() * w
            fun y(kmh: Double) = h - (kmh / maxKmh).toFloat() * h
            for (i in 1..4) {
                val gy = h * i / 4f
                drawLine(grid, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            drawLine(axis, Offset(0f, h), Offset(w, h), strokeWidth = 2f)
            drawLine(axis, Offset(0f, 0f), Offset(0f, h), strokeWidth = 2f)
            var prev: Offset? = Offset(0f, h)
            for (p in measured) {
                val o = Offset(x(p.step), y(p.kmh!!))
                prev?.let { drawLine(line, it, o, strokeWidth = 3f, cap = StrokeCap.Round) }
                drawCircle(line, 4f, o)
                prev = o
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                "Fahrstufe ${profile.mode.steps}  ·  oben ${maxKmh.roundToInt()} km/h",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PointTable(points: List<Pair<Int, Double?>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        points.forEach { (step, kmh) ->
            Text(
                "$step: ${kmh?.let { "${it.roundToInt()} km/h" } ?: "–"}",
                style = MaterialTheme.typography.bodySmall,
                color = if (kmh == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun Label(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
