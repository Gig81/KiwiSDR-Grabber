package com.grabber.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.model.UiThemeMode

/**
 * Token cromatici di un tema. Le due istanze sono [DarkPalette] (tema storico) e [LightPalette].
 * La UI non usa questa classe direttamente ma [GrabberColors], che legge la palette del tema attivo.
 */
@Immutable
data class GrabberPalette(
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
    /** Primo piano (testo e icone) sui riempimenti accesi: `Primary`, `Secondary`, `Success`, `Warning`. */
    val onAccent: Color,
    /** Primo piano sui riempimenti saturi scuri (`Error`, `PrimaryVariant`), uguale nei due temi. */
    val onStrongAccent: Color,
    /** Sfondo della scheda selezionata nella barra principale. */
    val selectedTab: Color,
    /** Velo dei pannelli bloccati dalla licenza. */
    val lockedOverlay: Color,
    /** Colori per modo di demodulazione (badge dei preferiti e degli utenti). */
    val modeAm: Color,
    val modeUsb: Color,
    val modeLsb: Color,
    val modeCw: Color,
    val modeNfm: Color
)

val DarkPalette = GrabberPalette(
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
    onAccent = Color(0xFF000000),
    onStrongAccent = Color(0xFFFFFFFF),
    selectedTab = Color(0xFF2E3D52),
    lockedOverlay = Color(0xE6101824),
    modeAm = Color(0xFFFBBF24),
    modeUsb = Color(0xFF58A6FF),
    modeLsb = Color(0xFFBC8CFF),
    modeCw = Color(0xFFFF7B72),
    modeNfm = Color(0xFF34D399)
)

/**
 * Tema chiaro. Gli accenti sono più scuri di quelli del tema scuro per mantenere un contrasto
 * di almeno 4.5:1 sia come testo su fondo chiaro sia come riempimento con testo bianco.
 */
val LightPalette = GrabberPalette(
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
    onAccent = Color(0xFFFFFFFF),
    onStrongAccent = Color(0xFFFFFFFF),
    selectedTab = Color(0xFFD6E4F7),
    lockedOverlay = Color(0xE6F3F5F8),
    modeAm = Color(0xFF9A5B00),
    modeUsb = Color(0xFF0959C2),
    modeLsb = Color(0xFF7B3FC4),
    modeCw = Color(0xFFC0202B),
    modeNfm = Color(0xFF13773F)
)

/** Palette del tema attivo, fornita da [GrabberTheme]. */
val LocalGrabberPalette = staticCompositionLocalOf { DarkPalette }

/**
 * Colori del tema attivo. Ogni proprietà va letta in un contesto `@Composable`: nei blocchi
 * di disegno (`Canvas`, `drawBehind`) o nei `remember { … }` leggere il colore prima, in una `val`.
 */
object GrabberColors {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.surface
    val SurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.surfaceVariant
    val SurfaceElevated: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.surfaceElevated
    val Border: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.border
    val Primary: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.primary
    val PrimaryVariant: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.primaryVariant
    val Secondary: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.secondary
    val Success: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.success
    val Warning: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.warning
    val Error: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.error
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.textSecondary
    val TextDisabled: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.textDisabled
    val SignalGreen: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.signalGreen
    val SignalAmber: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.signalAmber
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.onAccent
    val OnStrongAccent: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.onStrongAccent
    val SelectedTab: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.selectedTab
    val LockedOverlay: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.lockedOverlay
    val ModeAm: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.modeAm
    val ModeUsb: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.modeUsb
    val ModeLsb: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.modeLsb
    val ModeCw: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.modeCw
    val ModeNfm: Color @Composable @ReadOnlyComposable get() = LocalGrabberPalette.current.modeNfm

    /** I display LCD restano scuri in entrambi i temi. */
    val LcdBackground: Color get() = DisplayColors.LcdBackground
    val LcdPrimary: Color get() = DisplayColors.LcdPrimary
}

/**
 * Colori dei "display strumentali" (LCD del VFO, spettro, waterfall, righello frequenze), che restano
 * scuri in entrambi i temi come un pannello radio. Sono costanti: si possono usare anche nei blocchi
 * di disegno. Gli accenti sono quelli del tema scuro, leggibili sul fondo scuro.
 */
object DisplayColors {
    val LcdBackground = Color(0xFF050B14)
    val LcdPrimary = Color(0xFF38D9A9)
    val SpectrumBackground = Color(0xFF070B10)
    val SpectrumGrid = Color(0xFF1E2630)
    val SpectrumScaleText = Color(0xFF4A5568)
    val WaterfallBackground = Color(0xFF05080E)
    val RulerBackground = Color(0xFF0D1117)
    val RulerText = Color(0xFFADBBC8)
    val RulerTick = Color(0xFF30363D)
    val Border = DarkPalette.border
    val Text = DarkPalette.textPrimary
    val TextMuted = Color(0xFF8B949E)
    val Primary = DarkPalette.primary
    val Success = DarkPalette.success
    val Warning = DarkPalette.warning
    val Error = DarkPalette.error
    val LabelBackdrop = Color(0xB3000000)
    val MeterTickHigh = Color(0xFF552222)
    val MeterTickLow = Color(0xFF223322)
}

