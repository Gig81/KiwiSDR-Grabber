package com.grabber.ui.feature.spectrum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.log10
import kotlin.math.pow
import com.grabber.ui.designsystem.GrabberColors

@Composable
internal fun WaterfallScaleSummary(
    startFreqKhz: Double,
    centerFreqKhz: Double,
    stopFreqKhz: Double,
    binCount: Int = 1024,
    currentZoom: Int,
    vfoKhz: Double
) {
    val startStr = remember(startFreqKhz) {
        String.format(java.util.Locale.US, "%.3f kHz", startFreqKhz)
    }
    val centerStr = remember(centerFreqKhz) {
        String.format(java.util.Locale.US, "CF: %.3f kHz", centerFreqKhz)
    }
    val stopStr = remember(stopFreqKhz) {
        String.format(java.util.Locale.US, "%.3f kHz", stopFreqKhz)
    }
    val rbwStr = remember(startFreqKhz, stopFreqKhz, binCount) {
        val rbw = (stopFreqKhz - startFreqKhz) / binCount.coerceAtLeast(1)
        if (rbw >= 1.0) String.format(java.util.Locale.US, "RBW: %.1f kHz", rbw)
        else String.format(java.util.Locale.US, "RBW: %.1f Hz", rbw * 1000.0)
    }
    val vfoStr = remember(vfoKhz) {
        String.format(java.util.Locale.US, "%.3f kHz", vfoKhz)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "◀ Inizio: $startStr",
            fontSize = 9.sp,
            color = Color.Gray,
            fontFamily = FontFamily.Monospace
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = centerStr,
                fontSize = 9.sp,
                color = GrabberColors.Primary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "•",
                fontSize = 9.sp,
                color = GrabberColors.Border
            )
            Text(
                text = "VFO: $vfoStr",
                fontSize = 9.sp,
                color = GrabberColors.Error,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "•",
                fontSize = 9.sp,
                color = GrabberColors.Border
            )
            Text(
                text = rbwStr,
                fontSize = 9.sp,
                color = GrabberColors.Success,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "Fine: $stopStr ▶",
            fontSize = 9.sp,
            color = Color.Gray,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ──────────────────────────────────────────────────────────────
// Righello frequenze con etichette adattive allo zoom
// ──────────────────────────────────────────────────────────────

/**
 * Dati calcolati per le tacche del righello frequenze.
 * @property majorTicks Lista frequenze in kHz dove disegnare le tacche principali con etichetta.
 * @property minorTicks Lista frequenze in kHz per le tacche secondarie senza etichetta.
 * @property majorStep Passo tra le tacche principali in kHz (usato per formattare le etichette).
 */
internal data class FrequencyTickInfo(
    val majorTicks: List<Double>,
    val minorTicks: List<Double>,
    val majorStep: Double
)

/**
 * Bande di riferimento radio HF con nome e frequenza centrale.
 * Usate per le etichette intelligenti a zoom basso/medio.
 */
private data class BandReference(
    val name: String,
    val freqKhz: Double
)

/** Principali bande di riferimento HF / VHF utilizzabili nel righello */
private val REFERENCE_BANDS = listOf(
    BandReference("LW", 200.0),
    BandReference("MW", 1000.0),
    BandReference("160m", 1900.0),
    BandReference("120m", 2400.0),
    BandReference("90m", 3300.0),
    BandReference("80m", 3700.0),
    BandReference("60m", 5350.0),
    BandReference("49m", 5950.0),
    BandReference("40m", 7100.0),
    BandReference("31m", 9500.0),
    BandReference("30m", 10125.0),
    BandReference("25m", 11700.0),
    BandReference("22m", 13600.0),
    BandReference("20m", 14200.0),
    BandReference("19m", 15200.0),
    BandReference("17m", 18100.0),
    BandReference("16m", 17600.0),
    BandReference("15m", 21200.0),
    BandReference("13m", 21450.0),
    BandReference("12m", 24900.0),
    BandReference("CB", 27200.0),
    BandReference("10m", 28500.0)
)

/**
 * Calcola le tacche del righello in base allo span visibile.
 * Sceglie automaticamente un passo "bello" (1, 2, 5, 10, 20, 50, ...) in modo da avere
 * circa 5-12 tacche principali visibili, mai troppe né troppo poche.
 */
internal fun computeFrequencyTicks(startKhz: Double, stopKhz: Double, spanKhz: Double): FrequencyTickInfo {
    if (spanKhz <= 0) return FrequencyTickInfo(emptyList(), emptyList(), 1.0)

    // Trova un passo "carino": multipli di 1, 2, 5 × potenza di 10
    // Obiettivo: circa 6-10 tacche principali visibili
    val rawStep = spanKhz / 8.0
    val magnitude = 10.0.pow(kotlin.math.floor(log10(rawStep)))
    val normalized = rawStep / magnitude

    val majorStep = when {
        normalized <= 1.0 -> magnitude
        normalized <= 2.0 -> 2.0 * magnitude
        normalized <= 5.0 -> 5.0 * magnitude
        else -> 10.0 * magnitude
    }

    val minorStep = majorStep / 5.0

    // Genera tacche principali
    val firstMajor = (kotlin.math.ceil(startKhz / majorStep) * majorStep)
    val majorTicks = mutableListOf<Double>()
    var freq = firstMajor
    while (freq <= stopKhz) {
        majorTicks.add(freq)
        freq += majorStep
    }

    // Genera tacche secondarie (escluse quelle coincidenti con le principali)
    val firstMinor = (kotlin.math.ceil(startKhz / minorStep) * minorStep)
    val minorTicks = mutableListOf<Double>()
    freq = firstMinor
    while (freq <= stopKhz) {
        // Evita duplicazione con le tacche principali (tolleranza 1% del passo)
        val isOnMajor = majorTicks.any { kotlin.math.abs(it - freq) < majorStep * 0.01 }
        if (!isOnMajor) {
            minorTicks.add(freq)
        }
        freq += minorStep
    }

    return FrequencyTickInfo(majorTicks, minorTicks, majorStep)
}

/**
 * Formatta la frequenza per l'etichetta del righello.
 * Adatta la precisione in base al passo tra le tacche.
 */
internal fun formatFrequencyLabel(freqKhz: Double, stepKhz: Double): String {
    return when {
        // Passo ≥ 1 MHz: mostra "X MHz"
        stepKhz >= 1000.0 -> {
            val mhz = freqKhz / 1000.0
            if (mhz == mhz.toLong().toDouble()) {
                "${mhz.toLong()} MHz"
            } else {
                String.format(java.util.Locale.US, "%.1f MHz", mhz)
            }
        }
        // Passo ≥ 100 kHz: mostra "X.X MHz" o "XXX0 kHz"
        stepKhz >= 100.0 -> {
            val mhz = freqKhz / 1000.0
            String.format(java.util.Locale.US, "%.1f", mhz)
        }
        // Passo ≥ 10 kHz: mostra "XXXX0"
        stepKhz >= 10.0 -> {
            String.format(java.util.Locale.US, "%.0f", freqKhz)
        }
        // Passo ≥ 1 kHz: mostra "XXXXX"
        stepKhz >= 1.0 -> {
            String.format(java.util.Locale.US, "%.0f", freqKhz)
        }
        // Passo frazionario: mostra con decimali
        else -> {
            String.format(java.util.Locale.US, "%.1f", freqKhz)
        }
    }
}

/**
 * Composable righello frequenze posizionato tra lo spettro FFT e la cascata waterfall.
 * Disegna tacche principali (con etichetta) e secondarie (senza), più etichette
 * delle bande di riferimento quando lo zoom è sufficientemente ampio.
 * Supporta click-to-tune come lo spettro e il waterfall.
 * Ottimizzato: riceve start/stop/span stabili ed esegue il layout del testo solo al cambio di zoom/frequenza.
 */
@Composable
internal fun FrequencyRulerView(
    startFreqKhz: Double,
    stopFreqKhz: Double,
    spanKhz: Double,
    vfoKhz: Double,
    currentZoom: Int,
    onTune: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val currentOnTune by rememberUpdatedState(onTune)

    val tickInfo = remember(startFreqKhz, stopFreqKhz, spanKhz) {
        if (spanKhz > 0) computeFrequencyTicks(startFreqKhz, stopFreqKhz, spanKhz) else null
    }

    val tickLabelStyle = remember {
        TextStyle(
            fontSize = 8.sp,
            color = Color(0xFFADBBC8),
            fontFamily = FontFamily.Monospace
        )
    }
    val tickTextLayouts = remember(textMeasurer, tickInfo, tickLabelStyle) {
        val info = tickInfo
        if (info != null) {
            info.majorTicks.associateWith { tick ->
                textMeasurer.measure(formatFrequencyLabel(tick, info.majorStep), style = tickLabelStyle)
            }
        } else emptyMap()
    }

    val bandLabelStyle = remember {
        TextStyle(
            fontSize = 7.sp,
            color = GrabberColors.Warning.copy(alpha = 0.7f),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
    val visibleBands = remember(startFreqKhz, stopFreqKhz) {
        REFERENCE_BANDS.filter { it.freqKhz in startFreqKhz..stopFreqKhz }
    }
    val bandTextLayouts = remember(textMeasurer, visibleBands, bandLabelStyle) {
        visibleBands.associateWith { band ->
            textMeasurer.measure(band.name, style = bandLabelStyle)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF0D1117))
            .border(1.dp, GrabberColors.Border, RoundedCornerShape(2.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (spanKhz > 0 && size.width > 0) {
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val tunedKhz = startFreqKhz + (fraction * spanKhz)
                        currentOnTune(tunedKhz)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            if (spanKhz <= 0 || tickInfo == null) {
                // Nessun dato: disegna solo una linea orizzontale di base
                drawLine(
                    color = Color(0xFF30363D),
                    start = Offset(0f, 0f),
                    end = Offset(w, 0f),
                    strokeWidth = 1f
                )
                return@Canvas
            }

            // Linea di base del righello (bordo superiore)
            drawLine(
                color = Color(0xFF58A6FF).copy(alpha = 0.4f),
                start = Offset(0f, 0f),
                end = Offset(w, 0f),
                strokeWidth = 1f
            )

            // ── Tacche secondarie (piccole, senza etichetta) ──
            for (tick in tickInfo.minorTicks) {
                val frac = ((tick - startFreqKhz) / spanKhz).coerceIn(0.0, 1.0).toFloat()
                val x = frac * w
                drawLine(
                    color = Color(0xFF30363D),
                    start = Offset(x, 0f),
                    end = Offset(x, h * 0.3f),
                    strokeWidth = 1f
                )
            }

            // ── Tacche principali (con etichetta frequenza pre-misurata) ──
            for (tick in tickInfo.majorTicks) {
                val frac = ((tick - startFreqKhz) / spanKhz).coerceIn(0.0, 1.0).toFloat()
                val x = frac * w

                // Tacca principale
                drawLine(
                    color = Color(0xFF58A6FF).copy(alpha = 0.6f),
                    start = Offset(x, 0f),
                    end = Offset(x, h * 0.5f),
                    strokeWidth = 1f
                )

                // Etichetta frequenza pre-misurata
                tickTextLayouts[tick]?.let { textLayout ->
                    val labelX = (x - textLayout.size.width / 2f).coerceIn(1f, w - textLayout.size.width - 1f)
                    drawText(
                        textLayout,
                        topLeft = Offset(labelX, h * 0.45f)
                    )
                }
            }

            // ── Etichette bande di riferimento pre-misurate ──
            var lastBandX = -100f
            for (band in visibleBands) {
                val frac = ((band.freqKhz - startFreqKhz) / spanKhz).coerceIn(0.0, 1.0).toFloat()
                val x = frac * w

                if (x - lastBandX < 40f) continue // Troppo vicina alla precedente

                // Tacca banda (colore diverso, ambra)
                drawLine(
                    color = GrabberColors.Warning.copy(alpha = 0.5f),
                    start = Offset(x, h * 0.55f),
                    end = Offset(x, h),
                    strokeWidth = 1f
                )

                bandTextLayouts[band]?.let { bandLayout ->
                    val bx = (x - bandLayout.size.width / 2f).coerceIn(1f, w - bandLayout.size.width - 1f)
                    drawText(
                        bandLayout,
                        topLeft = Offset(bx, h - bandLayout.size.height - 1f)
                    )
                }

                lastBandX = x
            }

            // ── Indicatore posizione VFO sul righello ──
            val vfoFrac = ((vfoKhz - startFreqKhz) / spanKhz).coerceIn(0.0, 1.0).toFloat()
            val vfoX = (vfoFrac * w).coerceIn(0f, w)
            drawLine(
                color = GrabberColors.Error,
                start = Offset(vfoX, 0f),
                end = Offset(vfoX, h),
                strokeWidth = 1.5f
            )
        }
    }
}
