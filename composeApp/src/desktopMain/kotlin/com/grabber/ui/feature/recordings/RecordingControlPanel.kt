package com.grabber.ui.feature.recordings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.grabber.model.ConnectionState
import com.grabber.recording.AudioRecordingFormat
import com.grabber.recording.RecordingInterval
import com.grabber.ui.designsystem.AppDivider
import com.grabber.ui.designsystem.AppPanel
import com.grabber.ui.designsystem.AppScrollableColumn
import com.grabber.ui.designsystem.AppSubtleDivider
import com.grabber.ui.designsystem.AppSwitch
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.DangerButton
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.GrabberShapes
import com.grabber.ui.designsystem.OutlinedButton
import com.grabber.ui.designsystem.PrimaryButton
import java.io.File
import java.util.Locale

private val RECORDING_INTERVAL_ROWS = listOf(
    listOf(
        RecordingInterval.CONTINUOUS to "Continuo",
        RecordingInterval.MINUTES_5 to "5 min",
        RecordingInterval.MINUTES_10 to "10 min"
    ),
    listOf(
        RecordingInterval.MINUTES_15 to "15 min",
        RecordingInterval.MINUTES_30 to "30 min",
        RecordingInterval.MINUTES_60 to "60 min"
    )
)

/** Avvio/arresto della registrazione, formato, intervallo dei segmenti e gate di registrazione. */
@Composable
internal fun RecordingControlPanel(
    controller: KiwiAppController,
    modifier: Modifier = Modifier
) {
    val isRecording by controller.isRecording.collectAsState()
    val isRecordingSquelchArmed by controller.isRecordingSquelchArmed.collectAsState()
    val recordSquelchEnabled by controller.squelchRecordEnabled.collectAsState()
    val vadRecordEnabled by controller.vadRecordEnabled.collectAsState()
    val recordingElapsed by controller.recordingElapsed.collectAsState()
    val currentFilePath by controller.currentFilePath.collectAsState()
    val recordedSegments by controller.recordedSegments.collectAsState()
    val recordingFormat by controller.recordingFormat.collectAsState()
    val connectionState by controller.connectionState.collectAsState()

    var selectedInterval by remember { mutableStateOf(RecordingInterval.CONTINUOUS) }

    AppPanel(
        modifier = modifier,
        title = "CONTROLLO REGISTRAZIONE",
        icon = Icons.Default.Mic,
        size = ControlSize.Compact,
        contentSpacing = 6.dp
    ) {
        AppDivider()
        AppScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f), contentSpacing = 8.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RecordButton(
                    isRecording = isRecording,
                    enabled = connectionState is ConnectionState.Connected,
                    vadRecordEnabled = vadRecordEnabled,
                    recordSquelchEnabled = recordSquelchEnabled,
                    onStart = { controller.startRecording(selectedInterval, recordingFormat) },
                    onStop = controller::stopRecording
                )
                LcdCounter(
                    label = "DURATA",
                    value = formatElapsed(recordingElapsed),
                    isActive = isRecording,
                    activeBorder = GrabberColors.Error,
                    activeValue = GrabberColors.SignalAmber
                )
                LcdCounter(
                    label = if (isRecordingSquelchArmed) "QSO" else "SEGMENTI",
                    value = "$recordedSegments",
                    isActive = isRecording,
                    activeBorder = GrabberColors.Primary,
                    activeValue = GrabberColors.Primary
                )
            }
            if (isRecording) CurrentFileNotice(currentFilePath)
            RecordingFormatSelector(
                selected = recordingFormat,
                isLocked = isRecording,
                onSelect = controller::setRecordingFormat
            )
            RecordingIntervalSelector(
                selected = selectedInterval,
                isLocked = isRecording,
                onSelect = { selectedInterval = it }
            )
            OutlinedButton(
                text = "Apri Cartella Registrazioni",
                onClick = { openRecordingsDirectory(controller.recordingsDirectory) },
                icon = Icons.Default.FolderOpen,
                size = ControlSize.Compact,
                contentColor = GrabberColors.TextSecondary,
                modifier = Modifier.fillMaxWidth()
            )
            AppSubtleDivider()
            Text(
                text = "ATTIVAZIONE GATE IN REGISTRAZIONE",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = GrabberColors.TextSecondary
            )
            RecorderSwitchSetting(
                title = "Squelch in Registrazione",
                description = "Pausa la registrazione durante i silenzi RF",
                checked = recordSquelchEnabled,
                onCheckedChange = controller::setSquelchRecordEnabled
            )
            AppSubtleDivider()
            RecorderSwitchSetting(
                title = "Voice-Gate in Registrazione",
                description = "Salva solo segmenti con voce convalidata (VAD)",
                checked = vadRecordEnabled,
                onCheckedChange = controller::setVadRecordEnabled
            )
        }
    }
}

private fun formatElapsed(elapsedSeconds: Long): String =
    String.format(Locale.US, "%02d:%02d:%02d", elapsedSeconds / 3600, (elapsedSeconds % 3600) / 60, elapsedSeconds % 60)

