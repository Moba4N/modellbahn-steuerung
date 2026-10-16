package de.moba.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Ein Menue der Menueleiste; [entries] wird erst beim Aufklappen gelesen, damit Haken und Sperren stimmen. */
internal class MenuSpec(val title: String, val entries: () -> List<MenuEntry>)

const val MENU_BAR_TAG = "menueleiste"

/** Menueleiste wie unter Windows: Datei, Ansicht, je ein Menue pro Reiter, Zentrale, Werkzeuge, Hilfe. */
@Composable
internal fun MenuBar(menus: List<MenuSpec>) {
    val openMenu by ShellUiState.menuBarOpen.collectAsState()
    fun close() {
        ShellUiState.menuBarOpen.value = null
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().height(34.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp).testTag(MENU_BAR_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            menus.forEach { menu ->
                val open = openMenu == menu.title
                Box {
                    Text(
                        menu.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (open) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(if (open) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable { ShellUiState.menuBarOpen.value = if (open) null else menu.title }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                    DropdownMenu(expanded = open, onDismissRequest = ::close, modifier = Modifier.widthIn(min = 220.dp)) {
                        if (open) {
                            menu.entries().forEach { entry ->
                                if (entry.separatorBefore) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text(entry.title) },
                                    enabled = entry.enabled,
                                    // Platz fuer den Haken immer frei lassen, damit die Texte buendig stehen
                                    leadingIcon = {
                                        if (entry.checked) {
                                            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        } else {
                                            Spacer(Modifier.size(18.dp))
                                        }
                                    },
                                    onClick = {
                                        close()
                                        entry.onClick()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
