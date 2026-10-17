package de.moba.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Input
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Output
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.moba.core.files.BackupInfo
import de.moba.core.files.DataPart
import de.moba.core.files.LayoutFiles
import de.moba.core.files.LayoutInfo
import de.moba.core.files.ResourceFile
import de.moba.core.files.ResourceKind
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Anlagen-Dateien fuer das Datei-Menue; [reload] startet die Module mit der jetzt offenen Anlage neu. */
class FileSupport(val files: LayoutFiles, val reload: () -> Unit)

/** Dialoge des Datei-Menues. */
enum class FileDialogKind { OPEN, NEW, BACKUP, IMPORT, EXPORT, RESOURCES, PRINT }

/** Zustand des Datei-Menues, von aussen setzbar (z. B. fuer Vorschaubilder). */
object FileUiState {
    val menuOpen = kotlinx.coroutines.flow.MutableStateFlow(false)
    val dialog = kotlinx.coroutines.flow.MutableStateFlow<FileDialogKind?>(null)
}

/** Zweites Burger-Menue links oben: Anlagen, Sicherung, Import, Export, Ressourcen, Drucken. */
@Composable
internal fun FileMenu(support: FileSupport, compact: Boolean, unsaved: () -> List<UnsavedWork>, run: (() -> Unit) -> Unit) {
    val open by FileUiState.menuOpen.collectAsState()
    val current by support.files.current.collectAsState()
    val recent by support.files.recent.collectAsState()
    fun close() {
        FileUiState.menuOpen.value = false
    }
    fun show(kind: FileDialogKind) {
        close()
        FileUiState.dialog.value = kind
    }
    Box {
        if (compact) {
            IconButton(onClick = { FileUiState.menuOpen.value = !open }) { Icon(Icons.Filled.Menu, "Datei") }
        } else {
            TextButton(onClick = { FileUiState.menuOpen.value = !open }) {
                Icon(Icons.Filled.Menu, null, Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Datei", style = MaterialTheme.typography.titleSmall)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = ::close, modifier = Modifier.widthIn(min = 260.dp)) {
            MenuHeader("Anlage: ${current.name}")
            MenuItem("Neue Anlage …", Icons.Outlined.CreateNewFolder) { show(FileDialogKind.NEW) }
            MenuItem("Anlage öffnen …", Icons.Outlined.FolderOpen) { show(FileDialogKind.OPEN) }
            val pending = unsaved()
            MenuItem("Alles speichern", Icons.Outlined.Save, enabled = pending.isNotEmpty() && pending.all { it.canSave }) {
                close()
                pending.forEach { runCatching { it.save() } }
            }
            if (recent.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                MenuHeader("Zuletzt verwendet")
                recent.forEach { layout ->
                    MenuItem(layout.name, Icons.Outlined.History) {
                        close()
                        run { openLayout(support, layout.path) }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            MenuItem("Sicherung …", Icons.Outlined.Backup) { show(FileDialogKind.BACKUP) }
            MenuItem("Import …", Icons.AutoMirrored.Outlined.Input) { show(FileDialogKind.IMPORT) }
            MenuItem("Export …", Icons.Outlined.Output) { show(FileDialogKind.EXPORT) }
            MenuItem("Ressourcen-Browser …", Icons.Outlined.Inventory2) { show(FileDialogKind.RESOURCES) }
            MenuItem("Drucken …", Icons.Outlined.Print) { show(FileDialogKind.PRINT) }
        }
    }
}

private fun openLayout(support: FileSupport, path: String): String? = runCatching {
    support.files.open(path)
    support.reload()
}.exceptionOrNull()?.message

@Composable
private fun MenuHeader(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
)

@Composable
private fun MenuItem(text: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) =
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, enabled = enabled, onClick = onClick)

/**
 * Offene Dialoge des Datei-Menues und die Rueckfrage bei ungespeicherten Aenderungen, bevor eine andere
 * Anlage geladen wird. [printables] sind alle druckbaren Seiten der sichtbaren Reiter.
 */
@Composable
internal fun FileDialogs(support: FileSupport, unsaved: () -> List<UnsavedWork>, printables: () -> List<PrintItem>, guard: FileGuard) {
    val dialog by FileUiState.dialog.collectAsState()
    val close = { FileUiState.dialog.value = null }
    when (dialog) {
        FileDialogKind.OPEN -> OpenDialog(support, guard, close)
        FileDialogKind.NEW -> NewDialog(support, guard, close)
        FileDialogKind.BACKUP -> BackupDialog(support, guard, close)
        FileDialogKind.IMPORT -> ImportDialog(support, guard, close)
        FileDialogKind.EXPORT -> ExportDialog(support, close)
        FileDialogKind.RESOURCES -> ResourcesDialog(support, close)
        FileDialogKind.PRINT -> PrintDialog(printables(), close)
        null -> Unit
    }
    guard.pending?.let { action ->
        UnsavedDialog(unsaved(), onProceed = {
            guard.pending = null
            action()
        }, onCancel = { guard.pending = null })
    }
}

/** Fuehrt Aktionen, die die Daten neu laden, erst nach Rueckfrage aus, wenn noch etwas ungespeichert ist. */
internal class FileGuard(private val unsaved: () -> List<UnsavedWork>) {
    var pending by mutableStateOf<(() -> Unit)?>(null)

    fun run(action: () -> Unit) {
        if (unsaved().isEmpty()) action() else pending = action
    }
}

@Composable
private fun UnsavedDialog(pending: List<UnsavedWork>, onProceed: () -> Unit, onCancel: () -> Unit) {
    if (pending.isEmpty()) return onProceed()
    var error by remember { mutableStateOf<String?>(null) }
    val canSave = pending.all { it.canSave }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Ungespeicherte Änderungen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${pending.joinToString(" und ") { it.title }}: Es gibt Änderungen, die noch nicht gespeichert sind. Beim Laden anderer Daten gehen sie verloren.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (canSave) {
                Button(onClick = {
                    runCatching { pending.forEach { it.save() } }
                        .onSuccess { onProceed() }
                        .onFailure { error = "Speichern fehlgeschlagen: ${it.message}" }
                }) { Text("Speichern und weiter") }
            }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onProceed) { Text("Ohne Speichern weiter") }
                TextButton(onClick = onCancel) { Text("Abbrechen") }
            }
        },
    )
}

