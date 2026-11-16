package com.grabber.waterfall

import kotlin.math.roundToInt

enum class WaterfallPaletteType(val displayName: String) {
    KIWI_CLASSIC("KiwiSDR Classico"),
    TURBO("Turbo Rainbow"),
    VIRIDIS("Viridis"),
    FIAMMA("Fiamma / Fire"),
    MONOCHROME("Monocromatico"),
    AMBER("Fosfori Ambra")
}

/**
 * Gestione palette colori per la cascata Waterfall.
 * Utilizza lookup-table precalcolate a 256 colori (formato ARGB 0xAARRGGBB)
 * per garantire una mappatura istantanea ad altissime prestazioni (O(1)).
 */
object WaterfallPalette {

    private val classicLut = IntArray(256)
    private val turboLut = IntArray(256)
    private val viridisLut = IntArray(256)
    private val fiammaLut = IntArray(256)
    private val monochromeLut = IntArray(256)
    private val amberLut = IntArray(256)

    init {
        buildLut(
            classicLut,
            listOf(
                0.00f to ColorRgb(0x05, 0x08, 0x20),
                0.18f to ColorRgb(0x00, 0x28, 0x85),
                0.36f to ColorRgb(0x00, 0x8F, 0xBF),
                0.55f to ColorRgb(0x15, 0xB0, 0x35),
                0.74f to ColorRgb(0xE0, 0xC5, 0x15),
                0.90f to ColorRgb(0xEA, 0x28, 0x15),
                1.00f to ColorRgb(0xFF, 0xFF, 0xFF)
            )
        )

        buildLut(
            turboLut,
            listOf(
                0.00f to ColorRgb(0x30, 0x12, 0x3B),
                0.20f to ColorRgb(0x46, 0x82, 0xF6),
                0.40f to ColorRgb(0x1A, 0xEA, 0xBB),
                0.60f to ColorRgb(0xA2, 0xFC, 0x3C),
                0.80f to ColorRgb(0xF7, 0x8D, 0x29),
                1.00f to ColorRgb(0x9E, 0x10, 0x05)
            )
        )

        buildLut(
            viridisLut,
            listOf(
                0.00f to ColorRgb(0x44, 0x01, 0x54),
                0.25f to ColorRgb(0x3B, 0x52, 0x8B),
                0.50f to ColorRgb(0x21, 0x90, 0x8C),
                0.75f to ColorRgb(0x5D, 0xC9, 0x63),
                1.00f to ColorRgb(0xFA, 0xE7, 0x25)
            )
        )

        buildLut(
            fiammaLut,
            listOf(
                0.00f to ColorRgb(0x00, 0x00, 0x00),
                0.22f to ColorRgb(0x5A, 0x05, 0x38),
                0.45f to ColorRgb(0xAD, 0x18, 0x00),
                0.70f to ColorRgb(0xF0, 0x7E, 0x00),
                0.90f to ColorRgb(0xFF, 0xDC, 0x20),
                1.00f to ColorRgb(0xFF, 0xFF, 0xFF)
            )
        )

        buildLut(
            monochromeLut,
            listOf(
                0.00f to ColorRgb(0x05, 0x05, 0x05),
                1.00f to ColorRgb(0xFF, 0xFF, 0xFF)
            )
        )

        buildLut(
            amberLut,
            listOf(
                0.00f to ColorRgb(0x08, 0x04, 0x00),
                0.40f to ColorRgb(0x80, 0x40, 0x00),
                0.75f to ColorRgb(0xFF, 0x96, 0x00),
                0.95f to ColorRgb(0xFF, 0xD0, 0x60),
                1.00f to ColorRgb(0xFF, 0xF5, 0xDD)
            )
        )
    }

    private data class ColorRgb(val r: Int, val g: Int, val b: Int)

    private fun buildLut(lut: IntArray, points: List<Pair<Float, ColorRgb>>) {
        for (i in 0..255) {
            val fraction = i / 255.0f
            var lower = points.first()
            var upper = points.last()

            for (k in 0 until points.size - 1) {
                if (fraction >= points[k].first && fraction <= points[k + 1].first) {
                    lower = points[k]
                    upper = points[k + 1]
                    break
                }
            }

            val range = upper.first - lower.first
            val localFraction = if (range > 0f) (fraction - lower.first) / range else 0f

            val r = (lower.second.r + (upper.second.r - lower.second.r) * localFraction).roundToInt().coerceIn(0, 255)
            val g = (lower.second.g + (upper.second.g - lower.second.g) * localFraction).roundToInt().coerceIn(0, 255)
            val b = (lower.second.b + (upper.second.b - lower.second.b) * localFraction).roundToInt().coerceIn(0, 255)

            // ARGB con alpha = 0xFF
            lut[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    /**
     * Mappa un valore dBm (es. -120 .. -30) in un colore ARGB Int.
     *
     * @param dbm Valore misurato in dBm.
     * @param minDb Soglia minima di visualizzazione (Floor, default -110 dBm).
     * @param maxDb Soglia massima di visualizzazione (Ceiling, default -20 dBm).
     * @param palette Tipo di palette selezionato.
     */
    fun getColorForDbm(
        dbm: Int,
        minDb: Int = -110,
        maxDb: Int = -20,
        palette: WaterfallPaletteType = WaterfallPaletteType.KIWI_CLASSIC
    ): Int {
        val range = (maxDb - minDb).coerceAtLeast(1)
        val index = (((dbm - minDb).toFloat() / range) * 255f).roundToInt().coerceIn(0, 255)
        return getLut(palette)[index]
    }

    /**
     * Restituisce la LUT a 256 interi ARGB per la palette specificata.
     */
    fun getLut(palette: WaterfallPaletteType): IntArray {
        return when (palette) {
            WaterfallPaletteType.KIWI_CLASSIC -> classicLut
            WaterfallPaletteType.TURBO -> turboLut
            WaterfallPaletteType.VIRIDIS -> viridisLut
            WaterfallPaletteType.FIAMMA -> fiammaLut
            WaterfallPaletteType.MONOCHROME -> monochromeLut
            WaterfallPaletteType.AMBER -> amberLut
        }
    }
}

