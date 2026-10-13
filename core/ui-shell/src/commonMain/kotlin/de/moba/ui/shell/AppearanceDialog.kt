package de.moba.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Einstellungen zum Erscheinungsbild: Design, hell/dunkel, Schrift und eigenes Hintergrundbild.
 * Alles ist kostenlos und wird sofort uebernommen und gespeichert.
 */
@Composable
internal fun AppearanceDialog(app: MobaApp, picker: ImagePicker?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(16.dp).widthIn(max = 640.dp),
        ) {
            Column {
                Row(Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Erscheinungsbild", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Schließen") }
                }
                Column(
                    Modifier.heightIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(24.dp, 8.dp, 24.dp, 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    AppearanceSettings(app, picker)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSettings(app: MobaApp, picker: ImagePicker?) {
    val appearance by app.appearance.collectAsState()
    val dark by app.darkTheme.collectAsState()
    val background by app.backgroundImage.collectAsState()
    var imageError by remember { mutableStateOf(false) }
    fun update(change: Appearance.() -> Appearance) = app.setAppearance(appearance.change())

    Section("Design") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DesignStyle.entries.forEach { style ->
                StyleTile(style, dark, selected = appearance.style == style) { update { copy(style = style) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = dark, onClick = { app.setDarkTheme(true) }, label = { Text("Dunkel") })
            FilterChip(selected = !dark, onClick = { app.setDarkTheme(false) }, label = { Text("Hell") })
        }
    }

    Section("Schriftart") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FontChoice.entries.forEach { font ->
                FilterChip(
                    selected = appearance.font == font,
                    onClick = { update { copy(font = font) } },
                    label = { Text(font.label, fontFamily = font.family) },
                )
            }
        }
        Text("Schriftgröße", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextSize.entries.forEach { size ->
                FilterChip(selected = appearance.textSize == size, onClick = { update { copy(textSize = size) } }, label = { Text(size.label) })
            }
        }
    }

    Section("Hintergrundbild") {
        Text(
            "Ein eigenes Foto, z. B. von deiner Anlage, liegt blass als Wasserzeichen hinter allen Ansichten.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(132.dp, 84.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                val image = background
                if (image != null) {
                    Image(image, null, contentScale = ContentScale.Crop, alpha = appearance.watermark.alpha * 3f, modifier = Modifier.matchParentSize())
                } else {
                    Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.outline)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = picker != null,
                    onClick = { picker?.pick { bytes -> if (bytes != null) imageError = !app.setBackgroundImage(bytes) } },
                ) { Text(if (background == null) "Bild wählen …" else "Anderes Bild …") }
                if (background != null) {
                    TextButton(onClick = { app.setBackgroundImage(null); imageError = false }) { Text("Bild entfernen") }
                }
            }
        }
        if (imageError) {
            Text("Diese Datei ist kein lesbares Bild (PNG oder JPEG).", color = Moba.colors.alarm, style = MaterialTheme.typography.bodySmall)
        }
        Text("Stärke", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WatermarkStrength.entries.forEach { strength ->
                FilterChip(
                    selected = appearance.watermark == strength,
                    enabled = background != null,
                    onClick = { update { copy(watermark = strength) } },
                    label = { Text(strength.label) },
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

/** Kachel mit einer kleinen Vorschau des Designs in den eigenen Farben. */
@Composable
private fun StyleTile(style: DesignStyle, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    val scheme = scheme(style, dark)
    val moba = mobaColors(style, dark)
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    Column(
        Modifier
            .width(176.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(border, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Miniatur: Kopfleiste, Seitenleiste und eine Karte
        Column(Modifier.fillMaxWidth().height(72.dp).clip(MaterialTheme.shapes.small).background(scheme.background)) {
            Row(
                Modifier.fillMaxWidth().height(16.dp).background(scheme.surfaceContainerLow).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Swatch(scheme.primary)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(18.dp, 8.dp).clip(CircleShape).background(moba.alarm))
            }
            Row(Modifier.weight(1f)) {
                Box(Modifier.width(16.dp).fillMaxHeight().background(scheme.surfaceContainerLow))
                Column(
                    Modifier.padding(6.dp).weight(1f).clip(shapeOf(style)).background(scheme.surfaceContainer).padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.fillMaxWidth(0.7f).height(5.dp).clip(CircleShape).background(scheme.onSurface.copy(alpha = 0.7f)))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Swatch(moba.signalGreen)
                        Swatch(moba.occupied)
                        Swatch(scheme.primary)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(style.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Text(style.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

private fun shapeOf(style: DesignStyle) = when (style) {
    DesignStyle.MODERN -> androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    DesignStyle.WINDOWS11 -> androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    DesignStyle.STELLWERK -> androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
}
