package com.grabber.ui.feature.recordings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.model.TranscriptionStatus
import com.grabber.monitor.RecordingHistoryEntry
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.GrabberShapes
import com.grabber.ui.designsystem.SecondaryButton
import com.grabber.ui.designsystem.StatusBadge
import java.io.File

private const val TRANSCRIPT_PREVIEW_LIMIT = 130
private const val SEEK_STEP_MS = 10_000L

/** Riga dell'archivio: selezione, metadati, anteprima della trascrizione e azioni sul file. */
@Composable
internal fun RecordingRow(
    recording: RecordingHistoryEntry,
    isPlaying: Boolean,
    isPlaybackPaused: Boolean,
    isSelected: Boolean,
    canTranscribe: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onShowTranscription: () -> Unit,
    onTranscribe: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onTogglePause: () -> Unit,
    onSeek: (deltaMs: Long) -> Unit,
    onOpenFile: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isPlaying) GrabberColors.SurfaceElevated else GrabberColors.SurfaceVariant,
        shape = RoundedCornerShape(GrabberShapes.control),
        border = BorderStroke(
            1.dp,
            when {
                isPlaying -> GrabberColors.Success
                isSelected -> GrabberColors.Primary
                else -> GrabberColors.Border
            }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onSelectedChange,
                    modifier = Modifier.size(20.dp).padding(end = 4.dp),
                    colors = CheckboxDefaults.colors(checkedColor = GrabberColors.Primary, uncheckedColor = GrabberColors.TextDisabled)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    RecordingHeadline(recording, isPlaying)
                    TranscriptionSummary(recording)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SecondaryButton(
                    text = "Testo",
                    onClick = onShowTranscription,
                    enabled = !recording.transcriptionText.isNullOrBlank(),
                    icon = Icons.Default.Visibility,
                    size = ControlSize.Dense,
                    contentColor = GrabberColors.Primary
                )
                SecondaryButton(
                    text = when (recording.transcriptionStatus) {
                        TranscriptionStatus.FAILED -> "Riprova"
                        TranscriptionStatus.COMPLETED -> "Ritrascrivi"
                        else -> "Trascrivi"
                    },
                    onClick = onTranscribe,
                    enabled = canTranscribe,
                    icon = Icons.Default.Refresh,
                    size = ControlSize.Dense,
                    contentColor = GrabberColors.Primary
                )
                if (isPlaying) PlaybackControls(isPlaybackPaused, onTogglePause, onSeek)
                SecondaryButton(
                    text = if (isPlaying) "Stop" else "Ascolta",
                    onClick = if (isPlaying) onStop else onPlay,
                    enabled = recording.fileExists,
                    icon = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    size = ControlSize.Dense,
                    contentColor = if (isPlaying) GrabberColors.Error else GrabberColors.Success
                )
                SecondaryButton(
                    text = "Apri",
                    onClick = onOpenFile,
                    enabled = recording.fileExists,
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    size = ControlSize.Dense,
                    contentColor = GrabberColors.TextSecondary
                )
                SecondaryButton(
                    text = "Elimina",
                    onClick = onDelete,
                    icon = Icons.Default.Delete,
                    size = ControlSize.Dense,
                    contentColor = GrabberColors.Error
                )
            }
        }
    }
}

/** Formato, presenza su disco, nome del file e metadati della registrazione. */
@Composable
private fun RecordingHeadline(recording: RecordingHistoryEntry, isPlaying: Boolean) {
    val isMp3 = recording.filePath.endsWith(".mp3", ignoreCase = true)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        StatusBadge(
            text = if (isMp3) "MP3" else "WAV",
            color = if (isMp3) GrabberColors.Warning else GrabberColors.Primary,
            size = ControlSize.Compact
        )
        if (!recording.fileExists) {
            StatusBadge(text = "NON SU DISCO", color = GrabberColors.Warning, size = ControlSize.Compact, icon = Icons.Default.Warning)
        }
        Text(
            text = File(recording.filePath).name,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = when {
                !recording.fileExists -> GrabberColors.TextDisabled
                isPlaying -> GrabberColors.Success
                else -> GrabberColors.TextPrimary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = "·  ${recording.timestamp}  ·  ${recording.frequencyKhz} kHz  ·  ${recording.mode}  ·  ${recording.durationSeconds}s",
            fontSize = 9.sp,
            color = GrabberColors.TextSecondary,
            maxLines = 1
        )
    }
}

/** Stato della trascrizione Vosk: in corso, anteprima del testo, errore oppure assente. */
@Composable
private fun TranscriptionSummary(recording: RecordingHistoryEntry) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        when (recording.transcriptionStatus) {
            TranscriptionStatus.PENDING, TranscriptionStatus.PROCESSING -> {
                Icon(Icons.Default.HourglassTop, contentDescription = null, tint = GrabberColors.Warning, modifier = Modifier.size(11.dp))
                Text("Trascrizione vocale Vosk in corso...", fontSize = 9.sp, color = GrabberColors.Warning)
            }
            TranscriptionStatus.COMPLETED -> {
                val transcript = recording.transcriptionText.orEmpty()
                Text("Trascrizione:", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = GrabberColors.TextSecondary)
                SummaryText(
                    text = when {
                        transcript.isBlank() -> "Nessuna voce rilevata"
                        transcript.length > TRANSCRIPT_PREVIEW_LIMIT -> "${transcript.take(TRANSCRIPT_PREVIEW_LIMIT)}…"
                        else -> transcript
                    },
                    color = if (recording.hasVoice == true) GrabberColors.Success else GrabberColors.TextDisabled
                )
            }
            TranscriptionStatus.FAILED -> SummaryText(
                text = "Trascrizione non riuscita: ${recording.transcriptionError ?: "errore"}",
                color = GrabberColors.Error
            )
            TranscriptionStatus.NOT_REQUESTED -> SummaryText(
                text = "Nessuna trascrizione disponibile",
                color = GrabberColors.TextDisabled
            )
        }
    }
}

@Composable
private fun RowScope.SummaryText(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 9.sp,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun PlaybackControls(isPaused: Boolean, onTogglePause: () -> Unit, onSeek: (Long) -> Unit) {
    PlaybackIconButton(Icons.Default.FastRewind, "Indietro 10s", GrabberColors.Primary) { onSeek(-SEEK_STEP_MS) }
    PlaybackIconButton(
        icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
        description = if (isPaused) "Riprendi" else "Pausa",
        tint = GrabberColors.Success,
        onClick = onTogglePause
    )
    PlaybackIconButton(Icons.Default.FastForward, "Avanti 10s", GrabberColors.Primary) { onSeek(SEEK_STEP_MS) }
}

@Composable
private fun PlaybackIconButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(26.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(14.dp))
    }
}
