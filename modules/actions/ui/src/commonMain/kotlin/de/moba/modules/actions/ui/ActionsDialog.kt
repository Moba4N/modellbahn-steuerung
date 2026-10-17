package de.moba.modules.actions.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.moba.modules.actions.api.ActionRule
import de.moba.modules.actions.api.ActionService
import de.moba.modules.actions.api.Command
import de.moba.modules.actions.api.CommandType
import de.moba.modules.actions.api.Condition
import de.moba.modules.actions.api.ConditionType
import de.moba.modules.actions.api.ParamKind
import de.moba.modules.actions.api.ParamSpec
import de.moba.modules.actions.api.describe
import de.moba.modules.actions.api.fuelMinutes
import de.moba.modules.routes.api.RouteService
import de.moba.modules.vehicles.api.VehicleService
import de.moba.ui.shell.Moba
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.StatusPill

/** Fenster "Aktionen": Aktionen mit Bedingungsbaum und Befehlen bearbeiten, Brennstoff, Protokoll. */
@Composable
internal fun ActionsDialog(app: MobaApp, service: ActionService, window: ActionsWindow, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f),
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktionen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (!service.canRun) StatusPill("Ausführen ab Pro", Moba.colors.locked)
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Schließen") }
                }
                TabRow(selectedTabIndex = window.tab.ordinal, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    ActionsTab.entries.forEach { tab ->
                        Tab(
                            selected = tab == window.tab,
                            onClick = { ActionsUiState.window.value = window.copy(tab = tab, newRule = false) },
                            text = { Text(tab.label) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (window.tab) {
                        ActionsTab.RULES -> RulesTab(app, service, window)
                        ActionsTab.FUEL -> FuelTab(app, service)
                        ActionsTab.LOG -> LogTab(service)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Aktionen

@Composable
private fun RulesTab(app: MobaApp, service: ActionService, window: ActionsWindow) {
    val book by service.book.collectAsState()
    val satisfied by service.satisfied.collectAsState()
    fun select(id: String?, new: Boolean = false) {
        ActionsUiState.window.value = window.copy(ruleId = id, newRule = new)
    }
    val selected = book.rules.firstOrNull { it.id == window.ruleId }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp
        val editing = window.newRule || selected != null
        Row(Modifier.fillMaxSize()) {
            if (wide || !editing) {
                Column(Modifier.then(if (wide) Modifier.width(260.dp) else Modifier.fillMaxWidth()).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    FilledTonalButton(onClick = { select(null, new = true) }) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                        Gap(6)
                        Text("Neue Aktion")
                    }
                    Spacer(Modifier.height(8.dp))
                    if (book.rules.isEmpty()) {
                        Text(
                            "Eine Aktion prüft Bedingungen (Block belegt, Uhrzeit, Zug, Brennstoff …) und führt Befehle aus, sobald sie erfüllt sind.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    book.rules.forEach { rule ->
                        val active = rule.id == window.ruleId && !window.newRule
                        Surface(
                            color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { select(rule.id) },
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(rule.name.ifBlank { rule.id }, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    when {
                                        !rule.enabled -> "aus"
                                        rule.id in satisfied -> "erfüllt"
                                        else -> "wartet"
                                    } + " · ${rule.commands.size} Befehl" + if (rule.commands.size == 1) "" else "e",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (rule.enabled && rule.id in satisfied) Moba.colors.signalGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (wide) VerticalDivider(Modifier.padding(horizontal = 12.dp))
            }
            if (editing) {
                val draft = selected ?: ActionRule(service.newId(), "", condition = Condition.and())
                key(draft.id, window.newRule) {
                    RuleEditor(app, service, draft, isNew = selected == null, satisfied = draft.id in satisfied, back = if (wide) null else ({ select(null) }))
                }
            } else if (wide) {
                Text("Links eine Aktion wählen oder eine neue anlegen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RuleEditor(app: MobaApp, service: ActionService, initial: ActionRule, isNew: Boolean, satisfied: Boolean, back: (() -> Unit)?) {
    var rule by remember { mutableStateOf(initial) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            back?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } }
            OutlinedTextField(rule.name, { rule = rule.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
            Gap(12)
            Text("Aktiv")
            Gap(6)
            Switch(rule.enabled, { rule = rule.copy(enabled = it) })
        }
        if (!isNew) {
            StatusPill(
                if (satisfied) "Bedingung erfüllt" else "Bedingung nicht erfüllt",
                if (satisfied) Moba.colors.signalGreen else MaterialTheme.colorScheme.outline,
            )
        }
        SectionTitle("Bedingung")
        ConditionNode(app, rule.condition, depth = 0, onChange = { rule = rule.copy(condition = it) }, onDelete = null)
        SectionTitle("Befehle")
        rule.commands.forEachIndexed { i, command ->
            CommandRow(
                app, i, command,
                onChange = { c -> rule = rule.copy(commands = rule.commands.toMutableList().also { it[i] = c }) },
                onMove = { delta ->
                    val j = i + delta
                    if (j in rule.commands.indices) {
                        rule = rule.copy(commands = rule.commands.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t })
                    }
                },
                onDelete = { rule = rule.copy(commands = rule.commands.filterIndexed { k, _ -> k != i }) },
            )
        }
        AddMenu("Befehl", CommandType.entries, { it.label }) { rule = rule.copy(commands = rule.commands + Command(it)) }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        HorizontalDivider()
        Row(horizontalArrangement = rowSpacing, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                service.save(rule.copy(name = rule.name.trim().ifEmpty { "Aktion ${rule.id.removePrefix("ak-")}" }))
                message = null
                ActionsUiState.window.value = ActionsWindow(ruleId = rule.id)
            }) { Text("Speichern") }
            OutlinedButton(
                onClick = { message = runCatching { service.run(rule.id) }.exceptionOrNull()?.message },
                enabled = !isNew && service.canRun,
            ) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                Gap(6)
                Text("Jetzt ausführen")
            }
            if (!isNew) {
                TextButton(onClick = {
                    service.delete(rule.id)
                    ActionsUiState.window.value = ActionsWindow()
                }) {
                    Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                    Gap(6)
                    Text("Löschen")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))

/** Eine Bedingung mit ihren Parametern; ein Operator zeigt seine Unterbedingungen eingerueckt darunter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionNode(app: MobaApp, c: Condition, depth: Int, onChange: (Condition) -> Unit, onDelete: (() -> Unit)?) {
    val operator = c.type == ConditionType.OPERATOR
    Column(
        Modifier.fillMaxWidth().padding(start = if (depth > 0) 20.dp else 0.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FlowRow(horizontalArrangement = rowSpacing, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Picker(c.type.label, ConditionType.entries, { it.label }) { type ->
                // Ein Operator behaelt seine Unterbedingungen, alle anderen fangen mit den Vorgaben an
                onChange(if (type == c.type) c else Condition(type, negate = c.negate, children = if (type == ConditionType.OPERATOR) c.children else emptyList()))
            }
            FilterChip(c.negate, { onChange(c.copy(negate = !c.negate)) }, label = { Text("nicht") })
            c.type.params.forEach { spec ->
                ParamField(app, spec, c.params[spec.key].orEmpty()) { onChange(c.copy(params = c.params + (spec.key to it))) }
            }
            onDelete?.let { IconButton(onClick = it) { Icon(Icons.Outlined.Delete, "Bedingung entfernen") } }
        }
        if (operator) {
            c.children.forEachIndexed { i, child ->
                ConditionNode(
                    app, child, depth + 1,
                    onChange = { n -> onChange(c.copy(children = c.children.toMutableList().also { it[i] = n })) },
                    onDelete = { onChange(c.copy(children = c.children.filterIndexed { k, _ -> k != i })) },
                )
            }
            AddMenu("Bedingung", ConditionType.entries, { it.label }) { type -> onChange(c.copy(children = c.children + Condition(type))) }
        } else {
            Text(c.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommandRow(app: MobaApp, index: Int, c: Command, onChange: (Command) -> Unit, onMove: (Int) -> Unit, onDelete: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).padding(8.dp),
        horizontalArrangement = rowSpacing,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("${index + 1}.", fontWeight = FontWeight.SemiBold)
        Picker(c.type.label, CommandType.entries, { it.label }) { if (it != c.type) onChange(Command(it)) }
        c.type.params.forEach { spec ->
            ParamField(app, spec, c.params[spec.key].orEmpty()) { onChange(c.copy(params = c.params + (spec.key to it))) }
        }
        IconButton(onClick = { onMove(-1) }) { Icon(Icons.Outlined.ArrowUpward, "Nach oben") }
        IconButton(onClick = { onMove(1) }) { Icon(Icons.Outlined.ArrowDownward, "Nach unten") }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Befehl entfernen") }
    }
}

/** Ein Parameter: Auswahl, Zahl, Melder, Lok, Fahrstrasse oder Text. Leer = Vorgabe. */
@Composable
private fun ParamField(app: MobaApp, spec: ParamSpec, value: String, onChange: (String) -> Unit) {
    val shown = value.ifEmpty { spec.default }
    when (spec.kind) {
        ParamKind.CHOICE -> Picker(
            "${spec.label}: ${spec.options.firstOrNull { it.first == shown }?.second ?: shown}",
            spec.options, { it.second },
        ) { onChange(it.first) }
        ParamKind.ROUTE, ParamKind.AUTOMATION -> {
            val session by app.session.collectAsState()
            val routes = session?.host?.service(RouteService::class)
            val book = routes?.book?.collectAsState()?.value
            val options = if (spec.kind == ParamKind.ROUTE) book?.routes.orEmpty().map { it.id to it.name }
            else book?.shuttles.orEmpty().map { it.id to "Pendel: ${it.name}" } + book?.trainRoutes.orEmpty().map { it.id to it.name }
            Picker("${spec.label}: ${options.firstOrNull { it.first == shown }?.second ?: shown.ifEmpty { "wählen" }}", options, { it.second }) { onChange(it.first) }
        }
        ParamKind.LOCO -> {
            val layout by app.layout.layout.collectAsState()
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallField(spec, value, KeyboardType.Number, { it.filter(Char::isDigit).take(5) }, onChange)
                if (layout.locos.isNotEmpty()) {
                    Picker("…", layout.locos, { "${it.name} (${it.address})" }) { onChange(it.address.toString()) }
                }
            }
        }
        ParamKind.NUMBER -> SmallField(spec, value, KeyboardType.Number, { it.filter(Char::isDigit).take(5) }, onChange)
        ParamKind.FEEDBACK -> SmallField(spec, value, KeyboardType.Decimal, { it.filter { c -> c.isDigit() || c == '.' }.take(7) }, onChange)
        ParamKind.TIME -> SmallField(spec, value, KeyboardType.Number, { it.filter { c -> c.isDigit() || c == ':' }.take(5) }, onChange)
        ParamKind.TEXT -> OutlinedTextField(value, onChange, label = { Text(spec.label) }, placeholder = { Text(spec.default) }, singleLine = true, modifier = Modifier.width(280.dp))
    }
}

@Composable
private fun SmallField(spec: ParamSpec, value: String, keyboard: KeyboardType, filter: (String) -> String, onChange: (String) -> Unit) =
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(filter(it)) },
        label = { Text(spec.label, maxLines = 1) },
        placeholder = { Text(spec.default.ifEmpty { if (spec.kind == ParamKind.FEEDBACK) "1.3" else if (spec.optional) "beliebig" else "" }) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.width(if (spec.label.length > 14) 210.dp else 150.dp),
    )

/** Knopf mit aufklappender Auswahl. */
@Composable
private fun <T> Picker(text: String, options: List<T>, label: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = {
                    open = false
                    onPick(option)
                })
            }
        }
    }
}

@Composable
private fun <T> AddMenu(what: String, options: List<T>, label: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
            Gap(6)
            Text("$what hinzufügen")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = {
                    open = false
                    onPick(option)
                })
            }
        }
    }
}

