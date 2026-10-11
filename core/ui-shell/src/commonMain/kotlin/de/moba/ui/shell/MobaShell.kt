package de.moba.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.moba.core.runtime.ModuleState
import de.moba.core.runtime.ModuleStatus
import kotlinx.coroutines.flow.MutableStateFlow

/** App-Rahmen: Nothalt immer oben, darunter Uebersicht und die Ansichten der aktiven Module. */
@Composable
fun MobaShell(app: MobaApp, screens: List<ModuleScreen> = emptyList()) {
    val session by app.session.collectAsState()
    val emptyStatus = remember { MutableStateFlow(emptyMap<String, ModuleStatus>()) }
    val modules by (session?.host?.status ?: emptyStatus).collectAsState()
    val visible = screens.filter { modules[it.moduleId]?.state == ModuleState.ACTIVE }
    var tab by remember { mutableStateOf(0) }
    if (tab > visible.size) tab = 0

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Button(
                    onClick = { app.emergencyStop() },
                    modifier = Modifier.fillMaxWidth().padding(12.dp).height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("NOTHALT", style = MaterialTheme.typography.titleLarge)
                }
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Übersicht") })
                    visible.forEachIndexed { i, screen ->
                        Tab(selected = tab == i + 1, onClick = { tab = i + 1 }, text = { Text(screen.title) })
                    }
                }
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    if (tab == 0) Overview(app, modules) else visible[tab - 1].Content(app)
                }
            }
        }
    }
}

@Composable
private fun Overview(app: MobaApp, modules: Map<String, ModuleStatus>) {
    val session by app.session.collectAsState()
    val trackPower by app.trackPower.collectAsState()
    val connected by app.connected.collectAsState()
    val error by app.error.collectAsState()
    val log by app.log.collectAsState()

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StationPicker(app, initial = session?.config ?: app.savedConfig)

        Text(
            "${session?.station?.name ?: "Keine Zentrale"}: ${if (connected) "verbunden" else "nicht verbunden"}",
            style = MaterialTheme.typography.bodyMedium,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gleisspannung", Modifier.weight(1f))
            Switch(checked = trackPower, onCheckedChange = { app.setTrackPower(it) }, enabled = connected)
        }

        Text("Module", style = MaterialTheme.typography.titleMedium)
        modules.forEach { (id, status) ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(session?.host?.module(id)?.displayName ?: id, style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (status.state) {
                            ModuleState.ACTIVE -> "aktiv"
                            ModuleState.LOCKED -> "gesperrt: ${status.reason ?: "Abo noetig"}"
                            ModuleState.FAILED -> "Fehler: ${status.reason}"
                            ModuleState.INSTALLED -> "installiert"
                            ModuleState.STOPPED -> "gestoppt"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Text("Meldungen der Zentrale", style = MaterialTheme.typography.titleMedium)
        if (log.isEmpty()) Text("Noch keine Meldungen.", style = MaterialTheme.typography.bodySmall)
        log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun StationPicker(app: MobaApp, initial: StationConfig) {
    var useZ21 by remember(initial) { mutableStateOf(initial is StationConfig.Z21) }
    var host by remember(initial) { mutableStateOf((initial as? StationConfig.Z21)?.host ?: "") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Zentrale", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.selectable(selected = !useZ21, onClick = { useZ21 = false }), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = !useZ21, onClick = { useZ21 = false })
                Text("Simulator (ohne Hardware)")
            }
            Row(Modifier.selectable(selected = useZ21, onClick = { useZ21 = true }), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = useZ21, onClick = { useZ21 = true })
                Text("Z21-Protokoll (z. B. YaMoRC YD7001, Roco z21)")
            }
            if (useZ21) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text("IP-Adresse der Zentrale") },
                    singleLine = true,
                )
            }
            Button(
                onClick = { app.connect(if (useZ21) StationConfig.Z21(host) else StationConfig.Simulator) },
                enabled = !useZ21 || host.isNotBlank(),
            ) {
                Text("Verbinden")
            }
        }
    }
}
