package de.moba.ui.shell

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
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

// ---------------------------------------------------------------- Modern

private val ModernDark = darkColorScheme(
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

private val ModernLight = lightColorScheme(
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

private val ModernDarkMoba = MobaColors(
    signalGreen = Color(0xFF3DDC97),
    occupied = Color(0xFFFFB547),
    alarm = Color(0xFFFF5A5F),
    locked = Color(0xFF8A96A0),
)

private val ModernLightMoba = MobaColors(
    signalGreen = Color(0xFF138A5A),
    occupied = Color(0xFFC77700),
    alarm = Color(0xFFD32F2F),
    locked = Color(0xFF6B7780),
)

private val ModernShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// ---------------------------------------------------------------- Windows 11 (Fluent)

private val FluentDark = darkColorScheme(
    primary = Color(0xFF60CDFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF004B6E),
    onPrimaryContainer = Color(0xFFD6F1FF),
    secondary = Color(0xFFC5C5C5),
    onSecondary = Color(0xFF1C1C1C),
    secondaryContainer = Color(0xFF3A3A3A),
    onSecondaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFF202020),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF202020),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFC5C5C5),
    surfaceContainerLowest = Color(0xFF1C1C1C),
    surfaceContainerLow = Color(0xFF1C1C1C),
    surfaceContainer = Color(0xFF2B2B2B),
    surfaceContainerHigh = Color(0xFF2D2D2D),
    surfaceContainerHighest = Color(0xFF383838),
    outline = Color(0xFF454545),
    outlineVariant = Color(0xFF333333),
    error = Color(0xFFFF99A4),
    onError = Color(0xFF3B0004),
)

private val FluentLight = lightColorScheme(
    primary = Color(0xFF005FB8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCE4F7),
    onPrimaryContainer = Color(0xFF001D35),
    secondary = Color(0xFF5D5D5D),
    secondaryContainer = Color(0xFFE5E5E5),
    onSecondaryContainer = Color(0xFF1A1A1A),
    background = Color(0xFFF3F3F3),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFF3F3F3),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFEBEBEB),
    onSurfaceVariant = Color(0xFF5D5D5D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEEEEE),
    surfaceContainer = Color(0xFFFBFBFB),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFF0F0F0),
    outline = Color(0xFFD1D1D1),
    outlineVariant = Color(0xFFE5E5E5),
    error = Color(0xFFC42B1C),
)

private val FluentDarkMoba = MobaColors(
    signalGreen = Color(0xFF6CCB5F),
    occupied = Color(0xFFFCE100),
    alarm = Color(0xFFFF99A4),
    locked = Color(0xFF9D9D9D),
)

private val FluentLightMoba = MobaColors(
    signalGreen = Color(0xFF0F7B0F),
    occupied = Color(0xFF9D5D00),
    alarm = Color(0xFFC42B1C),
    locked = Color(0xFF8A8A8A),
)

private val FluentShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

// ---------------------------------------------------------------- Stellwerk

private val StellwerkDark = darkColorScheme(
    primary = Color(0xFFF2C94C),
    onPrimary = Color(0xFF2A2000),
    primaryContainer = Color(0xFF4A3F12),
    onPrimaryContainer = Color(0xFFFFEDB0),
    secondary = Color(0xFFA9B8A2),
    onSecondary = Color(0xFF17201A),
    secondaryContainer = Color(0xFF2E3A30),
    onSecondaryContainer = Color(0xFFDDE8D8),
    background = Color(0xFF151A16),
    onBackground = Color(0xFFE4E8DF),
    surface = Color(0xFF151A16),
    onSurface = Color(0xFFE4E8DF),
    surfaceVariant = Color(0xFF26302A),
    onSurfaceVariant = Color(0xFFAAB5A6),
    surfaceContainerLowest = Color(0xFF101411),
    surfaceContainerLow = Color(0xFF1B221D),
    surfaceContainer = Color(0xFF202923),
    surfaceContainerHigh = Color(0xFF283229),
    surfaceContainerHighest = Color(0xFF323D33),
    outline = Color(0xFF4A5A4C),
    outlineVariant = Color(0xFF2F3B31),
    error = Color(0xFFFF6B5E),
    onError = Color(0xFF3B0600),
)

private val StellwerkLight = lightColorScheme(
    primary = Color(0xFF7A5C00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE08A),
    onPrimaryContainer = Color(0xFF261A00),
    secondary = Color(0xFF526350),
    secondaryContainer = Color(0xFFD5E3CF),
    onSecondaryContainer = Color(0xFF111F10),
    background = Color(0xFFEEEBE1),
    onBackground = Color(0xFF1C1C17),
    surface = Color(0xFFEEEBE1),
    onSurface = Color(0xFF1C1C17),
    surfaceVariant = Color(0xFFDDDBCD),
    onSurfaceVariant = Color(0xFF5A5B4F),
    surfaceContainerLowest = Color(0xFFFFFDF6),
    surfaceContainerLow = Color(0xFFF8F5EC),
    surfaceContainer = Color(0xFFE8E5D9),
    surfaceContainerHigh = Color(0xFFE2DFD2),
    surfaceContainerHighest = Color(0xFFDAD7C9),
    outline = Color(0xFFADAA98),
    outlineVariant = Color(0xFFD3D0C1),
    error = Color(0xFFBA1A1A),
)