/** Stili di testo con il colore della palette indicata. */
@Immutable
class GrabberTextStyles(palette: GrabberPalette) {
    val titleLarge = BaseTextStyles.titleLarge.copy(color = palette.textPrimary)
    val titleMedium = BaseTextStyles.titleMedium.copy(color = palette.textPrimary)
    val panelTitle = BaseTextStyles.panelTitle.copy(color = palette.textPrimary)
    val label = BaseTextStyles.label.copy(color = palette.textSecondary)
    val body = BaseTextStyles.body.copy(color = palette.textPrimary)
    val numeric = BaseTextStyles.numeric.copy(color = palette.textPrimary)
    val frequency = BaseTextStyles.frequency.copy(color = DisplayColors.LcdPrimary)
    val status = BaseTextStyles.status.copy(color = palette.textPrimary)
}

private val DarkTextStyles = GrabberTextStyles(DarkPalette)
private val LightTextStyles = GrabberTextStyles(LightPalette)

val LocalGrabberTextStyles = staticCompositionLocalOf { DarkTextStyles }

/** Stili di testo del tema attivo (colore incluso). */
object GrabberTypography {
    val titleLarge: TextStyle @Composable @ReadOnlyComposable get() = LocalGrabberTextStyles.current.titleLarge
    val titleMedium: TextStyle @Composable @ReadOnlyComposable get() = LocalGrabberTextStyles.current.titleMedium
    val panelTitle: TextStyle @Composable @ReadOnlyComposable get() = LocalGrabberTextStyles.current.panelTitle
    val label: TextStyle @Composable @ReadOnlyComposable get() = LocalGrabberTextStyles.current.label
    val body: TextStyle @Composable @ReadOnlyComposable get() = LocalGrabberTextStyles.current.body
    val numeric: TextStyle @Composable @ReadOnlyComposable get() = LocalGrabberTextStyles.current.numeric
    val frequency: TextStyle @Composable @ReadOnlyComposable get() = LocalGrabberTextStyles.current.frequency
    val status: TextStyle @Composable @ReadOnlyComposable get() = LocalGrabberTextStyles.current.status
}

/** Stili senza colore: la base comune dei due temi e della tipografia Material. */
private object BaseTextStyles {
    val titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp
    )
    val titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    )
    val panelTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    )
    val label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
    val numeric = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )
    val frequency = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 36.sp
    )
    val status = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
}

object GrabberSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
}

object GrabberShapes {
    val control = 6.dp
    val panel = 10.dp
    val chip = 999.dp
}

/** Palette effettiva per la modalità scelta; `SYSTEM` segue il tema di Windows. */
@Composable
fun paletteFor(mode: UiThemeMode): GrabberPalette = when (mode) {
    UiThemeMode.DARK -> DarkPalette
    UiThemeMode.LIGHT -> LightPalette
    UiThemeMode.SYSTEM -> if (isSystemInDarkTheme()) DarkPalette else LightPalette
}

private fun GrabberPalette.toColorScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        primary = primary,
        secondary = secondary,
        tertiary = success,
        error = error,
        onBackground = textPrimary,
        onSurface = textPrimary,
        onSurfaceVariant = textSecondary,
        outline = border
    )
} else {
    lightColorScheme(
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerHighest = surfaceElevated,
        primary = primary,
        onPrimary = onAccent,
        primaryContainer = selectedTab,
        onPrimaryContainer = textPrimary,
        secondary = secondary,
        onSecondary = onAccent,
        secondaryContainer = surfaceElevated,
        onSecondaryContainer = textPrimary,
        tertiary = success,
        onTertiary = onAccent,
        error = error,
        onError = onStrongAccent,
        onBackground = textPrimary,
        onSurface = textPrimary,
        onSurfaceVariant = textSecondary,
        inverseSurface = Color(0xFF2B313A),
        inverseOnSurface = Color(0xFFE6EDF3),
        outline = border,
        outlineVariant = border
    )
}

@Composable
fun GrabberTheme(mode: UiThemeMode = UiThemeMode.DARK, content: @Composable () -> Unit) {
    val palette = paletteFor(mode)
    val colorScheme = remember(palette) { palette.toColorScheme() }
    val textStyles = if (palette.isDark) DarkTextStyles else LightTextStyles
    // Gli stili Material non portano colore: così pulsanti, tooltip, menu e campi usano il proprio
    // colore del contenuto. I testi che usano `GrabberTypography` direttamente mantengono il loro colore.
    val typography = remember {
        Typography(
            displayLarge = BaseTextStyles.titleLarge,
            displayMedium = BaseTextStyles.titleMedium,
            headlineMedium = BaseTextStyles.titleMedium,
            titleLarge = BaseTextStyles.titleLarge,
            titleMedium = BaseTextStyles.titleMedium,
            titleSmall = BaseTextStyles.panelTitle,
            bodyLarge = BaseTextStyles.body,
            bodyMedium = BaseTextStyles.body,
            bodySmall = BaseTextStyles.label,
            labelLarge = BaseTextStyles.label,
            labelMedium = BaseTextStyles.label,
            labelSmall = BaseTextStyles.status
        )
    }
    val shapes = remember {
        Shapes(
            extraSmall = RoundedCornerShape(GrabberShapes.control),
            small = RoundedCornerShape(GrabberShapes.control),
            medium = RoundedCornerShape(GrabberShapes.panel),
            large = RoundedCornerShape(GrabberShapes.panel),
            extraLarge = RoundedCornerShape(GrabberShapes.panel)
        )
    }

    CompositionLocalProvider(
        LocalGrabberPalette provides palette,
        LocalGrabberTextStyles provides textStyles
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = shapes) {
            // Colore predefinito dei testi fuori da una `Surface` (altrimenti sarebbe nero).
            CompositionLocalProvider(LocalContentColor provides palette.textPrimary, content = content)
        }
    }
}
