package com.gig81.kiwisdrgrabber.ui.waterfall

import androidx.core.graphics.createBitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gig81.kiwisdrgrabber.ui.components.EmptyState
import com.gig81.kiwisdrgrabber.ui.components.Panel
import com.gig81.kiwisdrgrabber.ui.components.formatFrequencyKhz
import com.gig81.kiwisdrgrabber.ui.theme.DisplayKiwiColors
import com.gig81.kiwisdrgrabber.ui.theme.KiwiColors
import com.grabber.mobile.MobileAppController
import com.grabber.model.ConnectionState
import com.grabber.waterfall.WaterfallClient
import com.grabber.waterfall.WaterfallFrame
import com.grabber.waterfall.WaterfallPaletteType
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun WaterfallScreen(controller: MobileAppController, onConnect: () -> Unit) {
    val connection by controller.connectionState.collectAsStateWithLifecycle()
    if (connection !is ConnectionState.Connected) {
        EmptyState(
            message = "Lo spettro e il waterfall sono disponibili durante la connessione al ricevitore.",
            actionLabel = if (connection is ConnectionState.Disconnected || connection is ConnectionState.Error) "Connetti" else null,
            onAction = onConnect
        )
        return
    }

    val frame by controller.latestWaterfallFrame.collectAsStateWithLifecycle()
    val frequency by controller.currentFrequencyKhz.collectAsStateWithLifecycle()
    val wfState by controller.waterfallState.collectAsStateWithLifecycle()

    val display: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.background(DisplayKiwiColors.LcdBackground)) {
            SpectrumAndWaterfall(controller, frame, frequency)
            if (frame == null) {
                Text(
                    when (wfState) {
                        is ConnectionState.Error -> (wfState as ConnectionState.Error).message
                        else -> "In attesa dei dati dello spettro…"
                    },
                    modifier = Modifier.padding(16.dp),
                    color = DisplayKiwiColors.TextMuted
                )
            }
        }
    }

    // In orizzontale (o su tablet largo) i controlli vanno a lato, lasciando l'altezza allo spettro.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight && maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize()) {
                display(Modifier.weight(1f).fillMaxHeight())
                WaterfallControls(controller, frame, Modifier.width(320.dp).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                display(Modifier.fillMaxWidth().weight(1f))
                WaterfallControls(controller, frame, Modifier.fillMaxWidth().height(230.dp))
            }
        }
    }
}

