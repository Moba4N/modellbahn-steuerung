package de.moba.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.moba.core.runtime.License

/** Was ohne und mit Lizenz freigeschaltet ist; erscheint im Lizenzdialog. */
private val EDITIONS = listOf(
    "Kostenlos" to "Fahren, alle Zentralen, CV-Programmierung, je 3 Fahrzeuge, Gleisbild ansehen",
    "Pro" to "Gleisbilder speichern, bis zu 3 Gleisbilder gleichzeitig, unbegrenzt Fahrzeuge",
    "Decoder-Datenbank" to "Decoder-Programmierungen speichern und wieder aufspielen",
    "Club" to "Alles aus Pro und Decoder-Datenbank",
)

/** Kurzname der Ausgabe fuer Menue und Uebersicht. */
fun editionLabel(license: License?): String = license?.featureLabels?.joinToString(" + ") ?: "Kostenlos"

@Composable
internal fun LicenseDialog(app: MobaApp, onDismiss: () -> Unit) {
    val license by app.activeLicense.collectAsState()
    var key by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(16.dp).widthIn(max = 600.dp),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp, 16.dp, 24.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Lizenz", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Schließen") }
                }

                val active = license
                if (active != null) {
                    ActiveLicense(active)
                    TextButton(onClick = { app.removeLicense(); error = null }) { Text("Lizenz von diesem Gerät entfernen") }
                } else {
                    Text("Du nutzt die kostenlose Version.", style = MaterialTheme.typography.bodyLarge)
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EDITIONS.forEach { (name, text) ->
                        Row {
                            Text(name, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(150.dp), style = MaterialTheme.typography.bodyMedium)
                            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }

                if (app.canEnterLicense) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it; error = null },
                        label = { Text(if (active == null) "Lizenzschlüssel" else "Neuer Lizenzschlüssel") },
                        placeholder = { Text("MOBA1-…") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 3,
                        maxLines = 5,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(enabled = key.isNotBlank(), onClick = {
                        error = app.activateLicense(key)
                        if (error == null) key = ""
                    }) { Text("Aktivieren") }
                    Text(
                        "Die Lizenz wird ohne Internet geprüft und auf diesem Gerät gespeichert.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("In dieser Version können keine Lizenzen eingegeben werden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ActiveLicense(license: License) {
    val colors = Moba.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Verified, null, tint = colors.signalGreen)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(editionLabel(license), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.signalGreen)
            Text(
                buildString {
                    if (license.holder.isNotBlank()) append("Für ${license.holder} · ")
                    append("Nr. ${license.id} · ")
                    append(if (license.expires.isBlank()) "unbefristet" else "gültig bis ${license.expires}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
