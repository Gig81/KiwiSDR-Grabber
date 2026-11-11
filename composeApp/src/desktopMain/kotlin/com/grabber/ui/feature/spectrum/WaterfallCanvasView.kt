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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.app.FavoriteFrequency
import com.grabber.app.KiwiAppController
import com.grabber.ui.designsystem.GrabberColors
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Composable
internal fun WaterfallCanvasView(
    controller: KiwiAppController,
    startFreqKhz: Double,
    stopFreqKhz: Double,
    spanKhz: Double,
    vfoKhz: Double,
    favorites: List<FavoriteFrequency>,
    onTune: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val currentOnTune by rememberUpdatedState(onTune)
    val buffer = controller.waterfallBuffer

    // Riutilizzo dell'istanza BufferedImage per evitare allocazioni heap a 10 Hz
    val reusableImage = remember(buffer.width, buffer.height) {
        BufferedImage(buffer.width, buffer.height, BufferedImage.TYPE_INT_ARGB)
    }
    val targetArray = remember(reusableImage) {
        (reusableImage.raster.dataBuffer as DataBufferInt).data
    }
    var composeImageBitmap by remember(buffer) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(buffer) {
        controller.waterfallBufferVersion
            .collect {
                delay(100L)
                composeImageBitmap = withContext(Dispatchers.Default) {
                    buffer.copyPixelsInto(targetArray)
                    reusableImage.toComposeImageBitmap()
                }
            }
    }

    // Pre-misurazione etichette preferiti visibili nello span corrente
    val favLabelStyle = remember {
        TextStyle(
            fontSize = 8.sp,
            color = GrabberColors.Warning,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
    val visibleFavoriteLayouts = remember(favorites, startFreqKhz, stopFreqKhz, textMeasurer, favLabelStyle) {
        favorites.asSequence()
            .filter { it.frequencyKhz in startFreqKhz..stopFreqKhz }
            .sortedBy { it.frequencyKhz }
            .map { fav ->
                val label = fav.name.take(14).let { if (fav.name.length > 14) "$it…" else it }
                fav.frequencyKhz to textMeasurer.measure(label, style = favLabelStyle)
            }
            .toList()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF05080E))
            .border(1.dp, GrabberColors.Border, RoundedCornerShape(3.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (spanKhz > 0 && size.width > 0) {
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val tunedKhz = startFreqKhz + (fraction * spanKhz)
                        currentOnTune(tunedKhz)
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    if (spanKhz > 0 && size.width > 0) {
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        val tunedKhz = startFreqKhz + (fraction * spanKhz)
                        currentOnTune(tunedKhz)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Disegna l'intera immagine scalata per riempire la larghezza e altezza del canvas
            composeImageBitmap?.let {
                drawImage(
                    image = it,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(it.width, it.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(w.roundToInt(), h.roundToInt())
                )
            }

            // Linea verticale del VFO sulla cascata (rosso semi-trasparente)
            if (spanKhz > 0) {
                val vfoFrac = ((vfoKhz - startFreqKhz) / spanKhz).coerceIn(0.0, 1.0).toFloat()
                val vfoX = (vfoFrac * w).coerceIn(0f, w)

                drawLine(
                    color = GrabberColors.Error.copy(alpha = 0.85f),
                    start = Offset(vfoX, 0f),
                    end = Offset(vfoX, h),
                    strokeWidth = 1.5f
                )

                var previousLabelRight = Float.NEGATIVE_INFINITY
                for ((freqKhz, textLayout) in visibleFavoriteLayouts) {
                    val frac = ((freqKhz - startFreqKhz) / spanKhz).coerceIn(0.0, 1.0).toFloat()
                    val x = frac * w
                    drawLine(
                        color = GrabberColors.Warning.copy(alpha = 0.7f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1f
                    )

                    val labelX = (x + 3f).coerceIn(1f, (w - textLayout.size.width - 2f).coerceAtLeast(1f))
                    if (labelX >= previousLabelRight + 3f) {
                        drawRect(
                            color = Color.Black.copy(alpha = 0.7f),
                            topLeft = Offset(labelX - 1f, 1f),
                            size = Size(textLayout.size.width + 2f, textLayout.size.height + 2f)
                        )
                        drawText(textLayout, topLeft = Offset(labelX, 2f))
                        previousLabelRight = labelX + textLayout.size.width
                    }
                }
            }
        }
    }
}
