package de.moba.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.moba.core.station.StationConfig

/** Zentrale waehlen, Adresse eingeben oder im Netz suchen, verbinden. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StationCard(app: MobaApp) {
    val session by app.session.collectAsState()
    val connected by app.connected.collectAsState()
    val error by app.error.collectAsState()
    val found by app.found.collectAsState()
    val searching by app.searching.collectAsState()
    val initial = session?.config ?: app.savedConfig
    val colors = Moba.colors

    var driverId by remember(initial) { mutableStateOf(initial.driverId) }
    var host by remember(initial) { mutableStateOf(initial.host) }
    var port by remember(initial) { mutableStateOf(initial.port?.toString().orEmpty()) }
    var options by remember(initial) { mutableStateOf(initial.options) }
    val driver = app.driver(driverId) ?: app.drivers.first()

    SectionCard(
        title = "Zentrale",
        icon = Icons.Outlined.Router,
        trailing = {
            if (connected) StatusPill("Verbunden", colors.signalGreen, Icons.Filled.CheckCircle)
            else StatusPill("Getrennt", colors.alarm, Icons.Filled.ErrorOutline)
        },
    ) {
        Text(
            session?.station?.name ?: "Keine Zentrale gewählt",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            app.drivers.forEach { d ->
                FilterChip(
                    selected = d.id == driver.id,
                    onClick = {
                        driverId = d.id
                        port = ""
                        options = d.options.associate { it.key to it.default }
                    },
                    label = { Text(d.title) },
                )
            }
        }
        Text(driver.devices, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (driver.needsHost) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text("IP-Adresse") },
                    placeholder = { Text("z. B. 192.168.0.111") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { value -> port = value.filter { it.isDigit() }.take(5) },
                    label = { Text("Port") },
                    placeholder = { Text(driver.defaultPort?.toString().orEmpty()) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(110.dp),
                )
            }
        }

        driver.options.forEach { option ->
            Text(option.label, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                option.choices.forEach { (value, label) ->
                    FilterChip(
                        selected = (options[option.key] ?: option.default) == value,
                        onClick = { options = options + (option.key to value) },
                        label = { Text(label) },
                    )
                }
            }
        }

        if (app.canDiscover) {
            OutlinedButton(onClick = { app.discover() }, enabled = !searching) {
                if (searching) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (searching) "Suche läuft …" else "Im Netz suchen")
            }
            if (found.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    found.forEach { station ->
                        AssistChip(
                            onClick = {
                                val d = app.driver(station.driverId) ?: return@AssistChip
                                driverId = d.id
                                host = station.host
                                port = ""
                                options = d.options.associate { it.key to it.default }
                            },
                            label = { Text("${station.name} · ${station.host}") },
                        )
                    }
                }
            }
        }

        error?.let { Text(it, color = colors.alarm, style = MaterialTheme.typography.bodyMedium) }
        FilledTonalButton(
            onClick = {
                app.connect(
                    StationConfig(
                        driverId = driver.id,
                        host = if (driver.needsHost) host else "",
                        port = port.toIntOrNull(),
                        options = driver.options.associate { it.key to (options[it.key] ?: it.default) },
                    ),
                )
            },
            enabled = !driver.needsHost || host.isNotBlank(),
        ) {
            Icon(Icons.Filled.Link, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Verbinden")
        }
    }
}
