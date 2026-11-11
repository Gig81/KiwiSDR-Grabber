package com.grabber.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object GrabberColors {
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

object GrabberTypography {
    val titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        color = GrabberColors.TextPrimary
    )
    val titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = GrabberColors.TextPrimary
    )
    val panelTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = GrabberColors.TextPrimary
    )
    val label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = GrabberColors.TextSecondary
    )
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = GrabberColors.TextPrimary
    )
    val numeric = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = GrabberColors.TextPrimary
    )
    val frequency = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        color = GrabberColors.LcdPrimary
    )
    val status = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = GrabberColors.TextPrimary
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

@Composable
fun GrabberTheme(content: @Composable () -> Unit) {
    val colorScheme = remember {
        darkColorScheme(
            background = GrabberColors.Background,
            surface = GrabberColors.Surface,
            surfaceVariant = GrabberColors.SurfaceVariant,
            primary = GrabberColors.Primary,
            secondary = GrabberColors.Secondary,
            tertiary = GrabberColors.Success,
            error = GrabberColors.Error,
            onBackground = GrabberColors.TextPrimary,
            onSurface = GrabberColors.TextPrimary,
            onSurfaceVariant = GrabberColors.TextSecondary,
            outline = GrabberColors.Border
        )
    }
    // Gli stili Material non portano colore: così pulsanti, tooltip, menu e campi usano il proprio
    // colore del contenuto. I testi che usano `GrabberTypography` direttamente mantengono il loro colore.
    val typography = remember {
        Typography(
            displayLarge = GrabberTypography.titleLarge.withoutColor(),
            displayMedium = GrabberTypography.titleMedium.withoutColor(),
            headlineMedium = GrabberTypography.titleMedium.withoutColor(),
            titleLarge = GrabberTypography.titleLarge.withoutColor(),
            titleMedium = GrabberTypography.titleMedium.withoutColor(),
            titleSmall = GrabberTypography.panelTitle.withoutColor(),
            bodyLarge = GrabberTypography.body.withoutColor(),
            bodyMedium = GrabberTypography.body.withoutColor(),
            bodySmall = GrabberTypography.label.withoutColor(),
            labelLarge = GrabberTypography.label.withoutColor(),
            labelMedium = GrabberTypography.label.withoutColor(),
            labelSmall = GrabberTypography.status.withoutColor()
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

    MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = shapes) {
        // Colore predefinito dei testi fuori da una `Surface` (altrimenti sarebbe nero).
        CompositionLocalProvider(LocalContentColor provides GrabberColors.TextPrimary, content = content)
    }
}

private fun TextStyle.withoutColor(): TextStyle = copy(color = Color.Unspecified)
