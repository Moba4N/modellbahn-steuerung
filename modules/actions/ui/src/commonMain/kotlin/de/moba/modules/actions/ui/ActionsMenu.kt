package de.moba.modules.actions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.moba.modules.actions.api.ACTIONS_MODULE_ID
import de.moba.modules.actions.api.ActionService
import de.moba.ui.shell.Moba
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.ModuleEditor
import de.moba.ui.shell.ModuleMenu
import de.moba.ui.shell.StatusDot
import kotlinx.coroutines.flow.MutableStateFlow

/** Reiter im Fenster "Aktionen". */
enum class ActionsTab(val label: String) { RULES("Aktionen"), FUEL("Brennstoff"), LOG("Protokoll") }

/** Was im Fenster "Aktionen" offen ist; `null` = geschlossen. */
data class ActionsWindow(val tab: ActionsTab = ActionsTab.RULES, val ruleId: String? = null, val newRule: Boolean = false)

object ActionsUiState {
    val menuOpen = MutableStateFlow(false)
    val window = MutableStateFlow<ActionsWindow?>(null)
}

@Composable
internal fun rememberActionService(app: MobaApp): ActionService? {
    val session by app.session.collectAsState()
    return session?.host?.service(ActionService::class)
}

/** Burger-Menue "Aktionen" in der oberen Leiste: Aktionen ein- und ausschalten, von Hand starten, bearbeiten. */
object ActionsMenu : ModuleMenu {
    override val moduleId = ACTIONS_MODULE_ID
    override val title = "Aktionen"
    override val icon = Icons.Outlined.Bolt
    override val expanded get() = ActionsUiState.menuOpen

    @Composable
    override fun ColumnScope.Items(app: MobaApp, close: () -> Unit) {
        val service = rememberActionService(app) ?: return
        val book by service.book.collectAsState()
        val satisfied by service.satisfied.collectAsState()
        val running by service.running.collectAsState()
        fun open(window: ActionsWindow) {
            close()
            ActionsUiState.window.value = window
        }
        Text(
            if (service.canRun) "Aktionen (${book.rules.count { it.enabled }} von ${book.rules.size} aktiv)" else "Aktionen – ausführen ab Pro",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        book.rules.forEach { rule ->
            DropdownMenuItem(
                text = { Text(rule.name.ifBlank { rule.id }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        StatusDot(
                            when {
                                rule.id in running -> Moba.colors.occupied
                                rule.id in satisfied -> Moba.colors.signalGreen
                                else -> MaterialTheme.colorScheme.outlineVariant
                            },
                        )
                    }
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(rule.enabled, { service.setEnabled(rule.id, it) }, Modifier.padding(start = 8.dp).testTag("aktion-an-${rule.id}"))
                        IconButton(onClick = { runCatching { service.run(rule.id) } }, enabled = service.canRun) {
                            Icon(Icons.Filled.PlayArrow, "„${rule.name}“ ausführen")
                        }
                    }
                },
                onClick = { open(ActionsWindow(ruleId = rule.id)) },
            )
        }
        if (book.rules.isEmpty()) {
            Text(
                "Noch keine Aktion.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        DropdownMenuItem(text = { Text("Neue Aktion …") }, leadingIcon = { Icon(Icons.Outlined.Add, null) }, onClick = {
            open(ActionsWindow(newRule = true))
        })
        DropdownMenuItem(text = { Text("Aktionen bearbeiten …") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = {
            open(ActionsWindow(ruleId = book.rules.firstOrNull()?.id))
        })
        DropdownMenuItem(text = { Text("Brennstoff …") }, leadingIcon = { Icon(Icons.Outlined.LocalGasStation, null) }, onClick = {
            open(ActionsWindow(ActionsTab.FUEL))
        })
        DropdownMenuItem(text = { Text("Protokoll …") }, leadingIcon = { Icon(Icons.Outlined.History, null) }, onClick = {
            open(ActionsWindow(ActionsTab.LOG))
        })
    }

    @Composable
    override fun Dialogs(app: MobaApp) {
        val window by ActionsUiState.window.collectAsState()
        val service = rememberActionService(app) ?: return
        window?.let { ActionsDialog(app, service, it) { ActionsUiState.window.value = null } }
    }
}

@Composable
internal fun Gap(width: Int = 8) = Spacer(Modifier.width(width.dp))

internal val rowSpacing = Arrangement.spacedBy(8.dp)

/** Bearbeiten > Aktionen: dieselbe Liste und derselbe Editor wie im Menue "Aktionen". */
object ActionsEditor : ModuleEditor {
    override val moduleId = ACTIONS_MODULE_ID
    override val key = "aktionen"
    override val title = "Aktionen"
    override val icon = Icons.Outlined.Bolt
    override val order = 90

    private val window = MutableStateFlow(ActionsWindow())

    @Composable
    override fun Content(app: MobaApp) {
        val service = rememberActionService(app) ?: return
        val current by window.collectAsState()
        RulesTab(app, service, current) { window.value = it }
    }
}
