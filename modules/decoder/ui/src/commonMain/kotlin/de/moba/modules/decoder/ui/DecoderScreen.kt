package de.moba.modules.decoder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.moba.modules.decoder.api.Cv29Bit
import de.moba.modules.decoder.api.DECODER_MODULE_ID
import de.moba.modules.decoder.api.DecoderAddress
import de.moba.modules.decoder.api.DecoderLicenseException
import de.moba.modules.decoder.api.DecoderManufacturers
import de.moba.modules.decoder.api.DecoderProfile
import de.moba.modules.decoder.api.DecoderService
import de.moba.modules.decoder.api.ProgrammingTrack
import de.moba.modules.decoder.api.StandardCvs
import de.moba.ui.shell.LocalWideLayout
import de.moba.ui.shell.MobaApp
import de.moba.ui.shell.Moba
import de.moba.ui.shell.ModuleScreen
import de.moba.ui.shell.SectionCard
import de.moba.ui.shell.StatusPill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Zustand der Decoder-Ansicht, bleibt beim Wechsel zwischen den Reitern erhalten. */
object DecoderUiState {
    val track = MutableStateFlow(ProgrammingTrack.SERVICE)
    val mainAddress = MutableStateFlow("3")
    val cv = MutableStateFlow("1")
    val value = MutableStateFlow("")

    /** Zuletzt gelesene oder geschriebene Werte, CV zu Wert. */
    val values = MutableStateFlow<Map<Int, Int>>(emptyMap())

    /** Laufende Aktion mit Fortschritt (0..1), null = frei. */
    val busy = MutableStateFlow<Pair<String, Float>?>(null)
    val message = MutableStateFlow<String?>(null)
}

private fun say(text: String?) {
    DecoderUiState.message.value = text
}

/** CVs, die sich nicht schreiben lassen: Version und Herstellerkennung. */
private val READ_ONLY = setOf(7, 8)

/** Fuehrt eine Programmier-Aktion aus, zeigt Fortschritt und Fehler an. */
private fun CoroutineScope.program(label: String, action: suspend ((Float) -> Unit) -> Unit) {
    if (DecoderUiState.busy.value != null) return
    say(null)
    launch {
        DecoderUiState.busy.value = label to 0f
        try {
            action { DecoderUiState.busy.value = label to it }
        } catch (e: Exception) {
            say(e.message ?: "Programmieren fehlgeschlagen.")
        } finally {
            DecoderUiState.busy.value = null
        }
    }
}

private fun store(cv: Int, value: Int) = DecoderUiState.values.update { it + (cv to value) }

/** Reiter "Decoder": CVs lesen und schreiben (kostenlos) und die Decoder-Datenbank (Zusatzlizenz). */
object DecoderScreen : ModuleScreen {
    override val moduleId = DECODER_MODULE_ID
    override val title = "Decoder"
    override val icon = Icons.Outlined.Memory

    @Composable
    override fun Content(app: MobaApp) {
        val session by app.session.collectAsState()
        val service = session?.host?.service(DecoderService::class) ?: return
        val wide = LocalWideLayout.current
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { ProgrammingCards(service) }
                Column(
                    Modifier.width(400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { DatabaseCard(app, service) }
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProgrammingCards(service)
                DatabaseCard(app, service)
            }
        }
    }
}

