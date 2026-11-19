package com.gig81.kiwisdrgrabber.ui.radio

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gig81.kiwisdrgrabber.ui.components.Panel
import com.gig81.kiwisdrgrabber.ui.theme.KiwiColors
import com.grabber.audio.dsp.DspLimits
import com.grabber.audio.dsp.DspPreset
import com.grabber.audio.dsp.DspTarget
import com.grabber.mobile.MobileAppController
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Riepilogo del DSP nella schermata Radio; "Configura" apre un pannello con destinazione, preset,
 * filtri ed equalizzatore. Stesse funzioni del pannello DSP desktop; riservato alla versione attivata.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DspPanel(controller: MobileAppController) {
    val activated by controller.isActivated.collectAsStateWithLifecycle()
    val config by controller.dspConfig.collectAsStateWithLifecycle()
    val active by controller.isDspActive.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }

    Panel(title = "Equalizzatore e filtri (DSP)") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val summary = when {
                    !activated -> "Disponibile nella versione attivata (gratuita)."
                    active -> "${config.target.displayName} · ${DspPreset.fromName(config.presetName)?.displayName ?: "Personalizzato"}"
                    config.target == DspTarget.OFF -> "Spento"
                    else -> "${config.target.displayName} · nessun filtro attivo"
                }
                Text(summary, style = MaterialTheme.typography.bodySmall, color = if (active) KiwiColors.Success else KiwiColors.TextSecondary)
            }
            Button(onClick = { if (controller.canUseDsp()) showSheet = true }) {
                Icon(if (activated) Icons.Filled.Equalizer else Icons.Filled.Lock, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Configura")
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = KiwiColors.Surface
        ) {
            DspSheetContent(controller)
        }
    }
}

@Composable
private fun DspSheetContent(controller: MobileAppController) {
    val config by controller.dspConfig.collectAsStateWithLifecycle()
    val active by controller.isDspActive.collectAsStateWithLifecycle()
    var bypassed by remember { mutableStateOf(false) }

    // Qualsiasi modifica riapplica la configurazione: il confronto A/B termina.
    LaunchedEffect(config) { bypassed = false }
    DisposableEffect(Unit) { onDispose { if (bypassed) controller.setDspBypass(false) } }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Equalizzatore e filtri", style = MaterialTheme.typography.titleMedium)
        Text(
            "Si applica all'ascolto e, con \"Ascolto e registrazione\", alle nuove registrazioni. Le registrazioni già salvate non vengono modificate.",
            style = MaterialTheme.typography.bodySmall,
            color = KiwiColors.TextSecondary
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DspTarget.entries.forEach { target ->
                FilterChip(selected = config.target == target, onClick = { controller.setDspTarget(target) }, label = { Text(target.displayName) })
            }
        }
        Text("Preset", style = MaterialTheme.typography.labelMedium, color = KiwiColors.TextSecondary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DspPreset.selectable.forEach { preset ->
                FilterChip(
                    selected = config.presetName == preset.name,
                    onClick = { controller.applyDspPreset(preset) },
                    label = { Text(preset.displayName) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = active,
                onClick = {
                    bypassed = !bypassed
                    controller.setDspBypass(bypassed)
                }
            ) { Text(if (bypassed) "B: originale" else "A: DSP") }
            OutlinedButton(onClick = controller::resetDsp) { Text("Reimposta") }
        }

        Text("Filtri", style = MaterialTheme.typography.titleSmall, color = KiwiColors.TextSecondary)
        DspSlider(
            label = "Passa-alto",
            valueText = if (config.highPassHz == 0) "OFF" else "${config.highPassHz} Hz",
            value = config.highPassHz.toFloat(),
            range = 0f..DspLimits.HIGH_PASS_HZ.last.toFloat(),
            onChange = {
                val hz = (it / 10).roundToInt() * 10
                controller.setDspHighPass(if (hz < DspLimits.HIGH_PASS_HZ.first) 0 else hz)
            }
        )
        DspSlider(
            label = "Passa-basso",
            valueText = if (config.lowPassHz == 0) "OFF" else "${config.lowPassHz} Hz",
            value = (if (config.lowPassHz == 0) DspLimits.LOW_PASS_HZ.last else config.lowPassHz).toFloat(),
            range = DspLimits.LOW_PASS_HZ.first.toFloat()..DspLimits.LOW_PASS_HZ.last.toFloat(),
            onChange = {
                val hz = (it / 100).roundToInt() * 100
                controller.setDspLowPass(if (hz >= DspLimits.LOW_PASS_HZ.last) 0 else hz)
            }
        )
        DspSlider(
            label = "Notch",
            valueText = if (config.notchHz == 0) "OFF" else "${config.notchHz} Hz",
            value = config.notchHz.toFloat(),
            range = 0f..DspLimits.NOTCH_HZ.last.toFloat(),
            onChange = {
                val hz = (it / 10).roundToInt() * 10
                controller.setDspNotch(if (hz < DspLimits.NOTCH_HZ.first) 0 else hz)
            }
        )
        if (config.notchHz > 0) {
            DspSlider(
                label = "Larghezza notch (Q)",
                valueText = String.format(Locale.ITALY, "%.0f", config.notchQ),
                value = config.notchQ.toFloat(),
                range = DspLimits.NOTCH_Q_MIN.toFloat()..DspLimits.NOTCH_Q_MAX.toFloat(),
                onChange = { controller.setDspNotch(config.notchHz, it.roundToInt().toDouble()) }
            )
        }
        DspSlider(
            label = "Riduzione rumore",
            valueText = if (config.noiseReductionPercent == 0) "OFF" else "${config.noiseReductionPercent}%",
            value = config.noiseReductionPercent.toFloat(),
            range = 0f..100f,
            onChange = { controller.setDspNoiseReduction((it / 5).roundToInt() * 5) }
        )

        Text("Equalizzatore", style = MaterialTheme.typography.titleSmall, color = KiwiColors.TextSecondary)
        DspLimits.EQ_BANDS_HZ.forEachIndexed { band, hz ->
            val gain = config.eqGainsDb[band]
            DspSlider(
                label = if (hz >= 1000) String.format(Locale.ITALY, "%.1f kHz", hz / 1000.0).replace(",0 kHz", " kHz") else "$hz Hz",
                valueText = String.format(Locale.ITALY, "%+.1f dB", gain),
                value = gain.toFloat(),
                range = DspLimits.EQ_MIN_DB.toFloat()..DspLimits.EQ_MAX_DB.toFloat(),
                onChange = { controller.setDspEqGain(band, (it * 2).roundToInt() / 2.0) }
            )
        }
        Spacer(Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun DspSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodySmall, color = KiwiColors.TextSecondary)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.semantics { contentDescription = label }
        )
    }
}