@Composable
private fun SpectrumAndWaterfall(controller: MobileAppController, frame: WaterfallFrame?, tunedKhz: Double) {
    val buffer = controller.waterfallBuffer
    val version by controller.waterfallBufferVersion.collectAsStateWithLifecycle()
    val bitmap = remember(buffer) { createBitmap(buffer.width, buffer.height) }
    val pixels = remember(buffer) { IntArray(buffer.width * buffer.height) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val currentFrame by rememberUpdatedState(frame)
    var panAccumulator by remember { mutableFloatStateOf(0f) }
    var zoomAccumulator by remember { mutableFloatStateOf(1f) }

    Canvas(
        Modifier.fillMaxSize()
            .semantics { contentDescription = "Spettro e waterfall: tocca per sintonizzare, trascina per spostarti, pizzica per lo zoom" }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val f = currentFrame ?: return@detectTapGestures
                    controller.tuneFromWaterfall(f.frequencyAtFraction(offset.x / size.width))
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val f = currentFrame ?: return@detectTransformGestures
                    zoomAccumulator *= zoom
                    when {
                        zoomAccumulator > ZOOM_STEP_SCALE -> { controller.waterfallZoomIn(); zoomAccumulator = 1f }
                        zoomAccumulator < 1f / ZOOM_STEP_SCALE -> { controller.waterfallZoomOut(); zoomAccumulator = 1f }
                    }
                    // Lo spostamento viene inviato a passi (≥ 3% dello span) per non saturare il server.
                    panAccumulator += pan.x / size.width
                    if (abs(panAccumulator) >= 0.03f) {
                        controller.setWaterfallCenterFrequency(f.centerFreqKhz - panAccumulator * f.spanKhz)
                        panAccumulator = 0f
                    }
                }
            }
    ) {
        val spectrumHeight = size.height * SPECTRUM_FRACTION
        val waterfallTop = spectrumHeight
        // Rilettura della versione nel draw: il bitmap è aggiornato in place.
        if (version >= 0) {
            buffer.copyPixelsInto(pixels)
            bitmap.setPixels(pixels, 0, buffer.width, 0, 0, buffer.width, buffer.height)
        }
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(buffer.width, buffer.height),
            dstOffset = IntOffset(0, waterfallTop.roundToInt()),
            dstSize = IntSize(size.width.roundToInt(), (size.height - waterfallTop).roundToInt())
        )

        val f = frame ?: return@Canvas
        drawSpectrum(f, spectrumHeight, controller.waterfallMinDb.value, controller.waterfallMaxDb.value)
        if (tunedKhz in f.startFreqKhz..f.stopFreqKhz) {
            val x = f.fractionAtFrequency(tunedKhz) * size.width
            drawLine(DisplayKiwiColors.Error, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpectrum(
    frame: WaterfallFrame,
    height: Float,
    minDb: Int,
    maxDb: Int
) {
    drawRect(DisplayKiwiColors.SpectrumBackground, size = Size(size.width, height))
    val bins = frame.binCount
    if (bins < 2) return
    val range = (maxDb - minDb).coerceAtLeast(1).toFloat()
    val path = Path()
    for (i in 0 until bins) {
        val x = i.toFloat() / (bins - 1) * size.width
        val level = ((frame.getDbm(i) - minDb) / range).coerceIn(0f, 1f)
        val y = height - level * height
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, DisplayKiwiColors.LcdPrimary, style = Stroke(width = 1.5f))
    drawLine(DisplayKiwiColors.Border, Offset(0f, height), Offset(size.width, height))
}

@Composable
private fun WaterfallControls(controller: MobileAppController, frame: WaterfallFrame?, modifier: Modifier) {
    val zoom by controller.waterfallZoom.collectAsStateWithLifecycle()
    val autoFollow by controller.waterfallAutoFollow.collectAsStateWithLifecycle()
    val palette by controller.waterfallPalette.collectAsStateWithLifecycle()
    val minDb by controller.waterfallMinDb.collectAsStateWithLifecycle()
    val maxDb by controller.waterfallMaxDb.collectAsStateWithLifecycle()
    var paletteMenu by remember { mutableStateOf(false) }
    var dbRange by remember(minDb, maxDb) { mutableStateOf(minDb.toFloat()..maxDb.toFloat()) }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Panel {
            if (frame != null) {
                Text(
                    "${formatFrequencyKhz(frame.startFreqKhz)} – ${formatFrequencyKhz(frame.stopFreqKhz)} kHz · span ${formatFrequencyKhz(frame.spanKhz)} kHz",
                    style = MaterialTheme.typography.bodySmall,
                    color = KiwiColors.TextSecondary
                )
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = controller::waterfallZoomOut, enabled = zoom > 0) {
                    Icon(Icons.Filled.ZoomOut, contentDescription = "Riduci zoom")
                }
                Text("Zoom $zoom/${WaterfallClient.MAX_ZOOM}", modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = controller::waterfallZoomIn, enabled = zoom < WaterfallClient.MAX_ZOOM) {
                    Icon(Icons.Filled.ZoomIn, contentDescription = "Aumenta zoom")
                }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Segui la frequenza sintonizzata", modifier = Modifier.weight(1f))
                Switch(checked = autoFollow, onCheckedChange = controller::setWaterfallAutoFollow)
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Filled.Palette, contentDescription = null, tint = KiwiColors.TextSecondary)
                Box {
                    TextButton(onClick = { paletteMenu = true }) { Text("Palette: ${palette.displayName}") }
                    DropdownMenu(expanded = paletteMenu, onDismissRequest = { paletteMenu = false }) {
                        WaterfallPaletteType.entries.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.displayName) },
                                onClick = {
                                    paletteMenu = false
                                    controller.setWaterfallPalette(item)
                                }
                            )
                        }
                    }
                }
            }
            Text(
                "Range ${dbRange.start.roundToInt()} / ${dbRange.endInclusive.roundToInt()} dB",
                style = MaterialTheme.typography.bodySmall,
                color = KiwiColors.TextSecondary
            )
            RangeSlider(
                value = dbRange,
                onValueChange = { dbRange = it },
                onValueChangeFinished = {
                    controller.setWaterfallDbRange(dbRange.start.roundToInt(), dbRange.endInclusive.roundToInt())
                },
                valueRange = -150f..0f,
                modifier = Modifier.semantics { contentDescription = "Range dB del waterfall" }
            )
        }
    }
}

private const val SPECTRUM_FRACTION = 0.3f
private const val ZOOM_STEP_SCALE = 1.4f
