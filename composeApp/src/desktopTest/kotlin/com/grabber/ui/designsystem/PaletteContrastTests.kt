package com.grabber.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

/** Contrasto WCAG 2.x dei token di testo e degli accenti nelle due palette. */
class PaletteContrastTests {

    private val palettes = mapOf("scuro" to DarkPalette, "chiaro" to LightPalette)

    private fun contrast(foreground: Color, background: Color): Double {
        val fg = foreground.compositeOver(background).luminance().toDouble()
        val bg = background.luminance().toDouble()
        val (light, dark) = if (fg > bg) fg to bg else bg to fg
        return (light + 0.05) / (dark + 0.05)
    }

    private fun assertContrast(label: String, foreground: Color, background: Color, minimum: Double) {
        val ratio = contrast(foreground, background)
        assertTrue(ratio >= minimum, "$label: contrasto ${"%.2f".format(ratio)} < $minimum")
    }

    @Test
    fun bodyTextIsReadableOnAllSurfaces() {
        palettes.forEach { (name, p) ->
            listOf(p.background, p.surface, p.surfaceVariant).forEach { surface ->
                assertContrast("$name textPrimary", p.textPrimary, surface, 4.5)
                assertContrast("$name textSecondary", p.textSecondary, surface, 4.5)
            }
        }
    }

    @Test
    fun accentFillsHaveReadableForeground() {
        palettes.forEach { (name, p) ->
            listOf(p.primary, p.secondary, p.success, p.warning).forEach { fill ->
                assertContrast("$name onAccent", p.onAccent, fill, 4.5)
            }
            listOf(p.error, p.primaryVariant).forEach { fill ->
                assertContrast("$name onStrongAccent", p.onStrongAccent, fill, 3.0)
            }
        }
    }

    @Test
    fun lightThemeAccentsAreReadableAsText() {
        val p = LightPalette
        listOf(p.primary, p.secondary, p.success, p.warning, p.error, p.signalGreen, p.signalAmber,
            p.modeAm, p.modeUsb, p.modeLsb, p.modeCw, p.modeNfm).forEach { accent ->
            assertContrast("chiaro accento $accent", accent, p.surface, 4.5)
        }
    }

    @Test
    fun displayColorsStayDarkInBothThemes() {
        assertContrast("LCD", DisplayColors.LcdPrimary, DisplayColors.LcdBackground, 7.0)
        assertContrast("righello", DisplayColors.RulerText, DisplayColors.RulerBackground, 4.5)
        assertTrue(DisplayColors.SpectrumBackground.luminance() < 0.05f)
        assertTrue(DisplayColors.WaterfallBackground.luminance() < 0.05f)
    }
}
