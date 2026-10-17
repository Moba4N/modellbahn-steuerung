package de.moba.ui.shell

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

internal actual fun bundledFontFamily(regular: String, bold: String): FontFamily = FontFamily(
    Font(fontId(regular), FontWeight.Normal),
    Font(fontId(bold), FontWeight.Bold),
)

/** Ressourcen-Id einer Schrift aus fonts/font. */
private fun fontId(name: String): Int = when (name) {
    "outfit_regular" -> R.font.outfit_regular
    "outfit_bold" -> R.font.outfit_bold
    "worksans_regular" -> R.font.worksans_regular
    "worksans_bold" -> R.font.worksans_bold
    "instrumentsans_regular" -> R.font.instrumentsans_regular
    "instrumentsans_bold" -> R.font.instrumentsans_bold
    "bigshoulders_regular" -> R.font.bigshoulders_regular
    "bigshoulders_bold" -> R.font.bigshoulders_bold
    "tektur_regular" -> R.font.tektur_regular
    "tektur_medium" -> R.font.tektur_medium
    "lora_regular" -> R.font.lora_regular
    "lora_bold" -> R.font.lora_bold
    "ibmplexserif_regular" -> R.font.ibmplexserif_regular
    "ibmplexserif_bold" -> R.font.ibmplexserif_bold
    "jetbrainsmono_regular" -> R.font.jetbrainsmono_regular
    "jetbrainsmono_bold" -> R.font.jetbrainsmono_bold
    else -> error("Unbekannte Schrift: $name")
}