/** Grundgeruest der Datei-Dialoge mit Schliessen-Knopf. */
@Composable
private fun FileDialog(title: String, icon: ImageVector, onClose: () -> Unit, confirm: @Composable (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(icon, null) },
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        },
        confirmButton = { confirm?.invoke() },
        dismissButton = { TextButton(onClick = onClose) { Text("Schließen") } },
    )
}

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Problem(text: String?) {
    if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

// ---------------------------------------------------------------- Anlagen

@Composable
private fun OpenDialog(support: FileSupport, guard: FileGuard, onClose: () -> Unit) {
    val platform = rememberPlatformFiles()
    val current by support.files.current.collectAsState()
    val recent by support.files.recent.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    var name by remember(current.path) { mutableStateOf(current.name) }
    val layouts = (support.files.available() + recent).distinctBy { it.path }
    fun open(path: String) = guard.run {
        error = openLayout(support, path)
        if (error == null) onClose()
    }
    FileDialog("Anlage öffnen", Icons.Outlined.FolderOpen, onClose) {
        Hint("Jede Anlage hat eigene Gleisbilder, Fahrzeuge, Fahrstraßen, Symbole, Decoder und Klänge.")
        layouts.forEach { layout ->
            LayoutRow(layout, layout.path == current.path) { open(layout.path) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { FileUiState.dialog.value = FileDialogKind.NEW }) { Text("Neue Anlage …") }
            if (platform.canChooseFolder) {
                OutlinedButton(onClick = { platform.chooseFolder { it?.let(::open) } }) { Text("Ordner wählen …") }
            }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Name der offenen Anlage") }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(enabled = name.isNotBlank() && name != current.name, onClick = {
                error = runCatching { support.files.rename(name) }.exceptionOrNull()?.message
            }) { Text("Umbenennen") }
        }
        Hint(current.path)
        if (platform.canOpenFolder) TextButton(onClick = { platform.openFolder(current.path) }) { Text("Ordner im Explorer zeigen") }
        Problem(error)
    }
}

