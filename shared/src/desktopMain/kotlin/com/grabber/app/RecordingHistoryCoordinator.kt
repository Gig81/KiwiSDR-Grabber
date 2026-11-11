package com.grabber.app

import com.grabber.model.TranscriptionStatus
import com.grabber.monitor.HistoryDatabase
import com.grabber.monitor.RecordingHistoryEntry
import com.grabber.monitor.UserLogEntry
import com.grabber.stt.OfflineTranscriptionQueue
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Storico di registrazioni e utenti connessi, con la coda di trascrizione offline.
 * Tutte le operazioni rispettano il gate di licenza: senza licenza lo storico risulta vuoto.
 */
internal class RecordingHistoryCoordinator(
    private val scope: CoroutineScope,
    private val database: HistoryDatabase,
    private val appSettings: AppSettings,
    private val hasAdvancedFeatures: () -> Boolean,
    private val onTranscriptionFinished: (recordingId: Long, status: TranscriptionStatus, text: String?) -> Unit
) {
    private companion object {
        const val HISTORY_LIMIT = 100
    }

    private val _userLogs = MutableStateFlow<List<UserLogEntry>>(emptyList())
    val userLogs: StateFlow<List<UserLogEntry>> = _userLogs.asStateFlow()

    private val _recordingHistory = MutableStateFlow<List<RecordingHistoryEntry>>(emptyList())
    val recordingHistory: StateFlow<List<RecordingHistoryEntry>> = _recordingHistory.asStateFlow()

    private var historySearchJob: Job? = null
    @Volatile private var recordingSearchQuery = ""

    private val transcriptionQueue = OfflineTranscriptionQueue(
        scope = scope,
        database = database,
        modelPathProvider = { appSettings.settings.value.sttModelPath },
        onTranscriptionUpdated = { recordingId, status, text ->
            refresh()
            if (status.isFinished) onTranscriptionFinished(recordingId, status, text)
        }
    )

    val isTranscriptionConfigured: Boolean get() = appSettings.settings.value.sttModelPath.isNotBlank()

    fun refresh() {
        historySearchJob?.cancel()
        if (!hasAdvancedFeatures()) {
            clearVisibleHistory()
            return
        }
        historySearchJob = scope.launch(Dispatchers.IO + CoroutineName("aggiornamento storico")) {
            loadHistory()
        }
    }

    fun search(query: String) {
        recordingSearchQuery = query
        historySearchJob?.cancel()
        if (!hasAdvancedFeatures()) {
            _recordingHistory.value = emptyList()
            return
        }
        historySearchJob = scope.launch(Dispatchers.IO + CoroutineName("ricerca registrazioni")) {
            _recordingHistory.value = queryRecordings(query)
        }
    }

    /** Archivia un segmento registrato; la trascrizione va richiesta a parte con [enqueueTranscription]. */
    fun archiveRecording(
        filePath: String,
        durationSec: Long,
        frequencyKhz: Double,
        mode: String,
        transcriptionRequested: Boolean
    ): Long {
        val status = if (transcriptionRequested) TranscriptionStatus.PENDING else TranscriptionStatus.NOT_REQUESTED
        return database.insertRecording(filePath, durationSec, frequencyKhz, mode, transcriptionStatus = status)
    }

    fun enqueueTranscription(recordingId: Long, filePath: String) = transcriptionQueue.enqueue(recordingId, filePath)

    fun requestTranscription(recordingId: Long, filePath: String) {
        if (!hasAdvancedFeatures()) return
        scope.launch(Dispatchers.IO + CoroutineName("richiesta trascrizione")) {
            database.updateTranscription(recordingId, TranscriptionStatus.PENDING)
            transcriptionQueue.enqueue(recordingId, filePath)
            refresh()
        }
    }

    /** Riprende le trascrizioni rimaste in sospeso alla chiusura precedente. */
    fun resumePendingTranscriptions() {
        if (!hasAdvancedFeatures() || !isTranscriptionConfigured) return
        scope.launch(Dispatchers.IO + CoroutineName("ripresa trascrizioni")) {
            database.getPendingTranscriptions().forEach { (id, path) ->
                if (File(path).isFile) {
                    transcriptionQueue.enqueue(id, path)
                } else {
                    database.updateTranscription(id, TranscriptionStatus.FAILED, error = "File audio non trovato")
                }
            }
        }
    }

    fun logConnectedUsers(
        activeUsers: Int,
        maxUsers: Int,
        stationName: String,
        frequencyKhz: Double,
        mode: String,
        userNames: String
    ) {
        database.insertUserLog(activeUsers, maxUsers, stationName, frequencyKhz, mode, userNames)
        refresh()
    }

    fun deleteRecording(id: Long, filePath: String, deleteFileFromDisk: Boolean) =
        mutate { database.deleteRecording(id, filePath, deleteFileFromDisk) }

    fun deleteRecordings(items: List<Pair<Long, String>>, deleteFilesFromDisk: Boolean) =
        mutate { database.deleteRecordings(items, deleteFilesFromDisk) }

    fun deleteUserLogs(ids: List<Long>) = mutate { database.deleteUserLogs(ids) }

    fun clearAllUserLogs() = mutate { database.clearAllUserLogs() }

    fun closeTranscriptionQueue() = transcriptionQueue.close()

    private fun mutate(mutation: () -> Unit) {
        if (!hasAdvancedFeatures()) return
        scope.launch(Dispatchers.IO + CoroutineName("modifica storico")) {
            mutation()
            loadHistory()
        }
    }

    private fun loadHistory() {
        if (!hasAdvancedFeatures()) {
            clearVisibleHistory()
            return
        }
        _userLogs.value = database.getRecentUserLogs(HISTORY_LIMIT)
        _recordingHistory.value = queryRecordings(recordingSearchQuery)
    }

    private fun queryRecordings(query: String): List<RecordingHistoryEntry> =
        if (query.isBlank()) {
            database.getRecentRecordings(HISTORY_LIMIT)
        } else {
            database.searchRecordings(query, HISTORY_LIMIT)
        }

    private fun clearVisibleHistory() {
        _userLogs.value = emptyList()
        _recordingHistory.value = emptyList()
    }
}