/** Il colore e l'etichetta di avvio indicano il gate attivo: VAD, squelch o registrazione continua. */
@Composable
private fun RowScope.RecordButton(
    isRecording: Boolean,
    enabled: Boolean,
    vadRecordEnabled: Boolean,
    recordSquelchEnabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val modifier = Modifier.weight(1.2f).fillMaxHeight()
    if (isRecording) {
        DangerButton(
            text = "ARRESTA REC",
            onClick = onStop,
            icon = Icons.Default.Stop,
            size = ControlSize.Compact,
            modifier = modifier
        )
        return
    }
    PrimaryButton(
        text = when {
            vadRecordEnabled -> "AVVIA REC (VAD)"
            recordSquelchEnabled -> "AVVIA REC (SQL)"
            else -> "AVVIA REC"
        },
        onClick = onStart,
        enabled = enabled,
        icon = Icons.Default.FiberManualRecord,
        size = ControlSize.Compact,
        containerColor = when {
            vadRecordEnabled -> GrabberColors.Warning
            recordSquelchEnabled -> GrabberColors.Primary
            else -> GrabberColors.Success
        },
        contentColor = Color.Black,
        modifier = modifier
    )
}

@Composable
private fun RowScope.LcdCounter(
    label: String,
    value: String,
    isActive: Boolean,
    activeBorder: Color,
    activeValue: Color
) {
    Surface(
        modifier = Modifier.weight(0.9f).fillMaxHeight(),
        color = GrabberColors.LcdBackground,
        shape = RoundedCornerShape(GrabberShapes.control),
        border = BorderStroke(1.dp, if (isActive) activeBorder.copy(alpha = 0.6f) else GrabberColors.Border)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = label, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = GrabberColors.TextDisabled)
            Text(
                text = value,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = if (isActive) activeValue else GrabberColors.TextDisabled
            )
        }
    }
}

/** Nome del file in scrittura, oppure attesa del primo QSO quando il gate è chiuso. */
@Composable
private fun CurrentFileNotice(filePath: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GrabberColors.SurfaceVariant,
        shape = RoundedCornerShape(GrabberShapes.control),
        border = BorderStroke(1.dp, GrabberColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.FiberManualRecord,
                contentDescription = null,
                tint = if (filePath != null) GrabberColors.Error else GrabberColors.Warning,
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = if (filePath != null) "In scrittura: ${File(filePath).name}" else "In ascolto silenzio... pronto a catturare QSO",
                fontSize = 9.sp,
                color = if (filePath != null) GrabberColors.Success else GrabberColors.Warning,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SelectorHeader(title: String, isLocked: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrabberColors.TextPrimary)
        if (isLocked) Text(text = "(bloccato in rec)", fontSize = 8.sp, color = GrabberColors.TextDisabled)
    }
}

@Composable
private fun RecordingFormatSelector(
    selected: AudioRecordingFormat,
    isLocked: Boolean,
    onSelect: (AudioRecordingFormat) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SelectorHeader("FORMATO AUDIO", isLocked)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AudioRecordingFormat.entries.forEach { format ->
                val isSelected = selected == format
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(GrabberShapes.control))
                        .clickable(enabled = !isLocked) { onSelect(format) },
                    color = if (isSelected) GrabberColors.Primary.copy(alpha = 0.2f) else GrabberColors.SurfaceVariant,
                    shape = RoundedCornerShape(GrabberShapes.control),
                    border = BorderStroke(1.dp, if (isSelected) GrabberColors.Primary else GrabberColors.Border)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 5.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = format.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) GrabberColors.Primary else GrabberColors.TextSecondary
                        )
                        Text(
                            text = if (format == AudioRecordingFormat.MP3) "Compresso LAME" else "PCM Standard",
                            fontSize = 8.sp,
                            color = if (isSelected) GrabberColors.TextPrimary else GrabberColors.TextDisabled
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingIntervalSelector(
    selected: RecordingInterval,
    isLocked: Boolean,
    onSelect: (RecordingInterval) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SelectorHeader("MODALITÀ / INTERVALLO SEGMENTI", isLocked)
        RECORDING_INTERVAL_ROWS.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (interval, label) ->
                    IntervalOption(
                        label = label,
                        isSelected = selected == interval,
                        enabled = !isLocked,
                        onClick = { onSelect(interval) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.IntervalOption(label: String, isSelected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val contentColor = when {
        !enabled -> GrabberColors.TextDisabled.copy(alpha = 0.5f)
        isSelected -> GrabberColors.Primary
        else -> GrabberColors.TextSecondary
    }
    Surface(
        modifier = Modifier
            .weight(1f)
            .height(28.dp)
            .clip(RoundedCornerShape(GrabberShapes.control))
            .clickable(enabled = enabled, onClick = onClick),
        color = when {
            !enabled -> GrabberColors.SurfaceVariant.copy(alpha = 0.5f)
            isSelected -> GrabberColors.Primary.copy(alpha = 0.25f)
            else -> GrabberColors.SurfaceVariant
        },
        shape = RoundedCornerShape(GrabberShapes.control),
        border = BorderStroke(1.dp, if (isSelected) GrabberColors.Primary else GrabberColors.Border)
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RecorderSwitchSetting(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrabberColors.TextPrimary)
            Text(
                text = description,
                fontSize = 8.sp,
                color = GrabberColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange, size = ControlSize.Compact)
    }
}
