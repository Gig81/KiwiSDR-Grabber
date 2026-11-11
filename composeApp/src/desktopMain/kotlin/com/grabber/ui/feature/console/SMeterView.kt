package com.grabber.ui.feature.console

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.audio.squelch.SMeterUtils
import com.grabber.ui.designsystem.GrabberColors
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

private val S_METER_TICK_FRACTIONS = floatArrayOf(
    0.06f, 0.18f, 0.31f, 0.43f, 0.56f, 0.67f, 0.78f, 0.89f, 1.00f
)

/**
 * Componente S-Meter isolato ad alte prestazioni con scala S-Unit e dBm.
 */
@Composable
internal fun IsolatedSMeterView(rssiFlow: StateFlow<Double>, thresholdDbm: Double? = null) {
    val rssi by rssiFlow.collectAsState()

    val sUnitsText = remember(rssi) {
        SMeterUtils.dbmToSUnits(rssi)
    }

    val normalizedRssi = ((rssi + 127.0) / 97.0).coerceIn(0.0, 1.0).toFloat()
    val normalizedThreshold = thresholdDbm?.let { SMeterUtils.dbmToNormalized(it) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "S-METER",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                normalizedRssi > 0.8f -> GrabberColors.Error.copy(alpha = 0.2f)
                                normalizedRssi > 0.5f -> GrabberColors.Warning.copy(alpha = 0.2f)
                                else -> GrabberColors.Success.copy(alpha = 0.2f)
                            }
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = sUnitsText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = when {
                            normalizedRssi > 0.8f -> GrabberColors.Error
                            normalizedRssi > 0.5f -> GrabberColors.Warning
                            else -> GrabberColors.Success
                        }
                    )
                }
            }

            Text(
                text = String.format(Locale.US, "%.1f dBm", rssi),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = GrabberColors.Primary
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(GrabberColors.LcdBackground)
                .border(1.dp, GrabberColors.Border, RoundedCornerShape(3.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                for (fraction in S_METER_TICK_FRACTIONS) {
                    val x = fraction * width
                    drawLine(
                        color = if (fraction > 0.56f) Color(0xFF552222) else Color(0xFF223322),
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 1f
                    )
                }

                val activeWidth = width * normalizedRssi
                if (activeWidth > 0f) {
                    val s9X = 0.56f * width
                    val greenWidth = minOf(activeWidth, s9X)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(GrabberColors.Success, GrabberColors.Warning),
                            startX = 0f,
                            endX = s9X
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(greenWidth, height)
                    )

                    if (activeWidth > s9X) {
                        drawRect(
                            color = GrabberColors.Error,
                            topLeft = Offset(s9X, 0f),
                            size = Size(activeWidth - s9X, height)
                        )
                    }
                }

                // Linea indicatore visivo della soglia Squelch
                if (normalizedThreshold != null) {
                    val threshX = normalizedThreshold * width
                    drawLine(
                        color = GrabberColors.Warning,
                        start = Offset(threshX, 0f),
                        end = Offset(threshX, height),
                        strokeWidth = 2f
                    )
                }
            }
        }
    }
}
