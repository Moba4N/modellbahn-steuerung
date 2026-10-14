package de.moba.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

/**
 * Beenden mit Rueckfrage: Fenster schliessen (Windows) oder Zurueck (Android) ruft [request] auf.
 * Ist nichts ungespeichert, beendet die App sofort, sonst fragt ein Dialog nach.
 */
class ExitGuard {
    var asking by mutableStateOf(false)
        internal set

    fun request() {
        asking = true
    }

    internal fun cancel() {
        asking = false
    }
}

@Composable
internal fun ExitDialog(pending: List<UnsavedWork>, guard: ExitGuard, onExit: () -> Unit) {
    if (pending.isEmpty()) {
        LaunchedEffect(Unit) {
            guard.cancel()
            onExit()
        }
        return
    }
    var error by remember { mutableStateOf<String?>(null) }
    val canSave = pending.all { it.canSave }
    val names = pending.joinToString(" und ") { it.title }
    AlertDialog(
        onDismissRequest = guard::cancel,
        title = { Text("Ungespeicherte Änderungen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$names: Es gibt Änderungen, die noch nicht gespeichert sind. Beim Beenden gehen sie verloren.")
                if (!canSave) {
                    Text(
                        "Speichern gibt es ab der Basis-Lizenz (Burger-Menü, „Lizenz …“).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (canSave) {
                Button(onClick = {
                    runCatching { pending.forEach { it.save() } }
                        .onSuccess {
                            guard.cancel()
                            onExit()
                        }
                        .onFailure { error = "Speichern fehlgeschlagen: ${it.message}" }
                }) { Text("Speichern und beenden") }
            }
        },
        dismissButton = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                TextButton(onClick = {
                    guard.cancel()
                    onExit()
                }) { Text("Ohne Speichern beenden") }
                TextButton(onClick = guard::cancel) { Text("Abbrechen") }
            }
        },
    )
}
