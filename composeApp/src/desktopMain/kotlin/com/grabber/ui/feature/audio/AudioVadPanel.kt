package com.grabber.ui.feature.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.app.KiwiAppController
import com.grabber.ui.designsystem.AppDivider
import com.grabber.ui.designsystem.AppPanel
import com.grabber.ui.designsystem.AppScrollableColumn
import com.grabber.ui.designsystem.AppSlider
import com.grabber.ui.designsystem.AppSubtleDivider
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.FeatureLockedOverlay
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.GrabberTypography
import com.grabber.ui.designsystem.StatusBadge
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/** Voice Activity Detection: telemetria in tempo reale, sensibilità e hangover. Richiede la licenza. */
@Composable
internal fun AudioVadPanel(
    controller: KiwiAppController,
    isMachineLicensed: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by controller.settings.collectAsState()
    val vadListenEnabled by controller.vadListenEnabled.collectAsState()
    val vadRecordEnabled by controller.vadRecordEnabled.collectAsState()
    val isVoiceDetected by controller.isVoiceDetected.collectAsState()
    val isQrnDetected by controller.isQrnDetected.collectAsState()

    val (vadBadgeText, vadBadgeColor) = when {
        !vadListenEnabled && !vadRecordEnabled -> "VAD DISATTIVATO" to GrabberColors.TextDisabled
        isVoiceDetected -> "VOCE RILEVATA" to GrabberColors.Success
        isQrnDetected -> "SCARICA / QRN" to GrabberColors.Warning
        else -> "NESSUNA VOCE" to GrabberColors.TextDisabled
    }

    AppPanel(
        modifier = modifier,
        title = "VOICE ACTIVITY DETECTION (VAD)",
        icon = Icons.Default.RecordVoiceOver,
        iconTint = GrabberColors.Success,
        size = ControlSize.Compact,
        contentSpacing = 6.dp,
        actions = { StatusBadge(text = vadBadgeText, color = vadBadgeColor) },
        overlay = if (isMachineLicensed) {
            null
        } else {
            { FeatureLockedOverlay(message = "Il rilevamento VAD richiede l'attivazione.", onActivate = onActivate) }
        }
    ) {
        AppDivider()
        AppScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Spacer(modifier = Modifier.height(2.dp))
            CompactVadTelemetry(
                vadListenEnabled = vadListenEnabled,
                vadConfidenceFlow = controller.vadConfidence,
                sensitivity = settings.vadSensitivity,
                isVoiceDetected = isVoiceDetected,
                isQrnDetected = isQrnDetected
            )
            AppSubtleDivider()
            AppSlider(
                label = "Sensibilità Voce (Soglia)",
                valueText = "${(settings.vadSensitivity * 100).roundToInt()}%",
                description = "Soglia minima di confidenza richiesta per validare il parlato ed aprire il gate",
                value = settings.vadSensitivity,
                valueRange = 0.10f..0.90f,
                steps = 79,
                minLabel = "10%",
                maxLabel = "90%",
                accentColor = GrabberColors.Success,
                size = ControlSize.Compact,
                onValueChange = { controller.setVadSensitivity((it * 100).roundToInt() / 100f) }
            )
            AppSubtleDivider()
            AppSlider(
                label = "Hangover Vocale",
                valueText = "${settings.vadHangoverMs} ms",
                description = "Mantenimento apertura durante micro-pause tra le sillabe o consonanti deboli",
                value = settings.vadHangoverMs.toFloat(),
                valueRange = 50f..1000f,
                steps = 18,
                minLabel = "50ms",
                maxLabel = "1s",
                accentColor = GrabberColors.Secondary,
                size = ControlSize.Compact,
                onValueChange = { controller.setVadHangoverMs(it.roundToInt()) }
            )
        }
    }
}

/**
 * Telemetria Voice-Gate ultracompatta a linea singola con barra progressiva.
 * Isola vadConfidence per non causare la ricomposizione dell'intero pannello VAD.
 */
@Composable
private fun CompactVadTelemetry(
    vadListenEnabled: Boolean,
    vadConfidenceFlow: StateFlow<Float>,
    sensitivity: Float,
    isVoiceDetected: Boolean,
    isQrnDetected: Boolean
) {
    val vadConfidence by vadConfidenceFlow.collectAsState()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !vadListenEnabled -> GrabberColors.TextDisabled
                                isVoiceDetected -> GrabberColors.Success
                                isQrnDetected -> GrabberColors.Warning
                                else -> GrabberColors.TextDisabled
                            }
                        )
                )
                Text(
                    text = when {
                        !vadListenEnabled -> "Voice-gate disattivato (flusso audio libero)"
                        isVoiceDetected -> "Voce umana attiva — Gate aperto"
                        isQrnDetected -> "Scarica impulsiva o QRN — Gate bloccato"
                        else -> "In ascolto (nessuna voce)"
                    },
                    style = GrabberTypography.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = when {
                        !vadListenEnabled -> GrabberColors.TextDisabled
                        isVoiceDetected -> GrabberColors.Success
                        isQrnDetected -> GrabberColors.Warning
                        else -> GrabberColors.TextSecondary
                    }
                )
            }

            Text(
                text = "Conf: ${(vadConfidence * 100).roundToInt()}% (Soglia ${(sensitivity * 100).roundToInt()}%)",
                style = GrabberTypography.numeric,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = when {
                    !vadListenEnabled -> GrabberColors.TextDisabled
                    vadConfidence >= sensitivity -> GrabberColors.Success
                    isQrnDetected -> GrabberColors.Warning
                    else -> GrabberColors.TextSecondary
                }
            )
        }

        // Barra telemetrica con indicatore di soglia bianco
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(GrabberColors.SurfaceElevated)
        ) {
            val clamped = vadConfidence.coerceIn(0f, 1f)
            val barColor = when {
                !vadListenEnabled -> GrabberColors.TextDisabled
                isVoiceDetected -> GrabberColors.Success
                isQrnDetected -> GrabberColors.Warning
                else -> GrabberColors.Primary
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clamped)
                    .background(barColor)
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(sensitivity.coerceIn(0f, 1f))
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd)
                        .background(Color.White.copy(alpha = 0.9f))
                )
            }
        }
    }
}
