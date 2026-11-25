package com.grabber.ui.feature.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.app.AutoCenterResult
import com.grabber.app.KiwiAppController
import com.grabber.audio.squelch.SMeterUtils
import com.grabber.ui.designsystem.AppTooltip
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.feature.audio.GateTarget
import com.grabber.ui.feature.audio.explanation
import com.grabber.ui.feature.audio.label
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * Pannello audio live e gate per la console superiore.
 */
@Composable
internal fun ConsoleAudioPanel(
    controller: KiwiAppController,
    modifier: Modifier = Modifier
) {
    val volume by controller.volume.collectAsState()
    val isMuted by controller.isMuted.collectAsState()
    val thresholdDbm by controller.squelchThresholdDbm.collectAsState()
    // SQL e VAD sono le due componenti della modalità del gate di ascolto (con entrambi: "RF e voce").
    val listenGateMode by controller.listenGateMode.collectAsState()
    val squelchEnabled = listenGateMode.usesRf
    val vadEnabled = listenGateMode.usesVoice
    val gateTooltip = "Ascolto: ${listenGateMode.label(GateTarget.LISTEN)}. ${listenGateMode.explanation(GateTarget.LISTEN)}"
    val isVoiceDetected by controller.isVoiceDetected.collectAsState()
    val isQrnDetected by controller.isQrnDetected.collectAsState()
    val settings by controller.settings.collectAsState()

    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = GrabberColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, GrabberColors.Border),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IsolatedSMeterView(rssiFlow = controller.rssi, thresholdDbm = thresholdDbm)

            ConsoleSliderRow(
                label = "VOL",
                icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                iconDescription = if (isMuted) "Attiva audio" else "Disattiva audio",
                labelColor = if (isMuted) GrabberColors.Error else GrabberColors.TextSecondary,
                iconTint = if (isMuted) GrabberColors.Error else GrabberColors.Primary,
                onLabelClick = controller::toggleMute,
                value = volume,
                onValueChange = controller::setVolume,
                accentColor = GrabberColors.Primary,
                valueText = "${(volume * 100).roundToInt()}%",
                valueColor = GrabberColors.TextPrimary
            )
            ConsoleSliderRow(
                label = "SQL",
                icon = Icons.Default.GraphicEq,
                labelColor = if (squelchEnabled) GrabberColors.Primary else GrabberColors.TextDisabled,
                onLabelClick = { controller.setListenGateMode(listenGateMode.withRf(!squelchEnabled)) },
                labelTooltip = "$gateTooltip Clic: squelch RF ${if (squelchEnabled) "off" else "on"}.",
                value = SMeterUtils.dbmToNormalized(thresholdDbm),
                onValueChange = { controller.setSquelchThreshold(SMeterUtils.normalizedToDbm(it)) },
                accentColor = GrabberColors.Warning,
                valueText = "${thresholdDbm.roundToInt()}"
            )
            ConsoleSliderRow(
                label = "VAD",
                icon = if (vadEnabled) Icons.Default.GraphicEq else Icons.Default.MicOff,
                labelColor = if (vadEnabled) GrabberColors.Success else GrabberColors.TextDisabled,
                onLabelClick = { controller.setListenGateMode(listenGateMode.withVoice(!vadEnabled)) },
                labelTooltip = "$gateTooltip Clic: voice gate ${if (vadEnabled) "off" else "on"}.",
                value = settings.vadSensitivity,
                onValueChange = controller::setVadSensitivity,
                valueRange = 0.1f..0.9f,
                accentColor = GrabberColors.Success,
                valueText = "${(settings.vadSensitivity * 100).roundToInt()}%"
            )

            ConsoleVadTelemetryRow(
                vadEnabled = vadEnabled,
                isVoiceDetected = isVoiceDetected,
                isQrnDetected = isQrnDetected,
                vadConfidenceFlow = controller.vadConfidence
            )

            ConsoleAfcRow(controller = controller, licensed = controller.isLicenseValid(settings.licenseKey))
        }
    }
}

/**
 * Riga "etichetta + slider + valore": l'etichetta con icona attiva o disattiva la funzione,
 * lo slider ne regola il livello.
 */
@Composable
private fun ConsoleSliderRow(
    label: String,
    icon: ImageVector,
    labelColor: Color,
    onLabelClick: () -> Unit,
    value: Float,
    onValueChange: (Float) -> Unit,
    accentColor: Color,
    valueText: String,
    iconTint: Color = labelColor,
    iconDescription: String? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    valueColor: Color = GrabberColors.TextSecondary,
    labelTooltip: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val labelContent: @Composable () -> Unit = {
            Row(
                modifier = Modifier
                    .width(56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onLabelClick)
                    .padding(horizontal = 2.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(icon, contentDescription = iconDescription, tint = iconTint, modifier = Modifier.size(16.dp))
                Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = labelColor)
            }
        }
        if (labelTooltip != null) AppTooltip(text = labelTooltip, content = labelContent) else labelContent()
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f).height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = GrabberColors.SurfaceVariant
            )
        )
        Text(
            valueText,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = valueColor
        )
    }
}