@Composable
private fun LayoutRow(layout: LayoutInfo, selected: Boolean, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        enabled = !selected,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(layout.name, fontWeight = FontWeight.SemiBold)
                Text(layout.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selected) Icon(Icons.Filled.Check, "Offen", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun NewDialog(support: FileSupport, guard: FileGuard, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var copy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    FileDialog("Neue Anlage", Icons.Outlined.CreateNewFolder, onClose, confirm = {
        Button(enabled = name.isNotBlank(), onClick = {
            guard.run {
                error = runCatching {
                    // Daten der bisherigen Anlage mitnehmen, z. B. als Ausgangspunkt fuer eine Clubanlage
                    val data = if (copy) support.files.export(support.files.parts()) else null
                    support.files.create(name)
                    data?.let { support.files.import(it, support.files.inspect(it)) }
                    support.reload()
                }.exceptionOrNull()?.message
                if (error == null) onClose()
            }
        }) { Text("Anlegen und öffnen") }
    }) {
        OutlinedTextField(name, { name = it.take(60) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(copy, { copy = it })
            Text("Daten der offenen Anlage übernehmen")
        }
        Hint(if (copy) "Die neue Anlage startet als Kopie der offenen." else "Die neue Anlage startet leer mit der Beispielanlage.")
        Problem(error)
    }
}

// ---------------------------------------------------------------- Sicherung

@Composable
private fun BackupDialog(support: FileSupport, guard: FileGuard, onClose: () -> Unit) {
    val platform = rememberPlatformFiles()
    var backups by remember { mutableStateOf(support.files.backups()) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf<BackupInfo?>(null) }
    fun refresh() {
        backups = support.files.backups()
    }
    FileDialog("Sicherung", Icons.Outlined.Backup, onClose, confirm = {
        Button(onClick = {
            runCatching { support.files.backup() }
                .onSuccess { message = "Gesichert: ${it.name}"; error = null; refresh() }
                .onFailure { error = "Sichern fehlgeschlagen: ${it.message}" }
        }) { Text("Jetzt sichern") }
    }) {
        Hint("Eine Sicherung enthält alle Daten der Anlage als ZIP. Die App sichert einmal täglich beim Start automatisch und behält die letzten 10 automatischen Sicherungen.")
        if (backups.isEmpty()) Text("Noch keine Sicherung.")
        backups.forEach { b ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(backupTitle(b), fontWeight = FontWeight.SemiBold)
                    Text("${formatDate(b.modifiedMillis)} · ${formatSize(b.size)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { confirmRestore = b }) { Text("Wiederherstellen") }
                        TextButton(onClick = {
                            support.files.read("${"sicherungen"}/${b.name}")?.let { bytes -> platform.save(b.name, bytes) { it?.let { p -> message = "Gespeichert: $p" } } }
                        }) { Text("Speichern unter …") }
                        TextButton(onClick = {
                            support.files.deleteBackup(b.name)
                            refresh()
                        }) { Text("Löschen") }
                    }
                }
            }
        }
        message?.let { Hint(it) }
        Problem(error)
    }
    confirmRestore?.let { b ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Sicherung wiederherstellen?") },
            text = { Text("Die Daten der Anlage werden durch den Stand vom ${formatDate(b.modifiedMillis)} ersetzt. Der jetzige Stand wird vorher gesichert.") },
            confirmButton = {
                Button(onClick = {
                    confirmRestore = null
                    guard.run {
                        runCatching {
                            support.files.restore(b.name)
                            support.reload()
                        }.onSuccess { message = "Wiederhergestellt: ${backupTitle(b)}"; error = null; refresh() }
                            .onFailure { error = "Wiederherstellen fehlgeschlagen: ${it.message}" }
                    }
                }) { Text("Wiederherstellen") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Abbrechen") } },
        )
    }
}

/** "Sicherung 17.10.2026 15:05 (automatisch)" aus dem Dateinamen. */
private fun backupTitle(b: BackupInfo): String {
    val label = b.name.removePrefix("sicherung-").removeSuffix(".zip").split('-').drop(4).joinToString(" ")
    return "Sicherung ${formatDate(b.modifiedMillis)}" + if (label.isBlank()) "" else " ($label)"
}

// ---------------------------------------------------------------- Import und Export

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartChoice(available: Set<DataPart>, chosen: Set<DataPart>, onChange: (Set<DataPart>) -> Unit) {
    Column {
        DataPart.entries.filter { it in available }.forEach { part ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(part in chosen, { on -> onChange(if (on) chosen + part else chosen - part) })
                Text(part.label)
            }
        }
    }
}

@Composable
private fun ExportDialog(support: FileSupport, onClose: () -> Unit) {
    val platform = rememberPlatformFiles()
    val current by support.files.current.collectAsState()
    val available = remember { support.files.parts() }
    var chosen by remember { mutableStateOf(available) }
    var message by remember { mutableStateOf<String?>(null) }
    FileDialog("Export", Icons.Outlined.Output, onClose, confirm = {
        Button(enabled = chosen.isNotEmpty(), onClick = {
            val bytes = support.files.export(chosen)
            val name = current.name.map { if (it.isLetterOrDigit() || it in "-_") it else '-' }.joinToString("")
            platform.save("$name-export.zip", bytes) { it?.let { p -> message = "Exportiert: $p" } }
        }) { Text("Exportieren …") }
    }) {
        Hint("Speichert die gewählten Daten als ZIP, z. B. zum Weitergeben oder für eine zweite Anlage. Mit „Import“ lassen sie sich wieder einlesen.")
        if (available.isEmpty()) Text("Diese Anlage hat noch keine gespeicherten Daten.")
        PartChoice(available, chosen) { chosen = it }
        message?.let { Hint(it) }
    }
}

@Composable
private fun ImportDialog(support: FileSupport, guard: FileGuard, onClose: () -> Unit) {
    val platform = rememberPlatformFiles()
    var zip by remember { mutableStateOf<ByteArray?>(null) }
    var available by remember { mutableStateOf(emptySet<DataPart>()) }
    var chosen by remember { mutableStateOf(emptySet<DataPart>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    FileDialog("Import", Icons.AutoMirrored.Outlined.Input, onClose, confirm = {
        Button(enabled = zip != null && chosen.isNotEmpty(), onClick = {
            val bytes = zip ?: return@Button
            guard.run {
                runCatching {
                    support.files.import(bytes, chosen)
                    support.reload()
                }.onSuccess { done = "Importiert: ${chosen.joinToString { it.label }}."; error = null; zip = null }
                    .onFailure { error = "Import fehlgeschlagen: ${it.message}" }
            }
        }) { Text("Importieren") }
    }) {
        Hint("Liest Daten aus einem Export oder einer Sicherung dieser App. Die gewählten Teile ersetzen die der offenen Anlage; vorher wird gesichert.")
        FilledTonalButton(onClick = {
            platform.pick { bytes ->
                if (bytes == null) return@pick
                runCatching { support.files.inspect(bytes) }
                    .onSuccess { zip = bytes; available = it; chosen = it; error = null; done = null }
                    .onFailure { error = it.message; zip = null }
            }
        }) { Text("ZIP-Datei wählen …") }
        if (zip != null) {
            Text("Enthalten:", fontWeight = FontWeight.SemiBold)
            PartChoice(available, chosen) { chosen = it }
        }
        done?.let { Hint(it) }
        Problem(error)
    }
}

// ---------------------------------------------------------------- Ressourcen-Browser

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResourcesDialog(support: FileSupport, onClose: () -> Unit) {
    val platform = rememberPlatformFiles()
    val sound = LocalSoundSupport.current
    val current by support.files.current.collectAsState()
    var files by remember { mutableStateOf(support.files.resources()) }
    var filter by remember { mutableStateOf<ResourceKind?>(null) }
    var preview by remember { mutableStateOf<Pair<String, ImageBitmap?>?>(null) }
    var confirmDelete by remember { mutableStateOf<ResourceFile?>(null) }
    FileDialog("Ressourcen-Browser", Icons.Outlined.Inventory2, onClose) {
        Hint("Alle Dateien der Anlage „${current.name}“: Daten, Fahrzeugfotos und Klänge.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(filter == null, { filter = null }, label = { Text("Alle (${files.size})") })
            ResourceKind.entries.forEach { kind ->
                val n = files.count { it.kind == kind }
                if (n > 0) FilterChip(filter == kind, { filter = kind }, label = { Text("${kind.label} ($n)") })
            }
        }
        val shown = files.filter { filter == null || it.kind == filter }
        if (shown.isEmpty()) Text("Keine Dateien.")
        shown.forEach { f ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (f.kind) {
                            ResourceKind.IMAGE -> Icons.Outlined.Image
                            ResourceKind.SOUND -> Icons.Outlined.MusicNote
                            else -> Icons.Outlined.Description
                        },
                        null, Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.path, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(f.part?.label, formatSize(f.size), formatDate(f.modifiedMillis)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (f.kind == ResourceKind.IMAGE) {
                        IconButton(onClick = { preview = f.path to support.files.read(f.path)?.let(::decodeImage) }) { Icon(Icons.Outlined.Image, "Ansehen") }
                    }
                    if (f.kind == ResourceKind.SOUND && sound != null && f.path.startsWith("klaenge/")) {
                        IconButton(onClick = { sound.play(f.name) }) { Icon(Icons.Filled.PlayArrow, "Abspielen") }
                    }
                    IconButton(onClick = { support.files.read(f.path)?.let { platform.save(f.name, it) {} } }) { Icon(Icons.Outlined.SaveAlt, "Speichern unter") }
                    IconButton(onClick = { confirmDelete = f }) { Icon(Icons.Outlined.Delete, "Löschen") }
                }
            }
        }
        if (platform.canOpenFolder) TextButton(onClick = { platform.openFolder(current.path) }) { Text("Ordner im Explorer zeigen") }
    }
    preview?.let { (path, image) ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(path.substringAfterLast('/')) },
            text = {
                if (image == null) Text("Kein lesbares Bild.") else Image(image, null, Modifier.heightIn(max = 420.dp).fillMaxWidth())
            },
            confirmButton = { TextButton(onClick = { preview = null }) { Text("Schließen") } },
        )
    }
    confirmDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Datei löschen?") },
            text = {
                Text(
                    "„${f.path}“ wird gelöscht." +
                        if (f.kind == ResourceKind.DATA) " Die App hat die Daten noch geladen; beim nächsten Speichern entsteht die Datei neu." else "",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    support.files.delete(f.path)
                    files = support.files.resources()
                    confirmDelete = null
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Abbrechen") } },
        )
    }
}

