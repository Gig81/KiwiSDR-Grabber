package com.gig81.kiwisdrgrabber.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.grabber.model.UiThemeMode

/**
 * Token cromatici di un tema: stessi valori di `GrabberPalette` del desktop
 * (docs/GUI_DESIGN_SYSTEM.md), in versione scura ([DarkKiwiPalette]) e chiara ([LightKiwiPalette]).
 */
@Immutable
data class KiwiPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceElevated: Color,
    val border: Color,
    val primary: Color,
    val primaryVariant: Color,
    val secondary: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val signalGreen: Color,
    val signalAmber: Color,
    /** Primo piano sui riempimenti accesi (`Primary`, `Secondary`, `Success`, `Warning`). */
    val onAccent: Color
)

val DarkKiwiPalette = KiwiPalette(
    isDark = true,
    background = Color(0xFF0D1117),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF21262D),
    surfaceElevated = Color(0xFF2B313A),
    border = Color(0xFF30363D),
    primary = Color(0xFF58A6FF),
    primaryVariant = Color(0xFF3B82F6),
    secondary = Color(0xFF7DD3FC),
    success = Color(0xFF3FB950),
    warning = Color(0xFFD29922),
    error = Color(0xFFF85149),
    textPrimary = Color(0xFFE6EDF3),
    textSecondary = Color(0xFFB3BECA),
    textDisabled = Color(0xFF7A8796),
    signalGreen = Color(0xFF34D399),
    signalAmber = Color(0xFFFBBF24),
    onAccent = Color(0xFF0D1117)
)

val LightKiwiPalette = KiwiPalette(
    isDark = false,
    background = Color(0xFFF3F5F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEAEEF2),
    surfaceElevated = Color(0xFFDDE3EA),
    border = Color(0xFFC4CCD5),
    primary = Color(0xFF0959C2),
    primaryVariant = Color(0xFF1D4ED8),
    secondary = Color(0xFF0E6A85),
    success = Color(0xFF1A7535),
    warning = Color(0xFF8A5A00),
    error = Color(0xFFC0202B),
    textPrimary = Color(0xFF1F2328),
    textSecondary = Color(0xFF4F5A66),
    textDisabled = Color(0xFF7D8793),
    signalGreen = Color(0xFF13773F),
    signalAmber = Color(0xFF9A5B00),
    onAccent = Color(0xFFFFFFFF)
)

val LocalKiwiPalette = staticCompositionLocalOf { DarkKiwiPalette }

/**
 * Colori del tema attivo. Le proprietà vanno lette in un contesto `@Composable`: nei blocchi di
 * disegno (`Canvas`, `drawBehind`) leggere il colore prima, in una `val`, oppure usare [DisplayKiwiColors].
 */
object KiwiColors {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalKiwiPalette.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalKiwiPalette.current.surface
    val SurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalKiwiPalette.current.surfaceVariant
    val SurfaceElevated: Color @Composable @ReadOnlyComposable get() = LocalKiwiPalette.current.surfaceElevated
    val Border: Color @Composable @ReadOnlyComposable get() = LocalKiwiPalette.current.border
    val Primary: Color @Composable @ReadOnlyComposable get() = LocalKiwiPalette.current.primary
    val PrimaryVariant: Color @Composable @ReadOnlyComposable get() = LocalKiwiPalette.current.primaryVariant
    val Secondary: Color @Composable @ReadOnlyComposable get() = LocalKiwiPalette.current.secondary
    val Success: Color @Composable @ReadOnlyComposable get() = LocalKiwiPalette.current.success
    val Warning: Color @Composable @ReadOnlyComposable get() = LocalKiwiPalette.current.warning
    val Error: Color @Composable @ReadOnlyComposable get() = LocalKiwiPalette.current.error
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalKiwiPalette.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalKiwiPalette.current.textSecondary
    val TextDisabled: Color @Composable @ReadOnlyComposable get() = LocalKiwiPalette.current.textDisabled
    val SignalGreen: Color @Composable @ReadOnlyComposable get() = LocalKiwiPalette.current.signalGreen
    val SignalAmber: Color @Composable @ReadOnlyComposable get() = LocalKiwiPalette.current.signalAmber
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalKiwiPalette.current.onAccent

    /** I display (frequenza, waterfall) restano scuri in entrambi i temi. */
    val LcdBackground: Color get() = DisplayKiwiColors.LcdBackground
    val LcdPrimary: Color get() = DisplayKiwiColors.LcdPrimary
}

/**
 * Colori dei "display strumentali" (frequenza e S-meter della schermata Radio, spettro e waterfall),
 * scuri in entrambi i temi come sul desktop (`DisplayColors`). Sono costanti: utilizzabili nel disegno.
 */
object DisplayKiwiColors {
    val LcdBackground = Color(0xFF050B14)
    val LcdPrimary = Color(0xFF38D9A9)
    val SpectrumBackground = Color(0xFF0A1220)
    val Track = Color(0xFF21262D)
    val Border = DarkKiwiPalette.border
    val Text = DarkKiwiPalette.textPrimary
    val TextMuted = DarkKiwiPalette.textSecondary
    val Secondary = DarkKiwiPalette.secondary
    val Error = DarkKiwiPalette.error
    val Warning = DarkKiwiPalette.warning
    val SignalGreen = DarkKiwiPalette.signalGreen
    val SignalAmber = DarkKiwiPalette.signalAmber
    val Primary = DarkKiwiPalette.primary
}

/**
 * Colori del logo ufficiale (`docs/images/logo.jfif`), usati dallo splash screen in entrambi i temi:
 * lo sfondo coincide con quello dell'immagine del logo e con `@color/ic_launcher_background`.
 */
object BrandKiwiColors {
    val LogoBackground = Color(0xFF09182B)
    val LogoGreen = Color(0xFF33C15D)
    val OnLogoBackground = Color(0xFFF0F6FC)
    val OnLogoBackgroundMuted = Color(0xFF9DB0C6)
}

private fun KiwiPalette.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onAccent,
        primaryContainer = primaryVariant,
        onPrimaryContainer = if (isDark) textPrimary else Color.White,
        secondary = secondary,
        onSecondary = onAccent,
        secondaryContainer = surfaceElevated,
        onSecondaryContainer = textPrimary,
        tertiary = if (isDark) DisplayKiwiColors.LcdPrimary else success,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = textSecondary,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerHighest = surfaceElevated,
        outline = border,
        outlineVariant = border,
        error = error,
        onError = if (isDark) background else Color.White
    )
}

/** Stile monospaziato per frequenze e valori numerici (display "LCD"). */
val NumericStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

private val typography = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontSize = 11.sp)
    )
}

@Composable
fun KiwiGrabberTheme(mode: UiThemeMode = UiThemeMode.DARK, content: @Composable () -> Unit) {
    val palette = when (mode) {
        UiThemeMode.DARK -> DarkKiwiPalette
        UiThemeMode.LIGHT -> LightKiwiPalette
        UiThemeMode.SYSTEM -> if (isSystemInDarkTheme()) DarkKiwiPalette else LightKiwiPalette
    }
    val colorScheme = remember(palette) { palette.toColorScheme() }

    // Icone chiare sulle barre di sistema con il tema scuro, scure con quello chiaro.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !palette.isDark
                isAppearanceLightNavigationBars = !palette.isDark
            }
        }
    }

    CompositionLocalProvider(LocalKiwiPalette provides palette) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
}
