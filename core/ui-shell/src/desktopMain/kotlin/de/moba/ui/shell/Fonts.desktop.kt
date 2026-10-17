package de.moba.ui.shell

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

internal actual fun bundledFontFamily(regular: String, bold: String): FontFamily = FontFamily(
    Font("font/$regular.ttf", FontWeight.Normal),
    Font("font/$bold.ttf", FontWeight.Bold),
)
