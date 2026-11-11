package com.grabber.ui.feature.spectrum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.model.ModulationMode
import com.grabber.waterfall.WaterfallFrame
import kotlinx.coroutines.flow.StateFlow
import com.grabber.ui.designsystem.GrabberColors

@Composable
internal fun SpectrumAnalyzerView(
    frameFlow: StateFlow<WaterfallFrame?>,
    vfoKhz: Double,
    mode: ModulationMode,
    minDb: Int,
    maxDb: Int,
    onTune: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val frame by frameFlow.collectAsState()
    val textMeasurer = rememberTextMeasurer()
    val currentFrame by rememberUpdatedState(frame)
    val currentOnTune by rememberUpdatedState(onTune)
    val spectrumPath = remember { Path() }

    // Pre-misurazione etichette dBm fisse: evita misurazioni ad ogni frame nel draw loop
    val dbTextStyle = remember {
        TextStyle(
            fontSize = 8.sp,
            color = Color(0xFF4A5568),
            fontFamily = FontFamily.Monospace
        )
    }
    val dbLabels = remember(textMeasurer, dbTextStyle) {
        listOf(-30, -60, -90, -120).associateWith { db ->
            textMeasurer.measure("${db} dB", style = dbTextStyle)
        }
    }

    // Pre-calcolo e pre-misurazione tacche ed etichette frequenza: si aggiornano solo al cambio di banda/zoom
    val freqTicksInfo = remember(frame?.startFreqKhz, frame?.stopFreqKhz, frame?.spanKhz) {
        val f = frame
        if (f != null && f.spanKhz > 0) {
            computeFrequencyTicks(f.startFreqKhz, f.stopFreqKhz, f.spanKhz)
        } else null
    }

    val freqTextLayouts = remember(textMeasurer, freqTicksInfo, dbTextStyle) {
        val info = freqTicksInfo
        val f = frame
        if (info != null && f != null) {
            info.majorTicks
                .filter { it in f.startFreqKhz..f.stopFreqKhz }
                .associateWith { tick ->
                    textMeasurer.measure(formatFrequencyLabel(tick, info.majorStep), style = dbTextStyle)
                }
        } else emptyMap()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF070B10))
            .border(1.dp, GrabberColors.Border, RoundedCornerShape(3.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val f = currentFrame
                    if (f != null && size.width > 0) {
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val tunedKhz = f.frequencyAtFraction(fraction)
                        currentOnTune(tunedKhz)
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val f = currentFrame
                    if (f != null && size.width > 0) {
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        val tunedKhz = f.frequencyAtFraction(fraction)
                        currentOnTune(tunedKhz)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Griglia ampiezza dBm (orizzontale) con etichette dB pre-misurate
            val dbGrid = listOf(-30, -60, -90, -120)
            val dbRange = (maxDb - minDb).coerceAtLeast(1)

            for (db in dbGrid) {
                if (db in (minDb + 5)..(maxDb - 5)) {
                    val y = h - ((db - minDb).toFloat() / dbRange) * h
                    drawLine(
                        color = Color(0xFF1E2630),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                    // Etichetta dB sulla sinistra
                    dbLabels[db]?.let { layout ->
                        drawText(layout, topLeft = Offset(2f, y - layout.size.height - 1f))
                    }
                }
            }

            // 2. Griglia frequenza (verticale) con etichette pre-misurate
            val curFrame = frame
            if (curFrame != null && curFrame.spanKhz > 0 && freqTicksInfo != null) {
                for (tick in freqTicksInfo.majorTicks) {
                    if (tick < curFrame.startFreqKhz || tick > curFrame.stopFreqKhz) continue
                    val frac = curFrame.fractionAtFrequency(tick)
                    val x = frac * w
                    drawLine(
                        color = Color(0xFF1E2630),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1f
                    )
                    // Etichetta frequenza in basso (pre-misurata)
                    freqTextLayouts[tick]?.let { layout ->
                        val labelX = (x - layout.size.width / 2f).coerceIn(0f, w - layout.size.width)
                        drawText(layout, topLeft = Offset(labelX, h - layout.size.height - 1f))
                    }
                }
            } else {
                // Fallback: griglia fissa a 5 sezioni quando non c'è frame
                for (i in 1..4) {
                    val x = w * (i / 5f)
                    drawLine(
                        color = Color(0xFF1E2630),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1f
                    )
                }
            }

            // Se non ci sono frame, mostra linea piatta
            if (curFrame == null || curFrame.binCount == 0) {
                drawLine(
                    color = Color.Gray.copy(alpha = 0.3f),
                    start = Offset(0f, h * 0.9f),
                    end = Offset(w, h * 0.9f),
                    strokeWidth = 1f
                )
                return@Canvas
            }

            // 3. Tracciamento Passband filtro RF
            val vfoFrac = curFrame.fractionAtFrequency(vfoKhz)
            val (lowCutKhz, highCutKhz) = when (mode) {
                ModulationMode.AM -> -5.0 to 5.0
                ModulationMode.USB -> 0.3 to 2.7
                ModulationMode.LSB -> -2.7 to -0.3
                ModulationMode.CW -> 0.6 to 0.9
                ModulationMode.NFM -> -6.0 to 6.0
            }

            val filterLeftFrac = curFrame.fractionAtFrequency(vfoKhz + lowCutKhz)
            val filterRightFrac = curFrame.fractionAtFrequency(vfoKhz + highCutKhz)

            val filterLeftX = (filterLeftFrac * w).coerceIn(0f, w)
            val filterRightX = (filterRightFrac * w).coerceIn(0f, w)
            val filterWidth = (filterRightX - filterLeftX).coerceAtLeast(2f)

            drawRect(
                color = GrabberColors.Primary.copy(alpha = 0.18f),
                topLeft = Offset(filterLeftX, 0f),
                size = Size(filterWidth, h)
            )

            // Linea centrale VFO (Marker sintonizzazione)
            val vfoX = (vfoFrac * w).coerceIn(0f, w)
            drawLine(
                color = GrabberColors.Error,
                start = Offset(vfoX, 0f),
                end = Offset(vfoX, h),
                strokeWidth = 1.5f
            )

            // 4. Tracciamento curva FFT (1024 bin) con path riutilizzabile
            val bins = curFrame.bins
            val count = bins.size
            if (count > 1) {
                spectrumPath.reset()
                val stepX = w / (count - 1).toFloat()

                for (i in 0 until count) {
                    val dbm = curFrame.getDbm(i)
                    val normY = ((dbm - minDb).toFloat() / dbRange).coerceIn(0f, 1f)
                    val y = h - (normY * h)
                    val x = i * stepX

                    if (i == 0) {
                        spectrumPath.moveTo(x, y)
                    } else {
                        spectrumPath.lineTo(x, y)
                    }
                }

                drawPath(
                    path = spectrumPath,
                    color = GrabberColors.LcdPrimary,
                    style = Stroke(width = 1.2f, cap = StrokeCap.Round)
                )
            }
        }
    }
}
