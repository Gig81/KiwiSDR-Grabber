package com.grabber.ui.feature.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.audio.squelch.SquelchController
import com.grabber.recording.RecordingNoiseFilterConfig
import com.grabber.ui.designsystem.AppSlider
import com.grabber.ui.designsystem.CompactButton
import com.grabber.ui.designsystem.CompactTextField
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.GrabberColors
import java.util.Locale
import kotlin.math.roundToInt

/** Chiusura anticipata del segmento "RF e voce" dopo N secondi senza voce. */
@Composable
internal fun VoiceSilenceCloseSetting(
    enabled: Boolean,
    seconds: Double,
    onChange: (enabled: Boolean, seconds: Double) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        RecorderSwitchSetting(
            title = "Chiudi senza voce",
            description = "Chiude il QSO dopo il tempo indicato senza voce, anche con squelch aperto",
            checked = enabled,
            onCheckedChange = { onChange(it, seconds) }
        )
        if (enabled) {
            AppSlider(
                label = "Tempo senza voce",
                valueText = formatSeconds(seconds),
                value = seconds.toFloat(),
                valueRange = SquelchController.MIN_VOICE_SILENCE_CLOSE_SECONDS.toFloat()..
                    SquelchController.MAX_VOICE_SILENCE_CLOSE_SECONDS.toFloat(),
                steps = 28,
                minLabel = "1s",
                maxLabel = "30s",
                accentColor = GrabberColors.Warning,
                size = ControlSize.Compact,
                onValueChange = { onChange(true, it.roundToInt().toDouble()) },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

/**
 * Filtro dei segmenti QSO brevi o trascritti come rumore. I segmenti scartati vengono cancellati.
 * Vale solo per la registrazione con un gate attivo; i controlli sul testo richiedono Vosk.
 */
@Composable
internal fun RecordingNoiseFilterSection(
    config: RecordingNoiseFilterConfig,
    isTranscriptionConfigured: Boolean,
    gateActive: Boolean,
    onChange: (RecordingNoiseFilterConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "FILTRO ANTI-RUMORE (SEGMENTI QSO)",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = GrabberColors.TextSecondary
        )
        Text(
            text = if (gateActive) {
                "I segmenti giudicati rumore vengono cancellati dal disco e non entrano nell'archivio."
            } else {
                "Inattivo con la registrazione continua: si applica solo ai segmenti di un gate."
            },
            fontSize = 9.sp,
            color = if (gateActive) GrabberColors.TextSecondary else GrabberColors.TextDisabled
        )

        RecorderSwitchSetting(
            title = "Durata minima",
            description = "Scarta i QSO più brevi (senza contare l'hang time)",
            checked = config.minDurationEnabled,
            onCheckedChange = { onChange(config.copy(minDurationEnabled = it)) }
        )
        if (config.minDurationEnabled) {
            AppSlider(
                label = "Durata minima",
                valueText = formatSeconds(config.minDurationSeconds),
                value = config.minDurationSeconds.toFloat(),
                valueRange = RecordingNoiseFilterConfig.MIN_DURATION_RANGE.start.toFloat()..
                    RecordingNoiseFilterConfig.MIN_DURATION_RANGE.endInclusive.toFloat(),
                steps = 18,
                minLabel = "0.5s",
                maxLabel = "10s",
                accentColor = GrabberColors.Primary,
                size = ControlSize.Compact,
                onValueChange = { onChange(config.copy(minDurationSeconds = (it * 2).roundToInt() / 2.0)) },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        if (!isTranscriptionConfigured) {
            Text(
                text = "Trascrizione Vosk non configurata: i controlli sul testo restano inattivi.",
                fontSize = 9.sp,
                color = GrabberColors.Warning,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        RecorderSwitchSetting(
            title = "Parole rumore",
            description = "Scarta se la trascrizione è vuota o contiene solo queste parole",
            checked = config.noiseWordsEnabled,
            onCheckedChange = { onChange(config.copy(noiseWordsEnabled = it)) }
        )
        if (config.noiseWordsEnabled) {
            NoiseWordsField(
                words = config.noiseWords,
                onApply = { onChange(config.copy(noiseWords = it)) }
            )
        }

        RecorderSwitchSetting(
            title = "Confidenza minima",
            description = "Scarta se la confidenza media di Vosk è sotto la soglia",
            checked = config.minConfidenceEnabled,
            onCheckedChange = { onChange(config.copy(minConfidenceEnabled = it)) }
        )
        if (config.minConfidenceEnabled) {
            AppSlider(
                label = "Soglia confidenza",
                valueText = "${(config.minConfidence * 100).roundToInt()}%",
                value = config.minConfidence.toFloat(),
                valueRange = RecordingNoiseFilterConfig.MIN_CONFIDENCE_RANGE.start.toFloat()..
                    RecordingNoiseFilterConfig.MIN_CONFIDENCE_RANGE.endInclusive.toFloat(),
                steps = 15,
                minLabel = "10%",
                maxLabel = "90%",
                accentColor = GrabberColors.Success,
                size = ControlSize.Compact,
                onValueChange = { onChange(config.copy(minConfidence = (it * 20).roundToInt() / 20.0)) },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun NoiseWordsField(words: String, onApply: (String) -> Unit) {
    var draft by remember(words) { mutableStateOf(words) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = "es. uso, eh",
            fontSize = 10.sp,
            modifier = Modifier.weight(1f).height(26.dp)
        )
        CompactButton(
            text = "Applica",
            onClick = { onApply(draft) },
            enabled = draft.trim() != words.trim()
        )
    }
}

private fun formatSeconds(seconds: Double): String = String.format(Locale.US, "%.1f s", seconds)
