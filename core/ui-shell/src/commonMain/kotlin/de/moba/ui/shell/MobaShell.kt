package de.moba.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.moba.core.runtime.ModuleState

/** App-Rahmen: Zentrale, Gleisspannung, Nothalt und die Liste der Module mit ihrem Zustand. */
@Composable
fun MobaShell(app: MobaApp) {
    val trackPower by app.trackPower.collectAsState()
    val connected by app.connected.collectAsState()
    val modules by app.host.status.collectAsState()

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Modellbahn-Steuerung", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${app.station.name}: ${if (connected) "verbunden" else "nicht verbunden"}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gleisspannung", Modifier.weight(1f))
                    Switch(checked = trackPower, onCheckedChange = { app.setTrackPower(it) })
                }

                Button(
                    onClick = { app.emergencyStop() },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("NOTHALT", style = MaterialTheme.typography.titleLarge)
                }

                Spacer(Modifier.height(8.dp))
                Text("Module", style = MaterialTheme.typography.titleMedium)
                if (modules.isEmpty()) {
                    Text("Noch keine Module installiert.", style = MaterialTheme.typography.bodyMedium)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(modules.entries.toList(), key = { it.key }) { (id, status) ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(app.host.module(id)?.displayName ?: id, style = MaterialTheme.typography.titleSmall)
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
                }
            }
        }
    }
}