@Composable
private fun ColumnScope.ProgrammingCards(service: DecoderService) {
    val busy by DecoderUiState.busy.collectAsState()
    val message by DecoderUiState.message.collectAsState()
    if (!service.canProgram) {
        SectionCard(title = "CV-Programmierung", icon = Icons.Outlined.Tune) {
            Text(
                "Diese Zentrale unterstützt das Programmieren in der App noch nicht. " +
                    "Möglich ist es mit Zentralen am Z21-Protokoll (z. B. YaMoRC YD7001, Roco z21) und im Simulator.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    busy?.let { (label, progress) ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    SingleCvCard(service)
    DecoderOverviewCard(service)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SingleCvCard(service: DecoderService) {
    val scope = rememberCoroutineScope()
    val track by DecoderUiState.track.collectAsState()
    val mainAddress by DecoderUiState.mainAddress.collectAsState()
    val cvText by DecoderUiState.cv.collectAsState()
    val valueText by DecoderUiState.value.collectAsState()
    val busy by DecoderUiState.busy.collectAsState()
    val cv = cvText.toIntOrNull()
    val value = valueText.toIntOrNull()
    val pom = track == ProgrammingTrack.MAIN
    val pomAddress = mainAddress.toIntOrNull()

    SectionCard(
        title = "CV-Programmierung",
        icon = Icons.Outlined.Tune,
        trailing = { StatusPill("Kostenlos", Moba.colors.signalGreen) },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgrammingTrack.entries.forEach { t ->
                FilterChip(selected = t == track, onClick = { DecoderUiState.track.value = t }, label = { Text(t.label) })
            }
        }
        if (pom) {
            Text(
                "Auf dem Hauptgleis lässt sich nur schreiben; der Decoder bestätigt nicht.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pom) NumberField("Lokadresse", mainAddress, Modifier.width(120.dp)) { DecoderUiState.mainAddress.value = it }
            NumberField("CV", cvText, Modifier.width(90.dp)) { DecoderUiState.cv.value = it }
            NumberField("Wert", valueText, Modifier.width(90.dp), maxLength = 3) { DecoderUiState.value.value = it }
        }
        Text(
            cv?.let { StandardCvs.info(it) }?.let { "${it.name}${if (it.hint.isNotEmpty()) " – ${it.hint}" else ""}" } ?: " ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Bits des Werts zum Antippen, z. B. fuer CV 29
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (bit in 7 downTo 0) {
                val on = value != null && value and (1 shl bit) != 0
                Box(
                    Modifier.size(width = 34.dp, height = 30.dp).clip(MaterialTheme.shapes.extraSmall)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable {
                            val v = value ?: 0
                            DecoderUiState.value.value = (v xor (1 shl bit)).toString()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$bit",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                enabled = busy == null && !pom && cv != null,
                onClick = {
                    val c = cv ?: return@FilledTonalButton
                    scope.program("CV $c wird gelesen …") {
                        val v = service.read(c)
                        store(c, v)
                        DecoderUiState.value.value = v.toString()
                    }
                },
            ) {
                Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Lesen")
            }
            Button(
                enabled = busy == null && cv != null && value != null && value in 0..255 && (!pom || pomAddress != null),
                onClick = {
                    val c = cv ?: return@Button
                    val v = value ?: return@Button
                    scope.program("CV $c = $v wird geschrieben …") {
                        service.write(c, v, mainAddress = if (pom) pomAddress else null)
                        store(c, v)
                        say("CV $c = $v geschrieben.")
                    }
                },
            ) {
                Icon(Icons.Filled.Upload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Schreiben")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AddressRow(service, pom, pomAddress)
    }
}

/** Lokadresse in einem Schritt setzen: kurz in CV 1, lang in CV 17/18, dazu Bit 5 in CV 29. */
@Composable
private fun AddressRow(service: DecoderService, pom: Boolean, pomAddress: Int?) {
    val scope = rememberCoroutineScope()
    val busy by DecoderUiState.busy.collectAsState()
    var address by remember { mutableStateOf("") }
    val target = address.toIntOrNull()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        NumberField("Adresse", address, Modifier.width(120.dp)) { address = it }
        OutlinedButton(
            enabled = busy == null && target != null && target in 1..DecoderAddress.MAX_LONG && (!pom || pomAddress != null),
            onClick = {
                val a = target ?: return@OutlinedButton
                scope.program("Adresse $a wird geschrieben …") { progress ->
                    val cv29 = DecoderUiState.values.value[29]
                        ?: if (pom) 6 else service.read(29).also { store(29, it) }
                    val cvs = DecoderAddress.cvsFor(a, cv29).entries.toList()
                    cvs.forEachIndexed { i, (cv, v) ->
                        service.write(cv, v, mainAddress = if (pom) pomAddress else null)
                        store(cv, v)
                        progress((i + 1f) / cvs.size)
                    }
                    say("Adresse $a geschrieben (${if (a <= DecoderAddress.MAX_SHORT) "kurz, CV 1" else "lang, CV 17/18"}).")
                }
            },
        ) { Text("Adresse schreiben") }
    }
}

@Composable
private fun DecoderOverviewCard(service: DecoderService) {
    val scope = rememberCoroutineScope()
    val values by DecoderUiState.values.collectAsState()
    val busy by DecoderUiState.busy.collectAsState()
    val track by DecoderUiState.track.collectAsState()
    SectionCard(
        title = "Decoder",
        icon = Icons.Outlined.Memory,
        trailing = {
            FilledTonalButton(
                enabled = busy == null && track == ProgrammingTrack.SERVICE,
                onClick = {
                    scope.program("Decoder wird ausgelesen …") { progress ->
                        val cvs = StandardCvs.readSet
                        cvs.forEachIndexed { i, cv ->
                            store(cv, service.read(cv))
                            progress((i + 1f) / cvs.size)
                        }
                    }
                },
            ) { Text("Decoder auslesen") }
        },
    ) {
        if (values.isNotEmpty()) {
            val parts = listOfNotNull(
                values[8]?.let { "Hersteller: ${DecoderManufacturers.name(it)}" },
                values[7]?.let { "Version $it" },
                DecoderAddress.from(values)?.let { "Adresse $it" },
            )
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), fontWeight = FontWeight.SemiBold)
        } else {
            Text(
                "Lok aufs Programmiergleis stellen und „Decoder auslesen“ tippen.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Standard-CVs und alle weiteren bekannten Werte
        val rows = (StandardCvs.readSet + values.keys).distinct().sorted()
        Column {
            rows.forEach { cv ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraSmall)
                        .clickable {
                            DecoderUiState.cv.value = cv.toString()
                            values[cv]?.let { DecoderUiState.value.value = it.toString() }
                        }
                        .padding(vertical = 5.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("CV $cv", fontFamily = FontFamily.Monospace, modifier = Modifier.width(72.dp))
                    Text(
                        StandardCvs.info(cv)?.name ?: "Herstellerspezifisch",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(values[cv]?.toString() ?: "–", fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                }
            }
        }
        values[29]?.let { cv29 -> Cv29Editor(service, cv29) }
    }
}

/** Die Schalter in CV 29 einzeln, schreibt sofort. */
@Composable
private fun Cv29Editor(service: DecoderService, cv29: Int) {
    val scope = rememberCoroutineScope()
    val busy by DecoderUiState.busy.collectAsState()
    val track by DecoderUiState.track.collectAsState()
    val mainAddress by DecoderUiState.mainAddress.collectAsState()
    Text("Konfiguration (CV 29)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Cv29Bit.entries.forEach { bit ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = bit.isSet(cv29),
                enabled = busy == null,
                onCheckedChange = { on ->
                    val next = bit.set(cv29, on)
                    scope.program("CV 29 = $next wird geschrieben …") {
                        service.write(29, next, mainAddress = if (track == ProgrammingTrack.MAIN) mainAddress.toIntOrNull() else null)
                        store(29, next)
                    }
                },
            )
            Text(bit.label)
        }
    }
}

// ---------------------------------------------------------------- Datenbank

@Composable
private fun DatabaseCard(app: MobaApp, service: DecoderService) {
    val profiles by service.profiles.collectAsState()
    val values by DecoderUiState.values.collectAsState()
    SectionCard(
        title = "Decoder-Datenbank",
        icon = Icons.Outlined.Storage,
        trailing = {
            if (service.canUseDatabase) {
                StatusPill("Lizenz aktiv", Moba.colors.signalGreen)
            } else {
                StatusPill("Zusatzlizenz", Moba.colors.locked, Icons.Filled.Lock)
            }
        },
    ) {
        if (!service.canUseDatabase) {
            Text(
                "Sichere die Programmierung jeder Lok und schreibe sie jederzeit zurück, z. B. nach einem " +
                    "Decoder-Reset oder auf einen neuen Decoder. Dafür gibt es die Zusatzlizenz „Decoder-Datenbank“; " +
                    "im Club-Abo ist sie enthalten.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = {
                say("Die Decoder-Datenbank gibt es mit der Zusatzlizenz „Decoder-Datenbank“. CVs lesen und schreiben bleibt kostenlos.")
            }) {
                Icon(Icons.Filled.Lock, null, tint = Moba.colors.locked, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("${values.size} CVs speichern")
            }
            return@SectionCard
        }
        SaveProfileForm(app, service, values)
        if (profiles.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        profiles.forEach { ProfileCard(service, it) }
    }
}

@Composable
private fun SaveProfileForm(app: MobaApp, service: DecoderService, values: Map<Int, Int>) {
    val layout by app.layout.layout.collectAsState()
    var name by remember { mutableStateOf("") }
    var decoderType by remember { mutableStateOf("") }
    var loco by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var menu by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(60) },
        label = { Text("Name, z. B. „BR 218 Sound“") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Box {
        OutlinedTextField(
            value = loco?.let { "${it.first} (Adresse ${it.second})" } ?: "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Lok") },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clip(MaterialTheme.shapes.extraSmall).clickable { menu = true })
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("keine Lok") }, onClick = {
                loco = null
                menu = false
            })
            layout.locos.sortedBy { it.name }.forEach { l ->
                DropdownMenuItem(text = { Text("${l.name} (Adresse ${l.address})") }, onClick = {
                    loco = l.name to l.address
                    if (name.isBlank()) name = l.name
                    menu = false
                })
            }
        }
    }
    OutlinedTextField(
        value = decoderType,
        onValueChange = { decoderType = it.take(60) },
        label = { Text("Decoder, z. B. „ESU LokSound 5“") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        enabled = values.isNotEmpty() && name.isNotBlank(),
        onClick = {
            try {
                service.saveProfile(
                    DecoderProfile(
                        id = service.newProfileId(),
                        name = name.trim(),
                        locoName = loco?.first.orEmpty(),
                        locoAddress = loco?.second,
                        decoderType = decoderType.trim(),
                        cvs = values,
                    ),
                )
                say("„${name.trim()}“ mit ${values.size} CVs gespeichert.")
                name = ""
                decoderType = ""
                loco = null
            } catch (e: DecoderLicenseException) {
                say(e.message)
            } catch (e: IllegalArgumentException) {
                say(e.message)
            }
        },
    ) {
        Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("${values.size} CVs speichern")
    }
}

@Composable
private fun ProfileCard(service: DecoderService, profile: DecoderProfile) {
    val scope = rememberCoroutineScope()
    val busy by DecoderUiState.busy.collectAsState()
    val track by DecoderUiState.track.collectAsState()
    val mainAddress by DecoderUiState.mainAddress.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Pin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(profile.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (confirmDelete) {
                    TextButton(onClick = { service.deleteProfile(profile.id) }) { Text("Löschen?") }
                } else {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, "Löschen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                listOfNotNull(
                    profile.locoName.takeIf { it.isNotBlank() }?.let { n -> profile.locoAddress?.let { "$n · Adresse $it" } ?: n },
                    profile.decoderType.takeIf { it.isNotBlank() } ?: profile.manufacturer,
                    "${profile.cvs.size} CVs",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    DecoderUiState.values.value = profile.cvs
                    say("„${profile.name}“ geladen. Die Werte stehen links in der Übersicht.")
                }) { Text("Anzeigen") }
                Button(
                    enabled = busy == null,
                    onClick = {
                        val pomAddress = if (track == ProgrammingTrack.MAIN) mainAddress.toIntOrNull() else null
                        scope.program("„${profile.name}“ wird auf den Decoder geschrieben …") { progress ->
                            val cvs = profile.cvs.filterKeys { it !in READ_ONLY }.toSortedMap().entries.toList()
                            cvs.forEachIndexed { i, (cv, v) ->
                                service.write(cv, v, mainAddress = pomAddress)
                                store(cv, v)
                                progress((i + 1f) / cvs.size)
                            }
                            say("${cvs.size} CVs aus „${profile.name}“ geschrieben.")
                        }
                    },
                ) {
                    Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Auf Decoder schreiben")
                }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, maxLength: Int = 5, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(maxLength)) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
