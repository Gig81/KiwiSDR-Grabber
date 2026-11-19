package com.grabber.ui.feature.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.app.KiwiAppController
import com.grabber.audio.dsp.DspLimits
import com.grabber.audio.dsp.DspPreset
import com.grabber.audio.dsp.DspTarget
import com.grabber.ui.designsystem.AppDivider
import com.grabber.ui.designsystem.AppMenuButton
import com.grabber.ui.designsystem.AppPanel
import com.grabber.ui.designsystem.AppScrollableColumn
import com.grabber.ui.designsystem.AppSegmentedControl
import com.grabber.ui.designsystem.AppSlider
import com.grabber.ui.designsystem.AppSubtleDivider
import com.grabber.ui.designsystem.AppToggleButton
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.FeatureLockedOverlay
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.SecondaryButton
import com.grabber.ui.designsystem.StatusBadge
import java.util.Locale
import kotlin.math.roundToInt

private fun bandLabel(hz: Int): String = if (hz >= 1000) {
    String.format(Locale.US, "%.1f kHz", hz / 1000.0).replace(".0 kHz", " kHz")
} else {
    "$hz Hz"
}

/**
 * Equalizzatore e filtri DSP applicati all'ascolto in tempo reale e, se scelto, alle nuove
 * registrazioni. Le registrazioni già salvate non vengono elaborate. Funzione riservata alla licenza.
 */
