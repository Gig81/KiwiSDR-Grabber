package com.grabber.ui.feature.audio

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grabber.app.KiwiAppController
import com.grabber.audio.squelch.SquelchGateState
import com.grabber.ui.designsystem.AppDivider
import com.grabber.ui.designsystem.AppPanel
import com.grabber.ui.designsystem.AppScrollableColumn
import com.grabber.ui.designsystem.AppSlider
import com.grabber.ui.designsystem.AppSubtleDivider
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.StatusBadge
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.math.roundToInt

/** Soft mute, pre-buffer e hang time dello squelch, con lo stato del gate in tempo reale. */
@Composable
internal fun AudioDspPanel(
    controller: KiwiAppController,
    modifier: Modifier = Modifier
) {
    val settings by controller.settings.collectAsState()
    val squelchListenEnabled by controller.squelchListenEnabled.collectAsState()
    val squelchRecordEnabled by controller.squelchRecordEnabled.collectAsState()
    val squelchGateOpen by controller.squelchGateOpen.collectAsState()
    val squelchState by controller.squelchState.collectAsState()

    AppPanel(
        modifier = modifier,
        title = "DSP, ANTI-POP E HANG TIME",
        icon = Icons.Default.Tune,
        iconTint = GrabberColors.Primary,
        size = ControlSize.Compact,
        contentSpacing = 6.dp,
        actions = {
            SquelchStatusBadge(
                squelchListenEnabled = squelchListenEnabled,
                squelchRecordEnabled = squelchRecordEnabled,
                squelchGateOpen = squelchGateOpen,
                squelchState = squelchState,
                hangTimeRemainingFlow = controller.squelchHangTimeRemaining
            )
        }
    ) {
        AppDivider()
        AppScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Spacer(modifier = Modifier.height(2.dp))
            AppSlider(
                label = "Soft Mute (Anti-Pop)",
                valueText = "${settings.softMuteMs} ms",
                description = "Rampa fade-in/fade-out anti-clic all'apertura e chiusura del gate",
                value = settings.softMuteMs.toFloat(),
                valueRange = 2f..50f,
                steps = 47,
                minLabel = "2ms",
                maxLabel = "50ms",
                accentColor = GrabberColors.Primary,
                size = ControlSize.Compact,
                onValueChange = { controller.setSoftMuteMs(it.roundToInt()) }
            )
            AppSubtleDivider()
            AppSlider(
                label = "Pre-Buffer RAM (Anti-Troncamento)",
                valueText = "${settings.preBufferMs} ms",
                description = "Buffer circolare per salvare la sillaba iniziale o nominativo",
                value = settings.preBufferMs.toFloat(),
                valueRange = 100f..1000f,
                steps = 17,
                minLabel = "100ms",
                maxLabel = "1s",
                accentColor = GrabberColors.Warning,
                size = ControlSize.Compact,
                onValueChange = { controller.setPreBufferMs(it.roundToInt()) }
            )
            AppSubtleDivider()
            AppSlider(
                label = "Hang Time (Cooldown QSO)",
                valueText = String.format(Locale.US, "%.1f s", settings.hangTimeSeconds),
                description = "Mantiene il gate aperto durante le pause per unire i passaggi",
                value = settings.hangTimeSeconds.toFloat(),
                valueRange = 0.5f..10.0f,
                steps = 94,
                minLabel = "0.5s",
                maxLabel = "10s",
                accentColor = GrabberColors.Success,
                size = ControlSize.Compact,
                onValueChange = { controller.setHangTimeSeconds((it * 10).roundToInt() / 10.0) }
            )
        }
    }
}

/** Isola la lettura del tempo di hang residuo per non ricomporre l'intero pannello. */
@Composable
private fun SquelchStatusBadge(
    squelchListenEnabled: Boolean,
    squelchRecordEnabled: Boolean,
    squelchGateOpen: Boolean,
    squelchState: SquelchGateState,
    hangTimeRemainingFlow: StateFlow<Double>
) {
    val hangTimeRemaining by hangTimeRemainingFlow.collectAsState()
    val (gateBadgeText, gateBadgeColor) = when {
        !squelchListenEnabled && !squelchRecordEnabled -> "SQUELCH OFF" to GrabberColors.TextDisabled
        squelchGateOpen -> "GATE APERTO" to GrabberColors.Success
        squelchState == SquelchGateState.OPEN_HANG_TIME ->
            "HANG TIME (${String.format(Locale.US, "%.1f", hangTimeRemaining)}s)" to GrabberColors.Warning
        else -> "GATE CHIUSO" to GrabberColors.TextDisabled
    }
    StatusBadge(text = gateBadgeText, color = gateBadgeColor)
}