// ---------------------------------------------------------------- Drucken

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrintDialog(items: List<PrintItem>, onClose: () -> Unit) {
    val platform = rememberPlatformFiles()
    val scope = rememberCoroutineScope()
    var index by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val layer = rememberGraphicsLayer()
    val item = items.getOrNull(index.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    FileDialog("Drucken", Icons.Outlined.Print, onClose, confirm = {
        Button(enabled = item != null, onClick = {
            val title = item?.title ?: return@Button
            scope.launch { error = platform.print(title, layer.toImageBitmap()) }
        }) { Text("Drucken …") }
    }) {
        if (items.isEmpty()) {
            Text("Nichts zum Drucken: Gleisbilder und Fahrzeuge erscheinen hier, sobald ihre Module aktiv sind.")
            return@FileDialog
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEachIndexed { i, it -> FilterChip(i == index, { index = i }, label = { Text(it.title) }) }
        }
        Hint("Vorschau. Gedruckt wird auf eine Seite eingepasst, breite Seiten quer.")
        Box(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
            // Hell wie auf Papier; die Ebene nimmt das Gezeichnete fuer den Druck auf
            MobaTheme(dark = false, appearance = Appearance()) {
                Box(
                    Modifier
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        }
                        .background(Color.White)
                        .padding(16.dp),
                ) { item?.content?.invoke() }
            }
        }
        Problem(error)
    }
}