// ---------------------------------------------------------------- Brennstoff und Protokoll

@Composable
private fun FuelTab(app: MobaApp, service: ActionService) {
    val session by app.session.collectAsState()
    val vehicles = session?.host?.service(VehicleService::class)
    val fleet = vehicles?.fleet?.collectAsState()?.value
    val book by service.book.collectAsState()
    val locos = fleet?.locos.orEmpty().filter { it.traction.fuelMinutes() != null }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Dampfloks reichen 20 Minuten, Diesel und Triebwagen 60 Minuten volle Fahrt; langsamer hält der Vorrat länger. " +
                "Mit der Bedingung „Lokomotive-Brennstoff“ und dem Befehl „Auftanken“ lässt sich Wasser fassen oder Tanken nachspielen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (locos.isEmpty()) Text("Keine Dampf- oder Diesellok in den Fahrzeugen.")
        locos.forEach { loco ->
            val percent = book.fuel[loco.address] ?: 100.0
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(200.dp)) {
                    Text(loco.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${loco.traction?.label} · Adresse ${loco.address}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(
                    progress = { (percent / 100).toFloat() },
                    color = if (percent < 20) Moba.colors.alarm else Moba.colors.signalGreen,
                    modifier = Modifier.weight(1f).height(8.dp),
                )
                Text("${percent.toInt()} %", modifier = Modifier.width(52.dp))
                OutlinedButton(onClick = { service.refuel(loco.address) }) { Text("Auftanken") }
            }
        }
    }
}

@Composable
private fun LogTab(service: ActionService) {
    val log by service.log.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (log.isEmpty()) Text("Noch nichts ausgeführt.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        log.forEach { entry ->
            Text(entry.text, color = if (entry.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
    }
}
