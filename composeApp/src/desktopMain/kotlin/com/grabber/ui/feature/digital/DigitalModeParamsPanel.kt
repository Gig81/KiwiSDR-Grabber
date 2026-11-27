package com.grabber.ui.feature.digital

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.kiwi.ExtProtocol
import com.grabber.model.digital.DigitalMode
import com.grabber.model.digital.DigitalModeParams
import com.grabber.model.digital.FskFraming
import com.grabber.model.digital.FskParams
import com.grabber.model.digital.FskPreset
import com.grabber.ui.designsystem.AppCheckbox
import com.grabber.ui.designsystem.AppMenuButton
import com.grabber.ui.designsystem.CompactTextField
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.GrabberColors

/** Parametri del modo (tabella §5.2 del piano), salvati subito e applicati al decoder attivo. */
@Composable
internal fun DigitalModeParamsPanel(mode: DigitalMode, params: DigitalModeParams, onChange: (DigitalModeParams) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (mode) {
            DigitalMode.FT8, DigitalMode.FT4 ->
                Check("Solo CQ", params.ft8OnlyCq) { onChange(params.copy(ft8OnlyCq = it)) }
            DigitalMode.WSPR -> Hint("BFO 750 Hz · nessun caricamento su wsprnet")
            DigitalMode.CW -> {
                Check("WPM automatico", params.cwAutoWpm) { onChange(params.copy(cwAutoWpm = it)) }
                if (!params.cwAutoWpm) {
                    NumberField("WPM", params.cwWpm.toString()) { v -> v.toIntOrNull()?.let { onChange(params.copy(cwWpm = it)) } }
                }
                Check("Soglia automatica", params.cwAutoThreshold) { onChange(params.copy(cwAutoThreshold = it)) }
                if (!params.cwAutoThreshold) {
                    NumberField("Soglia dB", params.cwThresholdDb.toString()) { v -> v.toIntOrNull()?.let { onChange(params.copy(cwThresholdDb = it)) } }
                }
                Check("Spazi tra parole", params.cwWordSpaceCorrection) { onChange(params.copy(cwWordSpaceCorrection = it)) }
            }
            DigitalMode.CW_SKIMMER -> {
                Label("Sensibilità")
                AppMenuButton(
                    selected = params.skimmerPowerCalc,
                    options = listOf(0, 1, 2),
                    label = { listOf("Rapporto medio", "Fondo medio", "Soglia")[it] },
                    onSelect = { onChange(params.copy(skimmerPowerCalc = it)) },
                    size = ControlSize.Dense
                )
                Check("Filtra segnali vicini", params.skimmerFilterNeighbors) { onChange(params.copy(skimmerFilterNeighbors = it)) }
            }
            DigitalMode.FAX -> {
                Label("LPM")
                AppMenuButton(
                    selected = params.faxLpm,
                    options = ExtProtocol.Fax.LPM_VALUES,
                    label = { "$it" },
                    onSelect = { onChange(params.copy(faxLpm = it)) },
                    size = ControlSize.Dense
                )
                Check("Fasatura", params.faxPhasing) { onChange(params.copy(faxPhasing = it)) }
                Check("Arresto automatico", params.faxAutoStop) { onChange(params.copy(faxAutoStop = it)) }
                Check("Salva automaticamente", params.faxAutoSave) { onChange(params.copy(faxAutoSave = it)) }
            }
            DigitalMode.SSTV -> {
                Check("Correzione automatica inclinazione", params.sstvAutoAdjust) { onChange(params.copy(sstvAutoAdjust = it)) }
                Check("Salva automaticamente", params.sstvAutoSave) { onChange(params.copy(sstvAutoSave = it)) }
            }
            DigitalMode.RTTY -> FskParamsRow(params.rtty) { onChange(params.copy(rtty = it)) }
            DigitalMode.NAVTEX -> {
                Hint("170 Hz · 100 Bd · SITOR-B")
                TextFilter("Stazioni (B1)", params.navtexStations) { onChange(params.copy(navtexStations = it)) }
                TextFilter("Tipi (B2)", params.navtexTypes) { onChange(params.copy(navtexTypes = it)) }
                Check("Scarta duplicati", params.navtexDiscardDuplicates) { onChange(params.copy(navtexDiscardDuplicates = it)) }
            }
            DigitalMode.DSC -> Hint("170 Hz · 100 Bd · solo informativo, non per la sicurezza della vita in mare")
            DigitalMode.HFDL -> Hint("Messaggi dumphfdl del server")
            DigitalMode.ALE -> Hint("Eventi ALE 2G; la scansione del server non viene usata")
        }
    }
}

@Composable
private fun FskParamsRow(rtty: FskParams, onChange: (FskParams) -> Unit) {
    AppMenuButton(
        selected = rtty.preset,
        options = FskPreset.entries,
        label = FskPreset::label,
        onSelect = { onChange(FskParams.forPreset(it, rtty)) },
        size = ControlSize.Dense
    )
    val custom = rtty.preset == FskPreset.CUSTOM
    NumberField("Shift Hz", rtty.shiftHz.toString(), enabled = custom) { v -> v.toIntOrNull()?.let { onChange(rtty.copy(shiftHz = it)) } }
    NumberField("Baud", formatBaud(rtty.baud), enabled = custom) { v -> v.replace(',', '.').toDoubleOrNull()?.let { onChange(rtty.copy(baud = it)) } }
    AppMenuButton(
        selected = rtty.framing,
        options = FskFraming.entries,
        label = FskFraming::label,
        onSelect = { onChange(rtty.copy(framing = it, preset = FskPreset.CUSTOM)) },
        size = ControlSize.Dense
    )
    Check("Invertito", rtty.inverted) { onChange(rtty.copy(inverted = it, preset = if (rtty.preset == FskPreset.CUSTOM) FskPreset.CUSTOM else rtty.preset)) }
    NumberField("Centro Hz", rtty.centerHz.toString()) { v -> v.toIntOrNull()?.let { onChange(rtty.copy(centerHz = it)) } }
}

private fun formatBaud(baud: Double): String =
    if (baud == baud.toLong().toDouble()) baud.toLong().toString() else baud.toString().replace('.', ',')

@Composable
private fun Check(text: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    AppCheckbox(checked = checked, onCheckedChange = onChange, text = text, size = ControlSize.Compact)

@Composable
private fun Label(text: String) = Text(text, fontSize = 10.sp, color = GrabberColors.TextSecondary)

@Composable
private fun Hint(text: String) = Text(text, fontSize = 10.sp, color = GrabberColors.TextSecondary)

/** Campo numerico: il valore si conferma con Invio o lasciando il campo (i valori fuori intervallo vengono limitati). */
@Composable
private fun NumberField(label: String, value: String, enabled: Boolean = true, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Label(label)
    if (enabled) {
        CompactTextField(
            value = text,
            onValueChange = { text = it },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text != value) onCommit(text) }),
            modifier = Modifier.width(54.dp).height(24.dp).onFocusChanged { if (!it.isFocused && text != value) onCommit(text) },
            monospace = true
        )
    } else {
        Text(value, fontSize = 11.sp, color = GrabberColors.TextPrimary)
    }
}

@Composable
private fun TextFilter(label: String, value: String, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Label(label)
    CompactTextField(
        value = text,
        onValueChange = { text = it.uppercase() },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        modifier = Modifier.width(70.dp).height(24.dp).onFocusChanged { if (!it.isFocused && text != value) onCommit(text) },
        placeholder = "tutte",
        monospace = true
    )
}
