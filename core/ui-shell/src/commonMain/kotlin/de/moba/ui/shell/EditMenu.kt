package de.moba.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.SettingsInputComponent
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.MutableStateFlow

/** Menue "Bearbeiten": ob es aufgeklappt ist und welcher Editor offen ist ([ModuleEditor.key]). */
object EditUiState {
    val menuOpen = MutableStateFlow(false)
    val open = MutableStateFlow<String?>(null)

    /** Gewaehlter Eintrag je Editor ([ModuleEditor.key]); bleibt beim Wechsel zwischen den Listen erhalten. */
    val selection = MutableStateFlow<Map<String, String>>(emptyMap())

    fun select(editor: String, id: String?) {
        selection.value = if (id == null) selection.value - editor else selection.value + (editor to id)
    }
}

/** Gewaehlter Eintrag des Editors [key] und wie man ihn aendert. */
@Composable
fun rememberEditorSelection(key: String): Pair<String?, (String?) -> Unit> {
    val all by EditUiState.selection.collectAsState()
    return all[key] to { id: String? -> EditUiState.select(key, id) }
}

/** Schnittstellen: Zentrale waehlen und verbinden; gehoert zur App selbst, nicht zu einem Modul. */
internal object InterfaceEditor : ModuleEditor {
    override val moduleId = ""
    override val key = "schnittstellen"
    override val title = "Schnittstellen"
    override val icon = Icons.Outlined.SettingsInputComponent
    override val order = 0

    @Composable
    override fun Content(app: MobaApp) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.widthIn(max = 640.dp)) { StationCard(app) }
        }
    }
}

/** Knopf "Bearbeiten" in der oberen Leiste mit allen Objektlisten der laufenden Module. */
@Composable
internal fun EditMenu(editors: List<ModuleEditor>, compact: Boolean) {
    val open by EditUiState.menuOpen.collectAsState()
    fun close() {
        EditUiState.menuOpen.value = false
    }
    Box {
        if (compact) {
            IconButton(onClick = { EditUiState.menuOpen.value = !open }) { Icon(Icons.Outlined.EditNote, "Bearbeiten") }
        } else {
            TextButton(onClick = { EditUiState.menuOpen.value = !open }) {
                Icon(Icons.Outlined.EditNote, null, Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Bearbeiten", style = MaterialTheme.typography.titleSmall)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = ::close, modifier = Modifier.widthIn(min = 240.dp)) {
            editors.forEach { editor ->
                DropdownMenuItem(
                    text = { Text("${editor.title} …") },
                    leadingIcon = { Icon(editor.icon, null) },
                    onClick = {
                        close()
                        EditUiState.open.value = editor.key
                    },
                )
            }
        }
    }
}

/** Grosses Fenster mit dem gewaehlten Editor; links (bzw. oben) lassen sich die anderen Listen waehlen. */
@Composable
internal fun EditWindow(app: MobaApp, editors: List<ModuleEditor>) {
    val key by EditUiState.open.collectAsState()
    val editor = editors.firstOrNull { it.key == key } ?: return
    val close = { EditUiState.open.value = null }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
                val wide = maxWidth >= 900.dp
                Column(Modifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bearbeiten", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(16.dp))
                        if (!wide) EditorPicker(editors, editor) else Text(editor.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = close) { Icon(Icons.Outlined.Close, "Schließen") }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        if (wide) {
                            Column(Modifier.width(210.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                                editors.forEach { e ->
                                    val active = e.key == editor.key
                                    Surface(
                                        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.fillMaxWidth().clickable { EditUiState.open.value = e.key },
                                    ) {
                                        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(e.icon, null, Modifier.size(20.dp))
                                            Spacer(Modifier.width(10.dp))
                                            Text(e.title, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                                        }
                                    }
                                }
                            }
                            VerticalDivider(Modifier.padding(horizontal = 12.dp))
                        }
                        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                            // Die Editoren der Module richten sich nach ihrer Flaeche im Fenster, nicht nach der App
                            CompositionLocalProvider(LocalWideLayout provides (maxWidth >= 760.dp)) {
                                androidx.compose.runtime.key(editor.key) { editor.Content(app) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorPicker(editors: List<ModuleEditor>, current: ModuleEditor) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Icon(current.icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(current.title)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            editors.forEach { e ->
                DropdownMenuItem(text = { Text(e.title) }, leadingIcon = { Icon(e.icon, null) }, onClick = {
                    open = false
                    EditUiState.open.value = e.key
                })
            }
        }
    }
}


/** Kennung fuer einen neuen, noch nicht gespeicherten Eintrag in [ObjectEditor]. */
const val NEW_OBJECT = "<neu>"

/**
 * Liste links, Formular rechts (schmal: Liste oder Formular mit Zurueck) fuer die Editoren im Menue "Bearbeiten".
 * [selected] ist die Id des gewaehlten Eintrags oder [NEW_OBJECT]; [form] zeichnet das Formular dafuer.
 */
@Composable
fun <T> ObjectEditor(
    items: List<T>,
    id: (T) -> String,
    title: (T) -> String,
    subtitle: (T) -> String,
    selected: String?,
    onSelect: (String?) -> Unit,
    newLabel: String?,
    empty: String,
    form: @Composable () -> Unit,
) {
    val wide = LocalWideLayout.current
    @Composable
    fun list(modifier: Modifier) = Column(modifier.verticalScroll(rememberScrollState())) {
        if (newLabel != null) {
            androidx.compose.material3.FilledTonalButton(onClick = { onSelect(NEW_OBJECT) }) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(newLabel)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (items.isEmpty()) Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        items.forEach { item ->
            val active = id(item) == selected
            Surface(
                color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { onSelect(id(item)) },
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(title(item), fontWeight = FontWeight.Medium, maxLines = 1)
                    val sub = subtitle(item)
                    if (sub.isNotEmpty()) {
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }

    @Composable
    fun formPane(modifier: Modifier) = Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!wide) {
                TextButton(onClick = { onSelect(null) }) { Text("‹ Zurück zur Liste") }
            }
            form()
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            list(Modifier.width(300.dp).fillMaxHeight())
            if (selected != null) {
                formPane(Modifier.weight(1f).fillMaxHeight())
            } else {
                Text("Links einen Eintrag wählen.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
    } else if (selected == null) {
        list(Modifier.fillMaxSize())
    } else {
        formPane(Modifier.fillMaxSize())
    }
}

/** Speichern, Loeschen und eine Fehlermeldung unter einem Formular im Menue "Bearbeiten". */
@Composable
fun EditorButtons(message: String?, onSave: (() -> Unit)?, onDelete: (() -> Unit)?) {
    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onSave != null) androidx.compose.material3.Button(onClick = onSave) { Text("Speichern") }
        if (onDelete != null) {
            TextButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Löschen")
            }
        }
    }
}
