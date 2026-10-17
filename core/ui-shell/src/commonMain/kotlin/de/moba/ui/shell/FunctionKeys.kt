package de.moba.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow

/** Wie viele Funktionstasten Fahrpult und Fahrregler zeigen. Die meisten Zentralen schalten bis F28, die Z21 bis F31. */
enum class FunctionRange(val last: Int, val label: String) {
    F12(12, "F0–F12"),
    F20(20, "F0–F20"),
    F28(28, "F0–F28"),
    F31(31, "F0–F31"),
}

/** Gemeinsame Einstellung fuer alle Fahrpulte und Fahrregler; wird in den Einstellungen gemerkt. */
object FunctionKeysState {
    private const val PREF = "fahren.funktionen"
    private val range = MutableStateFlow<FunctionRange?>(null)

    @Composable
    fun range(app: MobaApp): FunctionRange {
        val r by range.collectAsState()
        return r ?: (FunctionRange.entries.firstOrNull { it.name == app.preference(PREF) } ?: FunctionRange.F12).also { range.value = it }
    }

    fun set(app: MobaApp, value: FunctionRange) {
        range.value = value
        app.setPreference(PREF, value.name)
    }

    /** Nur fuer Tests und Vorschaubilder. */
    fun reset() {
        range.value = null
    }
}

const val FUNCTION_KEYS_TAG = "funktionstasten"

/** Tasten F0 bis [FunctionRange.last]; [active] sind eingeschaltet. F0 (Licht) traegt ein Lampensymbol. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FunctionKeys(range: FunctionRange, active: Set<Int>, compact: Boolean = false, onToggle: (Int) -> Unit) {
    val keyWidth = if (compact) 48.dp else 60.dp
    FlowRow(
        Modifier.fillMaxWidth().testTag(FUNCTION_KEYS_TAG),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (f in 0..range.last) {
            val on = f in active
            val content: @Composable () -> Unit = {
                if (f == 0) Icon(Icons.Filled.Lightbulb, "Licht", Modifier.size(16.dp)) else Text("F$f", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
            val modifier = Modifier.width(keyWidth).height(if (compact) 34.dp else 38.dp)
            val padding = PaddingValues(0.dp)
            if (on) {
                Button(onClick = { onToggle(f) }, modifier = modifier, contentPadding = padding, shape = MaterialTheme.shapes.small) { content() }
            } else {
                FilledTonalButton(onClick = { onToggle(f) }, modifier = modifier, contentPadding = padding, shape = MaterialTheme.shapes.small) { content() }
            }
        }
    }
}

/** Auswahl, wie viele Funktionstasten gezeigt werden. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FunctionRangeChooser(app: MobaApp, range: FunctionRange) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FunctionRange.entries.forEach { r ->
            FilterChip(selected = r == range, onClick = { FunctionKeysState.set(app, r) }, label = { Text(r.label, style = MaterialTheme.typography.labelSmall) })
        }
    }
}
