package com.grabber.ui.feature.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.audio.squelch.GateMode
import com.grabber.ui.designsystem.AppSegmentedControl
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.GrabberColors

/** Gate a cui si riferisce una [GateMode]: cambiano etichette e spiegazioni. */
internal enum class GateTarget { LISTEN, RECORD }

internal fun GateMode.label(target: GateTarget): String = when (this) {
    GateMode.OFF -> if (target == GateTarget.LISTEN) "Libero" else "Continua"
    GateMode.RF -> "Solo RF"
    GateMode.VOICE -> "Solo voce"
    GateMode.RF_AND_VOICE -> "RF e voce"
}

/** Spiegazione breve del comportamento, mostrata sotto il selettore e nei tooltip. */
internal fun GateMode.explanation(target: GateTarget): String = when (target) {
    GateTarget.LISTEN -> when (this) {
        GateMode.OFF -> "Audio sempre udibile, senza gate."
        GateMode.RF -> "Si sente solo con segnale sopra la soglia SQL, più l'hang time."
        GateMode.VOICE -> "Si sente solo quando il VAD rileva la voce, qualunque sia il segnale."
        GateMode.RF_AND_VOICE -> "Servono segnale sopra la soglia SQL e voce: esclude portanti e rumore."
    }
    GateTarget.RECORD -> when (this) {
        GateMode.OFF -> "Registrazione continua o a intervalli, senza gate."
        GateMode.RF -> "Un file per QSO: parte sopra la soglia SQL, finisce dopo l'hang time."
        GateMode.VOICE -> "Un file per QSO: parte alla prima voce, anche sotto soglia; finisce dopo l'hang time senza voce."
        GateMode.RF_AND_VOICE -> "Parte quando c'è voce con segnale sopra soglia; finisce con lo squelch o dopo il tempo senza voce."
    }
}

/**
 * Selettore a quattro opzioni della modalità di un gate. Senza [voiceAllowed] (licenza assente)
 * le modalità con voce sono visibili ma non selezionabili.
 */
@Composable
internal fun GateModeSelector(
    title: String,
    target: GateTarget,
    selected: GateMode,
    voiceAllowed: Boolean,
    onSelect: (GateMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    note: String? = null,
    accentColor: Color = GrabberColors.Primary
) {
    val modes = GateMode.entries
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrabberColors.TextPrimary)
            if (note != null) Text(text = note, fontSize = 8.sp, color = GrabberColors.TextDisabled)
        }
        AppSegmentedControl(
            options = modes.map { it.label(target) },
            selectedIndex = modes.indexOf(selected),
            onSelectionChange = { onSelect(modes[it]) },
            size = ControlSize.Dense,
            accentColor = accentColor,
            fillWidth = true,
            isOptionEnabled = { index -> enabled && (voiceAllowed || !modes[index].usesVoice) },
            modifier = Modifier.fillMaxWidth()
        )
        Text(text = selected.explanation(target), fontSize = 9.sp, color = GrabberColors.TextSecondary)
    }
}
