package com.gig81.kiwisdrgrabber.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Token cromatici ripresi da `GrabberColors` del desktop (docs/GUI_DESIGN_SYSTEM.md). */
object KiwiColors {
    val Background = Color(0xFF0D1117)
    val Surface = Color(0xFF161B22)
    val SurfaceVariant = Color(0xFF21262D)
    val SurfaceElevated = Color(0xFF2B313A)
    val Border = Color(0xFF30363D)
    val Primary = Color(0xFF58A6FF)
    val PrimaryVariant = Color(0xFF3B82F6)
    val Secondary = Color(0xFF7DD3FC)
    val Success = Color(0xFF3FB950)
    val Warning = Color(0xFFD29922)
    val Error = Color(0xFFF85149)
    val TextPrimary = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFFB3BECA)
    val TextDisabled = Color(0xFF7A8796)
    val SignalGreen = Color(0xFF34D399)
    val SignalAmber = Color(0xFFFBBF24)
    val LcdBackground = Color(0xFF050B14)
    val LcdPrimary = Color(0xFF38D9A9)
}

private val colorScheme = darkColorScheme(
    primary = KiwiColors.Primary,
    onPrimary = KiwiColors.Background,
    primaryContainer = KiwiColors.PrimaryVariant,
    onPrimaryContainer = KiwiColors.TextPrimary,
    secondary = KiwiColors.Secondary,
    onSecondary = KiwiColors.Background,
    secondaryContainer = KiwiColors.SurfaceElevated,
    onSecondaryContainer = KiwiColors.TextPrimary,
    tertiary = KiwiColors.LcdPrimary,
    background = KiwiColors.Background,
    onBackground = KiwiColors.TextPrimary,
    surface = KiwiColors.Surface,
    onSurface = KiwiColors.TextPrimary,
    surfaceVariant = KiwiColors.SurfaceVariant,
    onSurfaceVariant = KiwiColors.TextSecondary,
    surfaceContainer = KiwiColors.Surface,
    surfaceContainerHigh = KiwiColors.SurfaceVariant,
    surfaceContainerHighest = KiwiColors.SurfaceElevated,
    outline = KiwiColors.Border,
    outlineVariant = KiwiColors.Border,
    error = KiwiColors.Error,
    onError = KiwiColors.Background
)

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
fun KiwiGrabberTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
}
