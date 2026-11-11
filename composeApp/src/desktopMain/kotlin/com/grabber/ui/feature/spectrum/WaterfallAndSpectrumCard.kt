package com.grabber.ui.feature.spectrum

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.grabber.app.KiwiAppController
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.waterfall.WaterfallClient

@Composable
fun WaterfallAndSpectrumCard(
    controller: KiwiAppController,
    isFullScreen: Boolean = false,
    expandWaterfall: Boolean = isFullScreen,
    showScaleSummary: Boolean = true,
    isMiniView: Boolean = false,
    showDynamicRangeControls: Boolean = !isMiniView,
    modifier: Modifier = Modifier
) {
    val connectionState by controller.connectionState.collectAsState()
    val waterfallState by controller.waterfallState.collectAsState()
    val currentZoom by controller.waterfallZoom.collectAsState()
    val currentVfoKhz by controller.currentFrequencyKhz.collectAsState()
    val currentMode by controller.currentMode.collectAsState()
    val currentPalette by controller.waterfallPalette.collectAsState()
    val currentMinDb by controller.waterfallMinDb.collectAsState()
    val currentMaxDb by controller.waterfallMaxDb.collectAsState()
    val currentSpeed by controller.waterfallSpeed.collectAsState()
    val waterfallCenterFreqKhz by controller.waterfallCenterFreqKhz.collectAsState()
    val isPaused by controller.isWaterfallPaused.collectAsState()
    val autoFollow by controller.waterfallAutoFollow.collectAsState()
    val settings by controller.settings.collectAsState()

    // Calcolo span e limiti frequenza da zoom e frequenza centrale:
    // Rimangono stabili tra i frame W/F, evitando continue ricomposizioni a catena
    val spanKhz = remember(currentZoom) { WaterfallClient.calculateSpanKhz(currentZoom) }
    val halfSpan = spanKhz / 2.0
    val maxStart = (WaterfallClient.TOTAL_SPECTRUM_KHZ - spanKhz).coerceAtLeast(0.0)
    val startFreqKhz = (waterfallCenterFreqKhz - halfSpan).coerceIn(0.0, maxStart)
    val stopFreqKhz = (startFreqKhz + spanKhz).coerceAtMost(WaterfallClient.TOTAL_SPECTRUM_KHZ)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = GrabberColors.SurfaceVariant),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(GrabberColors.Border))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(if (isMiniView) 4.dp else 6.dp),
            verticalArrangement = Arrangement.spacedBy(if (isMiniView) 2.dp else 4.dp)
        ) {
            // Header e Barra Strumenti
            if (isMiniView) {
                MiniWaterfallToolbar(
                    waterfallState = waterfallState,
                    connectionState = connectionState,
                    currentZoom = currentZoom,
                    isPaused = isPaused
                )
            } else {
                WaterfallToolbar(
                    controller = controller,
                    waterfallState = waterfallState,
                    connectionState = connectionState,
                    currentZoom = currentZoom,
                    currentPalette = currentPalette,
                    isPaused = isPaused,
                    autoFollow = autoFollow,
                    currentVfoKhz = currentVfoKhz,
                    currentMode = currentMode
                )

                if (showDynamicRangeControls) {
                    WaterfallDynamicRangeBar(
                        controller = controller,
                        currentMinDb = currentMinDb,
                        currentMaxDb = currentMaxDb,
                        speed = currentSpeed
                    )
                }
            }

            // Spettrometro FFT in tempo reale (linea dBm vs Freq + filtro passband)
            val spectrumHeight = if (isFullScreen) 140.dp else if (isMiniView) 75.dp else 95.dp
            SpectrumAnalyzerView(
                frameFlow = controller.latestWaterfallFrame,
                vfoKhz = currentVfoKhz,
                mode = currentMode,
                minDb = currentMinDb,
                maxDb = currentMaxDb,
                onTune = { controller.tuneFromWaterfall(it) },
                modifier = Modifier.fillMaxWidth().height(spectrumHeight)
            )

            // Righello frequenze tra spettro e waterfall con etichette adattive allo zoom
            val rulerHeight = if (isMiniView) 18.dp else 22.dp
            FrequencyRulerView(
                startFreqKhz = startFreqKhz,
                stopFreqKhz = stopFreqKhz,
                spanKhz = spanKhz,
                vfoKhz = currentVfoKhz,
                currentZoom = currentZoom,
                onTune = { controller.tuneFromWaterfall(it) },
                modifier = Modifier.fillMaxWidth().height(rulerHeight)
            )

            // Cascata Waterfall 2D a scorrimento temporale (Click-to-Tune)
            val waterfallModifier = if (expandWaterfall) {
                Modifier.fillMaxWidth().weight(1f)
            } else {
                Modifier.fillMaxWidth().height(160.dp)
            }

            WaterfallCanvasView(
                controller = controller,
                startFreqKhz = startFreqKhz,
                stopFreqKhz = stopFreqKhz,
                spanKhz = spanKhz,
                vfoKhz = currentVfoKhz,
                favorites = settings.favorites,
                onTune = { controller.tuneFromWaterfall(it) },
                modifier = waterfallModifier
            )

            // Riga di riepilogo frequenze (Start, Centro, Stop, Risoluzione RBW)
            if (showScaleSummary && !isMiniView) {
                WaterfallScaleSummary(
                    startFreqKhz = startFreqKhz,
                    centerFreqKhz = waterfallCenterFreqKhz,
                    stopFreqKhz = stopFreqKhz,
                    currentZoom = currentZoom,
                    vfoKhz = currentVfoKhz
                )
            }
        }
    }
}
