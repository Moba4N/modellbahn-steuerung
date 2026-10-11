package de.moba.ui.shell

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Farben mit Bedeutung im Bahnbetrieb, zusaetzlich zum Material-Farbschema. */
@Immutable
data class MobaColors(
    /** Gleis frei, verbunden, aktiv. */
    val signalGreen: Color,
    /** Abschnitt belegt, Hinweis. */
    val occupied: Color,
    /** Nothalt, Kurzschluss, Fehler. */
    val alarm: Color,
    /** Gesperrt, kein Abo. */
    val locked: Color,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4CC2FF),
    onPrimary = Color(0xFF00283D),
    primaryContainer = Color(0xFF0E3A52),
    onPrimaryContainer = Color(0xFFCBEBFF),
    secondary = Color(0xFF9AB4C8),
    onSecondary = Color(0xFF0F1D27),
    secondaryContainer = Color(0xFF26343F),
    onSecondaryContainer = Color(0xFFD6E5F1),
    background = Color(0xFF0E1317),
    onBackground = Color(0xFFE2E8ED),
    surface = Color(0xFF0E1317),
    onSurface = Color(0xFFE2E8ED),
    surfaceVariant = Color(0xFF1E272E),
    onSurfaceVariant = Color(0xFF9FB0BD),
    surfaceContainerLowest = Color(0xFF0A0E11),
    surfaceContainerLow = Color(0xFF141A1F),
    surfaceContainer = Color(0xFF182026),
    surfaceContainerHigh = Color(0xFF1F282F),
    surfaceContainerHighest = Color(0xFF273139),
    outline = Color(0xFF3A4852),
    outlineVariant = Color(0xFF26323A),
    error = Color(0xFFFF5A5F),
    onError = Color(0xFF3B0004),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00658F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6FF),
    onPrimaryContainer = Color(0xFF001E2E),
    secondary = Color(0xFF4F6070),
    secondaryContainer = Color(0xFFD3E4F7),
    background = Color(0xFFF5F7F9),
    surface = Color(0xFFF5F7F9),
    surfaceVariant = Color(0xFFDDE3E9),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEEF2F5),
    surfaceContainerHigh = Color(0xFFE6EBEF),
    surfaceContainerHighest = Color(0xFFDDE3E9),
    outline = Color(0xFFB4BEC6),
    outlineVariant = Color(0xFFD6DDE3),
    error = Color(0xFFD32F2F),
)

private val DarkMoba = MobaColors(
    signalGreen = Color(0xFF3DDC97),
    occupied = Color(0xFFFFB547),
    alarm = Color(0xFFFF5A5F),
    locked = Color(0xFF8A96A0),
)

private val LightMoba = MobaColors(
    signalGreen = Color(0xFF138A5A),
    occupied = Color(0xFFC77700),
    alarm = Color(0xFFD32F2F),
    locked = Color(0xFF6B7780),
)

val LocalMobaColors = staticCompositionLocalOf { DarkMoba }

private val MobaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Erscheinungsbild der App: standardmaessig dunkel, wie man es von Stellpult-Software kennt. */
@Composable
fun MobaTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    androidx.compose.runtime.CompositionLocalProvider(LocalMobaColors provides if (dark) DarkMoba else LightMoba) {
        MaterialTheme(colorScheme = scheme, shapes = MobaShapes, content = content)
    }
}

object Moba {
    val colors: MobaColors
        @Composable get() = LocalMobaColors.current
}
