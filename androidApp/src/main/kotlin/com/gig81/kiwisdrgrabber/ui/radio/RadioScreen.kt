package com.gig81.kiwisdrgrabber.ui.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gig81.kiwisdrgrabber.ui.components.Panel
import com.gig81.kiwisdrgrabber.ui.components.formatBytes
import com.gig81.kiwisdrgrabber.ui.components.formatDuration
import com.gig81.kiwisdrgrabber.ui.components.formatFrequencyKhz
import com.gig81.kiwisdrgrabber.ui.theme.KiwiColors
import com.gig81.kiwisdrgrabber.ui.theme.NumericStyle
import com.grabber.audio.squelch.SMeterUtils
import com.grabber.mobile.FREQUENCY_STEPS_KHZ
import com.grabber.mobile.MobileAppController
import com.grabber.model.ConnectionState
import com.grabber.model.ModulationMode
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

@Composable
fun RadioScreen(controller: MobileAppController, onConnect: () -> Unit) {
    val connection by controller.connectionState.collectAsStateWithLifecycle()
    val settings by controller.settings.collectAsStateWithLifecycle()
    val frequency by controller.currentFrequencyKhz.collectAsStateWithLifecycle()
    val mode by controller.currentMode.collectAsStateWithLifecycle()
    var showFrequencyDialog by remember { mutableStateOf(false) }
    var showFavoriteDialog by remember { mutableStateOf(false) }

    Column(
        // Su tablet il contenuto resta centrato con una larghezza leggibile.
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).wrapContentWidth().widthIn(max = 640.dp).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ConnectionPanel(connection, settings.serverAddress, onConnect, controller::disconnect)
        FrequencyDisplay(
            frequencyKhz = frequency,
            mode = mode,
            rssi = controller.rssi,
            onEditFrequency = { showFrequencyDialog = true },
            onAddFavorite = { showFavoriteDialog = true }
        )
        TuningPanel(
            stepKhz = settings.frequencyStepKhz,
            mode = mode,
            onStep = controller::stepFrequency,
            onStepSize = controller::setFrequencyStep,
            onMode = controller::setMode
        )
        AudioPanel(controller)
        RecordingPanel(controller)
    }

    if (showFrequencyDialog) {
        FrequencyInputDialog(
            initialKhz = frequency,
            onDismiss = { showFrequencyDialog = false },
            onConfirm = {
                showFrequencyDialog = false
                controller.tune(it)
            }
        )
    }
    if (showFavoriteDialog) {
        NameInputDialog(
            title = "Aggiungi ai preferiti",
            initial = "${mode.displayName} ${formatFrequencyKhz(frequency)} kHz",
            onDismiss = { showFavoriteDialog = false },
            onConfirm = {
                showFavoriteDialog = false
                controller.addCurrentAsFavorite(it)
            }
        )
    }
}

@Composable
private fun ConnectionPanel(
    state: ConnectionState,
    serverAddress: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val active = state is ConnectionState.Connected || state is ConnectionState.Connecting || state is ConnectionState.Reconnecting
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ricevitore", style = MaterialTheme.typography.labelMedium, color = KiwiColors.TextSecondary)
                Text(serverAddress, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                val detail = when (state) {
                    is ConnectionState.Connecting -> state.message
                    is ConnectionState.Reconnecting -> "Riconnessione tra ${state.secondsLeft} s (tentativo ${state.attempt})"
                    is ConnectionState.Error -> state.message
                    else -> null
                }
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = KiwiColors.TextSecondary)
                }
            }
            Button(
                onClick = if (active) onDisconnect else onConnect,
                colors = if (active) ButtonDefaults.buttonColors(containerColor = KiwiColors.Error) else ButtonDefaults.buttonColors()
            ) {
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (active) "Disconnetti" else "Connetti")
            }
        }
    }
}

