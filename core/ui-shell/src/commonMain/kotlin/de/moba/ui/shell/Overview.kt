package de.moba.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.moba.core.runtime.ModuleState
import de.moba.core.runtime.ModuleStatus

/** Startseite: Zentrale, Gleisspannung, Module und Meldungen als Kacheln. */
@Composable
internal fun Overview(app: MobaApp, modules: Map<String, ModuleStatus>, wide: Boolean) {
    val scroll = rememberScrollState()
    if (wide) {
        Row(Modifier.verticalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                StationCard(app)
                TrackPowerCard(app)
                ModulesCard(app, modules)
            }
            Column(Modifier.weight(1f)) { LogCard(app) }
        }
    } else {
        Column(Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StationCard(app)
            TrackPowerCard(app)
            ModulesCard(app, modules)
            LogCard(app)
        }
    }
}

@Composable
private fun StationCard(app: MobaApp) {
    val session by app.session.collectAsState()
    val connected by app.connected.collectAsState()
    val error by app.error.collectAsState()
    val initial = session?.config ?: app.savedConfig
    var useZ21 by remember(initial) { mutableStateOf(initial is StationConfig.Z21) }
    var host by remember(initial) { mutableStateOf((initial as? StationConfig.Z21)?.host ?: "") }
    val colors = Moba.colors

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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !useZ21, onClick = { useZ21 = false }, label = { Text("Simulator") })
            FilterChip(selected = useZ21, onClick = { useZ21 = true }, label = { Text("Z21-Protokoll (YaMoRC, Roco)") })
        }
        if (useZ21) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it.trim() },
                label = { Text("IP-Adresse der Zentrale") },
                placeholder = { Text("z. B. 192.168.0.111") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let { Text(it, color = colors.alarm, style = MaterialTheme.typography.bodyMedium) }
        FilledTonalButton(
            onClick = { app.connect(if (useZ21) StationConfig.Z21(host) else StationConfig.Simulator) },
            enabled = !useZ21 || host.isNotBlank(),
        ) {
            Icon(Icons.Filled.Link, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Verbinden")
        }
    }
}

@Composable
private fun TrackPowerCard(app: MobaApp) {
    val trackPower by app.trackPower.collectAsState()
    val connected by app.connected.collectAsState()
    val colors = Moba.colors
    SectionCard(title = "Gleisspannung", icon = Icons.Outlined.PowerSettingsNew) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (trackPower) "Eingeschaltet" else "Ausgeschaltet",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (trackPower) colors.signalGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Der Nothalt oben rechts hält alle Loks sofort an.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = trackPower,
                onCheckedChange = { app.setTrackPower(it) },
                enabled = connected,
                colors = SwitchDefaults.colors(checkedTrackColor = colors.signalGreen),
            )
        }
    }
}

@Composable
private fun ModulesCard(app: MobaApp, modules: Map<String, ModuleStatus>) {
    val session by app.session.collectAsState()
    val colors = Moba.colors
    SectionCard(title = "Module", icon = Icons.Outlined.Extension) {
        if (modules.isEmpty()) {
            Text("Noch keine Module gestartet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        modules.entries.forEachIndexed { index, (id, status) ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(session?.host?.module(id)?.displayName ?: id, style = MaterialTheme.typography.bodyLarge)
                    status.reason?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                when (status.state) {
                    ModuleState.ACTIVE -> StatusPill("Aktiv", colors.signalGreen)
                    ModuleState.LOCKED -> StatusPill("Gesperrt", colors.locked, Icons.Filled.Lock)
                    ModuleState.FAILED -> StatusPill("Fehler", colors.alarm)
                    ModuleState.INSTALLED -> StatusPill("Installiert", colors.locked)
                    ModuleState.STOPPED -> StatusPill("Gestoppt", colors.locked)
                }
            }
        }
    }
}

@Composable
private fun LogCard(app: MobaApp) {
    val log by app.log.collectAsState()
    val colors = Moba.colors
    SectionCard(title = "Meldungen der Zentrale", icon = Icons.Outlined.Notifications) {
        if (log.isEmpty()) {
            Text(
                "Noch keine Meldungen. Belegte s88-Abschnitte erscheinen hier.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        log.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    entry.time,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                StatusDot(
                    when (entry.kind) {
                        LogKind.OCCUPIED -> colors.occupied
                        LogKind.FREE -> colors.locked
                        LogKind.ALARM -> colors.alarm
                        LogKind.INFO -> colors.signalGreen
                    },
                    size = 8,
                )
                Spacer(Modifier.width(10.dp))
                Text(entry.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
