package com.grabber.waterfall

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Buffer circolare bidimensionale per lo storico della cascata Waterfall.
 * Mantiene sia i campioni binari grezzi (per ricolorazione istantanea al cambio palette/dBm)
 * sia il buffer di pixel ARGB pronto per il rendering.
 *
 * @param width Numero di bin orizzontali (default 1024).
 * @param height Numero di righe verticali di storico (default 250).
 */
class WaterfallBuffer(
    val width: Int = 1024,
    val height: Int = 250
) {
    private val lock = Any()

    val pixels = IntArray(width * height)
    private val rawLines: Array<ByteArray?> = arrayOfNulls(height)

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    private var currentPalette = WaterfallPaletteType.KIWI_CLASSIC
    private var currentMinDb = -110
    private var currentMaxDb = -10

    init {
        clear()
    }

    /**
     * Resetta il buffer con il colore di fondo scuro.
     */
    fun clear() {
        synchronized(lock) {
            val bgDark = 0xFF0D1117.toInt()
            pixels.fill(bgDark)
            for (i in 0 until height) {
                rawLines[i] = null
            }
            _version.value++
        }
    }

    /**
     * Aggiunge una nuova riga spettrale in cima al buffer e fa scorrere le righe precedenti verso il basso.
     */
    fun addLine(
        frame: WaterfallFrame,
        minDb: Int = currentMinDb,
        maxDb: Int = currentMaxDb,
        palette: WaterfallPaletteType = currentPalette
    ) {
        synchronized(lock) {
            currentMinDb = minDb
            currentMaxDb = maxDb
            currentPalette = palette

            // 1. Scorrimento righe raw storiche
            System.arraycopy(rawLines, 0, rawLines, 1, height - 1)
            rawLines[0] = frame.bins.clone()

            // 2. Scorrimento pixel ARGB verso il basso di 1 riga (width pixel)
            System.arraycopy(pixels, 0, pixels, width, (height - 1) * width)

            // 3. Rendering della nuova riga 0
            renderLine(0, frame.bins, minDb, maxDb, palette)

            _version.value++
        }
    }

    /**
     * Ricolora istantaneamente l'intero storico memorizzato usando la nuova palette o i nuovi valori min/max dBm.
     */
    fun recolor(
        minDb: Int = currentMinDb,
        maxDb: Int = currentMaxDb,
        palette: WaterfallPaletteType = currentPalette
    ) {
        synchronized(lock) {
            currentMinDb = minDb
            currentMaxDb = maxDb
            currentPalette = palette

            for (y in 0 until height) {
                val line = rawLines[y]
                if (line != null) {
                    renderLine(y, line, minDb, maxDb, palette)
                }
            }
            _version.value++
        }
    }

    private fun renderLine(
        y: Int,
        bins: ByteArray,
        minDb: Int,
        maxDb: Int,
        palette: WaterfallPaletteType
    ) {
        val lut = WaterfallPalette.getLut(palette)
        val range = (maxDb - minDb).coerceAtLeast(1)
        val rowOffset = y * width
        val count = minOf(width, bins.size)

        for (x in 0 until count) {
            val unsignedByte = bins[x].toInt() and 0xFF
            val dbm = unsignedByte - 255
            val lutIndex = (((dbm - minDb).toFloat() / range) * 255f).roundToInt().coerceIn(0, 255)
            pixels[rowOffset + x] = lut[lutIndex]
        }
    }

    /**
     * Crea una copia istantanea dei pixel attuali in modo thread-safe.
     */
    fun getPixelsCopy(): IntArray {
        synchronized(lock) {
            return pixels.clone()
        }
    }

    /**
     * Copia i pixel correnti in un array di destinazione preallocato in modo thread-safe,
     * evitando nuove allocazioni di memoria heap ad ogni frame.
     */
    fun copyPixelsInto(target: IntArray) {
        synchronized(lock) {
            val count = minOf(pixels.size, target.size)
            System.arraycopy(pixels, 0, target, 0, count)
        }
    }
}