@Composable
private fun FrequencyDisplay(
    frequencyKhz: Double,
    mode: ModulationMode,
    rssi: StateFlow<Double>,
    onEditFrequency: () -> Unit,
    onAddFavorite: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KiwiColors.LcdBackground)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f)
                    .clickable(onClickLabel = "Inserisci frequenza", onClick = onEditFrequency)
            ) {
                Text("Frequenza (kHz) · tocca per inserirla", style = MaterialTheme.typography.labelSmall, color = KiwiColors.TextSecondary)
                Text(
                    formatFrequencyKhz(frequencyKhz),
                    style = NumericStyle.copy(fontSize = 40.sp),
                    color = KiwiColors.LcdPrimary,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(mode.displayName, style = NumericStyle.copy(fontSize = 20.sp), color = KiwiColors.Secondary)
                IconButton(onClick = onAddFavorite) {
                    Icon(Icons.Filled.Star, contentDescription = "Aggiungi ai preferiti", tint = KiwiColors.SignalAmber)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SMeter(rssi)
    }
}

@Composable
private fun SMeter(rssi: StateFlow<Double>) {
    // Raccolta locale: gli aggiornamenti frequenti dell'S-meter non ricompongono tutta la schermata.
    val rssiDbm by rssi.collectAsStateWithLifecycle()
    val fraction = SMeterUtils.dbmToNormalized(rssiDbm)
    val label = "${SMeterUtils.dbmToSUnits(rssiDbm)} · ${String.format(Locale.ITALY, "%.0f", rssiDbm)} dBm"
    Column(Modifier.semantics { contentDescription = "S-meter $label" }) {
        Box(
            Modifier.fillMaxWidth().height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(KiwiColors.SurfaceVariant)
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(fraction)
                    .background(if (rssiDbm > SMeterUtils.S9_DBM) KiwiColors.SignalAmber else KiwiColors.SignalGreen)
            )
        }
        Text(label, style = NumericStyle.copy(fontSize = 13.sp), color = KiwiColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TuningPanel(
    stepKhz: Double,
    mode: ModulationMode,
    onStep: (Int) -> Unit,
    onStepSize: (Double) -> Unit,
    onMode: (ModulationMode) -> Unit
) {
    Panel(title = "Sintonia") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick = { onStep(-1) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Frequenza giù di un passo")
            }
            Text(
                "Passo ${formatStep(stepKhz)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = KiwiColors.TextPrimary
            )
            FilledTonalIconButton(onClick = { onStep(1) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Frequenza su di un passo")
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FREQUENCY_STEPS_KHZ.forEach { step ->
                FilterChip(selected = step == stepKhz, onClick = { onStepSize(step) }, label = { Text(formatStep(step)) })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModulationMode.entries.forEach { item ->
                FilterChip(selected = item == mode, onClick = { onMode(item) }, label = { Text(item.displayName) })
            }
        }
    }
}

@Composable
private fun AudioPanel(controller: MobileAppController) {
    val volume by controller.volume.collectAsStateWithLifecycle()
    val muted by controller.isMuted.collectAsStateWithLifecycle()
    val squelchEnabled by controller.squelchEnabled.collectAsStateWithLifecycle()
    val threshold by controller.squelchThresholdDbm.collectAsStateWithLifecycle()
    val gateOpen by controller.squelchGateOpen.collectAsStateWithLifecycle()

    Panel(title = "Audio") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = controller::toggleMute) {
                Icon(
                    if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (muted) "Riattiva audio" else "Silenzia",
                    tint = if (muted) KiwiColors.Error else KiwiColors.TextPrimary
                )
            }
            Slider(
                value = volume,
                onValueChange = controller::setVolume,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Volume" }
            )
            Text("${(volume * 100).toInt()}%", style = NumericStyle, modifier = Modifier.width(48.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Squelch RF", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (!squelchEnabled) "Disattivato" else if (gateOpen) "Aperto: segnale sopra soglia" else "Chiuso: audio silenziato",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (squelchEnabled && gateOpen) KiwiColors.SignalGreen else KiwiColors.TextSecondary
                )
            }
            Switch(checked = squelchEnabled, onCheckedChange = controller::setSquelchEnabled)
        }
        if (squelchEnabled) {
            Text(
                "Soglia ${SMeterUtils.dbmToSUnits(threshold)} (${String.format(Locale.ITALY, "%.0f", threshold)} dBm)",
                style = MaterialTheme.typography.bodySmall,
                color = KiwiColors.TextSecondary
            )
            Slider(
                value = SMeterUtils.dbmToNormalized(threshold),
                onValueChange = { controller.setSquelchThreshold(SMeterUtils.normalizedToDbm(it)) },
                modifier = Modifier.semantics { contentDescription = "Soglia squelch" }
            )
        }
    }
}

@Composable
private fun RecordingPanel(controller: MobileAppController) {
    val activated by controller.isActivated.collectAsStateWithLifecycle()
    val recording by controller.isRecording.collectAsStateWithLifecycle()
    val elapsed by controller.recordingElapsedSeconds.collectAsStateWithLifecycle()
    val bytes by controller.recordingBytes.collectAsStateWithLifecycle()
    val fileName by controller.recordingFileName.collectAsStateWithLifecycle()

    Panel(title = "Registrazione WAV") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (recording) {
                    Text(formatDuration(elapsed), style = NumericStyle.copy(fontSize = 22.sp), color = KiwiColors.Error)
                    Text("${formatBytes(bytes)} · ${fileName.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = KiwiColors.TextSecondary)
                } else {
                    Text(
                        if (activated) "I file vengono salvati in Registrazioni/KiwiGrabber (Musica/KiwiGrabber su Android 10-11)."
                        else "Disponibile nella versione attivata (gratuita).",
                        style = MaterialTheme.typography.bodySmall,
                        color = KiwiColors.TextSecondary
                    )
                }
            }
            if (recording) {
                OutlinedButton(onClick = controller::stopRecording) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Stop")
                }
            } else {
                Button(
                    onClick = controller::startRecording,
                    colors = ButtonDefaults.buttonColors(containerColor = KiwiColors.Error)
                ) {
                    Icon(if (activated) Icons.Filled.FiberManualRecord else Icons.Filled.Lock, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Registra")
                }
            }
        }
    }
}

@Composable
private fun FrequencyInputDialog(initialKhz: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf(String.format(Locale.US, "%.2f", initialKhz)) }
    val parsed = text.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..30000.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Frequenza in kHz") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = parsed == null,
                supportingText = { Text("Da 0 a 30000 kHz") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = { parsed?.let(onConfirm) }) { Text("Sintonizza") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@Composable
fun NameInputDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Nome") }) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

private fun formatStep(stepKhz: Double): String =
    if (stepKhz < 1.0) "${(stepKhz * 1000).toInt()} Hz" else "${stepKhz.toInt()} kHz"