/**
 * Riga di telemetria VAD isolata a foglia: il campionamento frequente (60ms) di vadConfidence
 * ricompone unicamente questa barra e il suo valore percentuale, senza sporcare il resto del pannello audio.
 */
@Composable
private fun ConsoleVadTelemetryRow(
    vadEnabled: Boolean,
    isVoiceDetected: Boolean,
    isQrnDetected: Boolean,
    vadConfidenceFlow: StateFlow<Float>
) {
    val vadConfidence by vadConfidenceFlow.collectAsState()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val gateColor = when {
            !vadEnabled -> GrabberColors.TextDisabled
            isQrnDetected -> GrabberColors.Warning
            isVoiceDetected -> GrabberColors.Success
            else -> GrabberColors.TextSecondary
        }
        val gateLabel = when {
            !vadEnabled -> "VAD OFF"
            isVoiceDetected -> "GATE APERTO"
            else -> "GATE CHIUSO"
        }
        Text(
            gateLabel,
            modifier = Modifier.width(78.dp),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = gateColor,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(GrabberColors.SurfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(vadConfidence.coerceIn(0f, 1f))
                    .background(
                        when {
                            vadConfidence >= 0.45f -> GrabberColors.Success
                            vadConfidence >= 0.25f -> GrabberColors.Warning
                            else -> GrabberColors.TextDisabled
                        }
                    )
            )
        }
        Text(
            "${(vadConfidence * 100).roundToInt()}%",
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            color = GrabberColors.TextSecondary
        )
    }
}

/**
 * Riga AFC: interruttore dell'autocentraggio (spento di default, riservato alla licenza) ed esito
 * dell'ultima correzione, che si attenua dopo qualche secondo.
 */
@Composable
private fun ConsoleAfcRow(controller: KiwiAppController, licensed: Boolean) {
    val enabled by controller.autoCenterEnabled.collectAsState()
    val result by controller.lastAutoCenter.collectAsState()
    var fresh by remember { mutableStateOf(false) }
    LaunchedEffect(result) {
        fresh = result != null
        if (result != null) {
            delay(AFC_RESULT_VISIBLE_MS)
            fresh = false
        }
    }
    val active = enabled && licensed
    val tooltip = if (!licensed) {
        "Autocentraggio: richiede l'attivazione della licenza."
    } else {
        "Autocentraggio (AFC): dopo una frequenza digitata o un clic sul waterfall/spettro centra il segnale. " +
            "Agisce in CW (tono a centro banda), AM (portante), NFM e con i decoder RTTY/NAVTEX/FAX; non in USB/LSB voce."
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AppTooltip(text = tooltip) {
            Row(
                modifier = Modifier
                    .width(56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = licensed) { controller.setAutoCenterEnabled(!enabled) }
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.CenterFocusStrong,
                    contentDescription = null,
                    tint = if (active) GrabberColors.Primary else GrabberColors.TextDisabled,
                    modifier = Modifier.size(14.dp)
                )
                Text("AFC", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (active) GrabberColors.Primary else GrabberColors.TextDisabled)
            }
        }
        Switch(
            checked = active,
            onCheckedChange = { controller.setAutoCenterEnabled(it) },
            enabled = licensed,
            modifier = Modifier.scale(0.55f).height(18.dp),
            colors = SwitchDefaults.colors(checkedTrackColor = GrabberColors.Primary)
        )
        Text(
            "Centraggio automatico",
            modifier = Modifier.weight(1f),
            fontSize = 8.sp,
            color = GrabberColors.TextSecondary,
            maxLines = 1
        )
        val current = result
        if (active && current != null) {
            val text = when (current.outcome) {
                AutoCenterResult.Outcome.APPLIED, AutoCenterResult.Outcome.CENTERED ->
                    current.offsetHz?.let { String.format(java.util.Locale.US, "%+.0f Hz", it) } ?: current.outcome.label
                else -> current.outcome.label
            }
            val color = when (current.outcome) {
                AutoCenterResult.Outcome.APPLIED, AutoCenterResult.Outcome.CENTERED -> GrabberColors.Success
                AutoCenterResult.Outcome.NOT_APPLICABLE -> GrabberColors.TextDisabled
                else -> GrabberColors.Warning
            }
            Text(
                text,
                modifier = Modifier.alpha(if (fresh) 1f else 0.45f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )
        }
    }
}

private const val AFC_RESULT_VISIBLE_MS = 6_000L