// ---------------------------------------------------------------- Hilfen

internal fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${(bytes + 512) / 1024} KB"
    else -> "${(bytes * 10 / (1024 * 1024)) / 10.0} MB".replace('.', ',')
}

internal fun formatDate(millis: Long): String {
    val t = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${two(t.dayOfMonth)}.${two(t.monthNumber)}.${t.year} ${two(t.hour)}:${two(t.minute)}"
}

/** Druckseite mit Ueberschrift, z. B. ein Gleisbild; [content] in voller Groesse darunter. */
@Composable
fun PrintPage(title: String, subtitle: String = "", content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.Black)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
        content()
    }
}

/** Liste zum Drucken, z. B. alle Loks: Kopfzeile und eine Zeile je Eintrag. */
@Composable
fun PrintTable(title: String, columns: List<String>, rows: List<List<String>>, subtitle: String = "") {
    PrintPage(title, subtitle.ifBlank { "${rows.size} Einträge" }) {
        Column(Modifier.width((columns.size * 150).coerceAtLeast(600).dp)) {
            @Composable
            fun line(cells: List<String>, bold: Boolean) = Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                cells.forEach { cell ->
                    Text(
                        cell, Modifier.weight(1f).padding(end = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                        color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            line(columns, bold = true)
            HorizontalDivider(color = Color.Black)
            rows.forEach { row ->
                line(row, bold = false)
                HorizontalDivider(color = Color.LightGray)
            }
        }
    }
}