private val StellwerkDarkMoba = MobaColors(
    signalGreen = Color(0xFF52D273),
    occupied = Color(0xFFFF8F3D),
    alarm = Color(0xFFFF6B5E),
    locked = Color(0xFF8C978A),
)

private val StellwerkLightMoba = MobaColors(
    signalGreen = Color(0xFF1E7B34),
    occupied = Color(0xFFB85300),
    alarm = Color(0xFFBA1A1A),
    locked = Color(0xFF747867),
)

private val StellwerkShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

val LocalMobaColors = staticCompositionLocalOf { ModernDarkMoba }

internal fun scheme(style: DesignStyle, dark: Boolean): ColorScheme = when (style) {
    DesignStyle.MODERN -> if (dark) ModernDark else ModernLight
    DesignStyle.WINDOWS11 -> if (dark) FluentDark else FluentLight
    DesignStyle.STELLWERK -> if (dark) StellwerkDark else StellwerkLight
}

internal fun mobaColors(style: DesignStyle, dark: Boolean): MobaColors = when (style) {
    DesignStyle.MODERN -> if (dark) ModernDarkMoba else ModernLightMoba
    DesignStyle.WINDOWS11 -> if (dark) FluentDarkMoba else FluentLightMoba
    DesignStyle.STELLWERK -> if (dark) StellwerkDarkMoba else StellwerkLightMoba
}

private fun shapes(style: DesignStyle): Shapes = when (style) {
    DesignStyle.MODERN -> ModernShapes
    DesignStyle.WINDOWS11 -> FluentShapes
    DesignStyle.STELLWERK -> StellwerkShapes
}

/** Material-Schriftstile mit einer anderen Schriftart. */
private fun typography(family: FontFamily): Typography {
    val t = Typography()
    if (family == FontFamily.Default) return t
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = family),
        displayMedium = t.displayMedium.copy(fontFamily = family),
        displaySmall = t.displaySmall.copy(fontFamily = family),
        headlineLarge = t.headlineLarge.copy(fontFamily = family),
        headlineMedium = t.headlineMedium.copy(fontFamily = family),
        headlineSmall = t.headlineSmall.copy(fontFamily = family),
        titleLarge = t.titleLarge.copy(fontFamily = family),
        titleMedium = t.titleMedium.copy(fontFamily = family),
        titleSmall = t.titleSmall.copy(fontFamily = family),
        bodyLarge = t.bodyLarge.copy(fontFamily = family),
        bodyMedium = t.bodyMedium.copy(fontFamily = family),
        bodySmall = t.bodySmall.copy(fontFamily = family),
        labelLarge = t.labelLarge.copy(fontFamily = family),
        labelMedium = t.labelMedium.copy(fontFamily = family),
        labelSmall = t.labelSmall.copy(fontFamily = family),
    )
}

/** Ersetzt die Hauptfarbe des Designs durch die gewaehlte Akzentfarbe; Flaechen und Signalfarben bleiben. */
internal fun ColorScheme.withAccent(accent: AccentColor, dark: Boolean): ColorScheme {
    val raw = (if (dark) accent.dark else accent.light) ?: return this
    val a = Color(raw)
    val onA = if (a.luminance() > 0.4f) Color(0xFF101418) else Color.White
    val container = if (dark) lerp(a, background, 0.72f) else lerp(a, Color.White, 0.8f)
    val onContainer = if (dark) lerp(a, Color.White, 0.75f) else lerp(a, Color.Black, 0.6f)
    return copy(
        primary = a,
        onPrimary = onA,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        inversePrimary = a,
        surfaceTint = a,
    )
}

/** Erscheinungsbild der App: standardmaessig dunkel, wie man es von Stellpult-Software kennt. */
@Composable
fun MobaTheme(dark: Boolean = true, appearance: Appearance = Appearance(), content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalMobaColors provides mobaColors(appearance.style, dark),
        LocalDensity provides Density(density.density, density.fontScale * appearance.textSize.scale),
    ) {
        MaterialTheme(
            colorScheme = scheme(appearance.style, dark).withAccent(appearance.accent, dark),
            shapes = shapes(appearance.style),
            typography = typography(appearance.font.family),
            content = content,
        )
    }
}

object Moba {
    val colors: MobaColors
        @Composable get() = LocalMobaColors.current
}
