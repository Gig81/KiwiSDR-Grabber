package com.grabber.ui.feature.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.grabber.monitor.RecordingHistoryEntry
import com.grabber.ui.designsystem.AppConfirmDialog
import com.grabber.ui.designsystem.AppDivider
import com.grabber.ui.designsystem.AppEmptyState
import com.grabber.ui.designsystem.AppPanel
import com.grabber.ui.designsystem.AppSearchField
import com.grabber.ui.designsystem.AppVerticalScrollbar
import com.grabber.ui.designsystem.ControlSize
import com.grabber.ui.designsystem.DangerButton
import com.grabber.ui.designsystem.GrabberColors
import com.grabber.ui.designsystem.OutlinedButton
import com.grabber.ui.designsystem.PrimaryButton
import com.grabber.ui.designsystem.StatusBadge
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/** Archivio delle registrazioni: ricerca, selezione multipla, riproduzione, trascrizione ed eliminazione. */
@Composable
internal fun RecordingArchivePanel(
    controller: KiwiAppController,
    modifier: Modifier = Modifier
) {
    val recordings by controller.recordingHistory.collectAsState()
    val settings by controller.settings.collectAsState()
    val currentlyPlayingFile by controller.currentlyPlayingFile.collectAsState()
    val isFilePlaying by controller.isFilePlaying.collectAsState()
    val isFilePlaybackPaused by controller.isFilePlaybackPaused.collectAsState()

    LaunchedEffect(Unit) {
        controller.refreshHistory()
    }

    var searchQuery by remember { mutableStateOf("") }
    var transcriptionToShow by remember { mutableStateOf<Pair<String, String>?>(null) }
    var itemToDelete by remember { mutableStateOf<RecordingHistoryEntry?>(null) }
    var selectedRecordingIds by remember { mutableStateOf(setOf<Long>()) }
    var showMultiDeleteDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    transcriptionToShow?.let { (fileName, transcription) ->
        TranscriptionDialog(fileName, transcription, onDismiss = { transcriptionToShow = null })
    }

    itemToDelete?.let { target ->
        AppConfirmDialog(
            title = "Elimina Registrazione",
            icon = Icons.Default.Delete,
            message = "Vuoi eliminare definitivamente '${File(target.filePath).name}'?\n\n" +
                if (target.fileExists) {
                    "Il file audio verrà cancellato dal disco locale e la voce rimossa dallo storico SQLite."
                } else {
                    "La voce verrà cancellata dallo storico SQLite (il file non è presente su disco)."
                },
            confirmText = "Elimina",
            onConfirm = {
                controller.deleteRecording(target.id, target.filePath, true)
                selectedRecordingIds = selectedRecordingIds - target.id
                itemToDelete = null
            },
            onDismiss = { itemToDelete = null }
        )
    }

    if (showMultiDeleteDialog && selectedRecordingIds.isNotEmpty()) {
        AppConfirmDialog(
            title = "Elimina Registrazioni Selezionate",
            icon = Icons.Default.Delete,
            message = "Vuoi eliminare definitivamente le ${selectedRecordingIds.size} registrazioni selezionate?\n\n" +
                "I file audio presenti verranno cancellati dal disco locale e tutte le voci rimosse dal database SQLite.",
            confirmText = "Elimina Tutte (${selectedRecordingIds.size})",
            onConfirm = {
                val targets = recordings.filter { it.id in selectedRecordingIds }.map { it.id to it.filePath }
                controller.deleteRecordings(targets, true)
                selectedRecordingIds = emptySet()
                showMultiDeleteDialog = false
            },
            onDismiss = { showMultiDeleteDialog = false }
        )
    }

    AppPanel(
        modifier = modifier,
        title = "ARCHIVIO REGISTRAZIONI",
        icon = Icons.Default.FolderShared,
        size = ControlSize.Compact,
        contentSpacing = 6.dp,
        titleAccessory = { Text(text = "(${recordings.size} salvate)", fontSize = 10.sp, color = GrabberColors.TextSecondary) },
        actions = {
            if (isFilePlaying) {
                val playingName = currentlyPlayingFile?.let { File(it).name }.orEmpty()
                StatusBadge(
                    text = if (isFilePlaybackPaused) "In pausa: $playingName" else "In riproduzione: $playingName",
                    color = if (isFilePlaybackPaused) GrabberColors.Warning else GrabberColors.Success,
                    icon = if (isFilePlaybackPaused) Icons.Default.Pause else Icons.Default.PlayArrow
                )
            }
        }
    ) {
        AppDivider()
        ArchiveToolbar(
            recordings = recordings,
            searchQuery = searchQuery,
            onSearchQueryChange = {
                searchQuery = it
                controller.searchRecordingHistory(it)
            },
            selectedRecordingIds = selectedRecordingIds,
            onSelectionChange = { selectedRecordingIds = it },
            onDeleteSelected = { showMultiDeleteDialog = true },
            onOpenFolder = { openRecordingsDirectory(controller.recordingsDirectory) },
            onRefresh = controller::refreshHistory
        )
        if (recordings.isEmpty()) {
            AppEmptyState(
                icon = Icons.Default.FolderOpen,
                message = if (searchQuery.isBlank()) {
                    "Nessuna registrazione salvata nell'archivio."
                } else {
                    "Nessuna registrazione corrisponde alla ricerca."
                }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(recordings, key = { it.id }) { rec ->
                        val isSelected = rec.id in selectedRecordingIds
                        RecordingRow(
                            recording = rec,
                            isPlaying = isFilePlaying && currentlyPlayingFile == rec.filePath,
                            isPlaybackPaused = isFilePlaybackPaused,
                            isSelected = isSelected,
                            canTranscribe = settings.sttModelPath.isNotBlank() && rec.fileExists,
                            onSelectedChange = { checked ->
                                selectedRecordingIds = if (checked) selectedRecordingIds + rec.id else selectedRecordingIds - rec.id
                            },
                            onShowTranscription = {
                                transcriptionToShow = File(rec.filePath).name to rec.transcriptionText.orEmpty()
                            },
                            onTranscribe = { controller.transcribeRecording(rec.id, rec.filePath) },
                            onPlay = { controller.playRecordedFile(rec.filePath) },
                            onStop = controller::stopFilePlayback,
                            onTogglePause = {
                                if (isFilePlaybackPaused) controller.resumeFilePlayback() else controller.pauseFilePlayback()
                            },
                            onSeek = controller::seekFilePlayback,
                            onOpenFile = { openWithDesktop(File(rec.filePath)) },
                            onDelete = { itemToDelete = rec }
                        )
                    }
                }
                AppVerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun ArchiveToolbar(
    recordings: List<RecordingHistoryEntry>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedRecordingIds: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    onDeleteSelected: () -> Unit,
    onOpenFolder: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AppSearchField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = "Cerca nelle trascrizioni o nei file...",
            modifier = Modifier.weight(1f)
        )
        if (recordings.isNotEmpty()) {
            val allSelected = selectedRecordingIds.size == recordings.size
            OutlinedButton(
                text = if (allSelected) "Deseleziona Tutti" else "Seleziona Tutti",
                onClick = { onSelectionChange(if (allSelected) emptySet() else recordings.map { it.id }.toSet()) },
                size = ControlSize.Dense,
                contentColor = GrabberColors.Primary
            )
            val missingCount = remember(recordings) { recordings.count { !it.fileExists } }
            if (missingCount > 0) {
                OutlinedButton(
                    text = "Mancanti ($missingCount)",
                    onClick = { onSelectionChange(recordings.filter { !it.fileExists }.map { it.id }.toSet()) },
                    icon = Icons.Default.Warning,
                    size = ControlSize.Dense,
                    contentColor = GrabberColors.Warning
                )
            }
            if (selectedRecordingIds.isNotEmpty()) {
                DangerButton(
                    text = "Elimina (${selectedRecordingIds.size})",
                    onClick = onDeleteSelected,
                    icon = Icons.Default.Delete,
                    size = ControlSize.Dense
                )
            }
        }
        OutlinedButton(
            text = "Apri Cartella",
            onClick = onOpenFolder,
            icon = Icons.Default.FolderOpen,
            size = ControlSize.Dense,
            contentColor = GrabberColors.Primary
        )
        IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = "Aggiorna archivio", tint = GrabberColors.Primary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TranscriptionDialog(fileName: String, transcription: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = GrabberColors.Primary, modifier = Modifier.size(18.dp))
                Text("Trascrizione Vocale — $fileName", color = GrabberColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                Text(
                    text = transcription,
                    color = GrabberColors.TextPrimary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    text = "Copia Testo",
                    onClick = {
                        val selection = StringSelection(transcription)
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                    },
                    icon = Icons.Default.ContentCopy,
                    contentColor = GrabberColors.Primary
                )
                PrimaryButton(text = "Chiudi", onClick = onDismiss)
            }
        },
        containerColor = GrabberColors.Surface,
        textContentColor = GrabberColors.TextPrimary
    )
}