@Composable
internal fun AudioDspPanel(
    controller: KiwiAppController,
    isMachineLicensed: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config by controller.dspConfig.collectAsState()
    val isActive by controller.isDspActive.collectAsState()
    var bypassed by remember { mutableStateOf(false) }

    // Qualsiasi modifica riapplica la configurazione: il confronto A/B termina.
    LaunchedEffect(config) { bypassed = false }
    DisposableEffect(Unit) {
        onDispose { if (bypassed) controller.setDspBypass(false) }
    }

    val (badgeText, badgeColor) = when {
        !isMachineLicensed -> "BLOCCATO" to GrabberColors.TextDisabled
        bypassed && isActive -> "BYPASS" to GrabberColors.Warning
        isActive -> "ATTIVO" to GrabberColors.Success
        config.target == DspTarget.OFF -> "SPENTO" to GrabberColors.TextDisabled
        else -> "NEUTRO" to GrabberColors.TextDisabled
    }
    val targets = DspTarget.entries
    val presets = DspPreset.selectable
    val currentPreset = DspPreset.fromName(config.presetName) ?: DspPreset.CUSTOM

    AppPanel(
        modifier = modifier,
        title = "DSP",
        icon = Icons.Default.Equalizer,
        iconTint = GrabberColors.Primary,
        size = ControlSize.Compact,
        contentSpacing = 6.dp,
        actions = { StatusBadge(text = badgeText, color = badgeColor) },
        overlay = if (isMachineLicensed) {
            null
        } else {
            { FeatureLockedOverlay(message = "Equalizzatore e filtri DSP richiedono l'attivazione.", onActivate = onActivate) }
        }
    ) {
        AppDivider()
        AppScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(
                text = "Si applica all'ascolto in tempo reale e, con \"Ascolto e registrazione\", alle nuove registrazioni. Le registrazioni già salvate non vengono modificate.",
                fontSize = 10.sp,
                color = GrabberColors.TextSecondary
            )
            AppSegmentedControl(
                options = targets.map { it.displayName },
                selectedIndex = targets.indexOf(config.target),
                onSelectionChange = { controller.setDspTarget(targets[it]) },
                size = ControlSize.Dense,
                fillWidth = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Preset:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrabberColors.TextSecondary)
                AppMenuButton(
                    selected = currentPreset,
                    options = presets,
                    label = { it.displayName },
                    onSelect = controller::applyDspPreset,
                    icon = Icons.Default.Tune,
                    size = ControlSize.Dense
                )
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    // A/B: ascolto momentaneo senza DSP, per confrontare; non viene salvato.
                    AppToggleButton(
                        text = if (bypassed) "B: originale" else "A: DSP",
                        checked = !bypassed,
                        onClick = {
                            if (isActive) {
                                bypassed = !bypassed
                                controller.setDspBypass(bypassed)
                            }
                        },
                        size = ControlSize.Dense,
                        accentColor = GrabberColors.Primary
                    )
                }
                SecondaryButton(
                    text = "Reimposta",
                    onClick = controller::resetDsp,
                    icon = Icons.Default.Restore,
                    size = ControlSize.Dense
                )
            }

            AppSubtleDivider()
            Text("FILTRI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrabberColors.TextSecondary)
            AppSlider(
                label = "Passa-alto",
                valueText = if (config.highPassHz == 0) "OFF" else "${config.highPassHz} Hz",
                description = "Taglia ronzii e bassi sotto la frequenza indicata",
                value = config.highPassHz.toFloat(),
                valueRange = 0f..DspLimits.HIGH_PASS_HZ.last.toFloat(),
                steps = DspLimits.HIGH_PASS_HZ.last / 10 - 1,
                minLabel = "OFF",
                maxLabel = "${DspLimits.HIGH_PASS_HZ.last} Hz",
                size = ControlSize.Compact,
                onValueChange = {
                    val hz = (it / 10).roundToInt() * 10
                    controller.setDspHighPass(if (hz < DspLimits.HIGH_PASS_HZ.first) 0 else hz)
                }
            )
            AppSlider(
                label = "Passa-basso",
                valueText = if (config.lowPassHz == 0) "OFF" else "${config.lowPassHz} Hz",
                description = "Taglia fruscio e acuti sopra la frequenza indicata",
                value = (if (config.lowPassHz == 0) DspLimits.LOW_PASS_HZ.last else config.lowPassHz).toFloat(),
                valueRange = DspLimits.LOW_PASS_HZ.first.toFloat()..DspLimits.LOW_PASS_HZ.last.toFloat(),
                steps = (DspLimits.LOW_PASS_HZ.last - DspLimits.LOW_PASS_HZ.first) / 100 - 1,
                minLabel = "${DspLimits.LOW_PASS_HZ.first} Hz",
                maxLabel = "OFF",
                size = ControlSize.Compact,
                onValueChange = {
                    val hz = (it / 100).roundToInt() * 100
                    controller.setDspLowPass(if (hz >= DspLimits.LOW_PASS_HZ.last) 0 else hz)
                }
            )
            AppSlider(
                label = "Notch",
                valueText = if (config.notchHz == 0) "OFF" else "${config.notchHz} Hz",
                description = "Elimina una portante o un fischio fisso",
                value = config.notchHz.toFloat(),
                valueRange = 0f..DspLimits.NOTCH_HZ.last.toFloat(),
                steps = DspLimits.NOTCH_HZ.last / 10 - 1,
                minLabel = "OFF",
                maxLabel = "${DspLimits.NOTCH_HZ.last} Hz",
                size = ControlSize.Compact,
                onValueChange = {
                    val hz = (it / 10).roundToInt() * 10
                    controller.setDspNotch(if (hz < DspLimits.NOTCH_HZ.first) 0 else hz)
                }
            )
            if (config.notchHz > 0) {
                AppSlider(
                    label = "Larghezza notch (Q)",
                    valueText = String.format(Locale.US, "%.0f", config.notchQ),
                    description = "Q alto = notch più stretto",
                    value = config.notchQ.toFloat(),
                    valueRange = DspLimits.NOTCH_Q_MIN.toFloat()..DspLimits.NOTCH_Q_MAX.toFloat(),
                    steps = (DspLimits.NOTCH_Q_MAX - DspLimits.NOTCH_Q_MIN).toInt() - 1,
                    size = ControlSize.Compact,
                    onValueChange = { controller.setDspNotch(config.notchHz, it.roundToInt().toDouble()) }
                )
            }
            AppSlider(
                label = "Riduzione rumore",
                valueText = if (config.noiseReductionPercent == 0) "OFF" else "${config.noiseReductionPercent}%",
                description = "Attenua il fruscio a larga banda (filtro adattivo)",
                value = config.noiseReductionPercent.toFloat(),
                valueRange = 0f..100f,
                steps = 19,
                minLabel = "OFF",
                maxLabel = "100%",
                size = ControlSize.Compact,
                onValueChange = { controller.setDspNoiseReduction((it / 5).roundToInt() * 5) }
            )

            AppSubtleDivider()
            Text("EQUALIZZATORE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrabberColors.TextSecondary)
            DspLimits.EQ_BANDS_HZ.forEachIndexed { band, hz ->
                val gain = config.eqGainsDb[band]
                AppSlider(
                    label = bandLabel(hz),
                    valueText = String.format(Locale.US, "%+.1f dB", gain),
                    value = gain.toFloat(),
                    valueRange = DspLimits.EQ_MIN_DB.toFloat()..DspLimits.EQ_MAX_DB.toFloat(),
                    steps = ((DspLimits.EQ_MAX_DB - DspLimits.EQ_MIN_DB) * 2).toInt() - 1,
                    accentColor = if (gain >= 0) GrabberColors.Primary else GrabberColors.Warning,
                    size = ControlSize.Compact,
                    onValueChange = { controller.setDspEqGain(band, (it * 2).roundToInt() / 2.0) }
                )
            }
        }
    }
}
