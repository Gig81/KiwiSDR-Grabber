package com.grabber.app

import com.grabber.alerting.TelegramAlertService
import com.grabber.audio.DesktopAudioPlayer
import com.grabber.audio.FileAudioPlayer
import com.grabber.audio.squelch.SquelchController
import com.grabber.kiwi.KiwiClient
import com.grabber.license.LicenseManager
import com.grabber.license.MachineIdUtil
import com.grabber.model.ConnectionState
import com.grabber.model.ModulationMode
import com.grabber.model.TranscriptionStatus
import com.grabber.monitor.HistoryDatabase
import com.grabber.monitor.RecordingHistoryEntry
import com.grabber.monitor.StationMonitor
import com.grabber.monitor.UserLogEntry
import com.grabber.recording.AudioRecorder
import com.grabber.recording.AudioRecordingFormat
import com.grabber.recording.RecordingInterval
import com.grabber.waterfall.WaterfallBuffer
import com.grabber.waterfall.WaterfallClient
import com.grabber.waterfall.WaterfallPaletteType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

private const val TAG = "KiwiAppController"
private const val MAX_FREQUENCY_KHZ = 30000.0
private const val DEFAULT_MAX_USERS = 4
private const val RECORDING_RESUME_DELAY_MS = 600L

/**
 * Registra e segnala le eccezioni non gestite delle coroutine del controller
 * (storico, trascrizioni, notifiche).
 */
private fun uncaughtErrorLogger() = CoroutineExceptionHandler { context, error ->
    val coroutineName = context[CoroutineName]?.name ?: "coroutine"
    AppErrorReporter.report("coroutine '$coroutineName'", error)
}

/**
 * Punto di accesso unico della UI all'applicazione.
 *
 * Collega client SDR, pipeline audio, registrazione e impostazioni, e delega waterfall,
 * storico e notifiche Telegram ai rispettivi coordinatori. La UI osserva solo i `StateFlow`
 * esposti qui e non accede direttamente ai servizi.
 */
class KiwiAppController(
    private val client: KiwiClient = KiwiClient(),
    private val player: DesktopAudioPlayer = DesktopAudioPlayer(),
    private val filePlayer: FileAudioPlayer = FileAudioPlayer(),
    private val recorder: AudioRecorder = AudioRecorder(),
    private val database: HistoryDatabase = HistoryDatabase(),
    private val monitor: StationMonitor = StationMonitor(),
    private val squelch: SquelchController = SquelchController(),
    private val waterfallClient: WaterfallClient = WaterfallClient(host = client.host, port = client.port),
    /** Buffer di pixel letto direttamente dal renderer del waterfall. */
    val waterfallBuffer: WaterfallBuffer = WaterfallBuffer(width = 1024, height = 250)
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + uncaughtErrorLogger())

    private val appDataDir = File(System.getProperty("user.home"), ".kiwigrabber").also { it.mkdirs() }
    private val appSettings = AppSettings(appDataDir)

    private val _connectionNotifications = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val connectionNotifications: SharedFlow<String> = _connectionNotifications.asSharedFlow()

    private val waterfall = WaterfallCoordinator(scope, client, waterfallClient, waterfallBuffer, appSettings)

    private val alerts = QsoAlertCoordinator(
        scope = scope,
        appSettings = appSettings,
        hasAdvancedFeatures = ::hasAdvancedFeatures,
        currentTuning = { client.currentFrequencyKhz.value to client.currentMode.value.displayName },
        receiverAddress = { serverEndpoint },
        onDeliveryFailure = { _connectionNotifications.tryEmit(it) }
    )

    private val history = RecordingHistoryCoordinator(
        scope = scope,
        database = database,
        appSettings = appSettings,
        hasAdvancedFeatures = ::hasAdvancedFeatures,
        onTranscriptionFinished = alerts::onTranscriptionFinished
    )

    val settings: StateFlow<SettingsData> = appSettings.settings
    val serverEndpoint: String get() = "${client.host}:${client.port}"
    val recordingsDirectory: File get() = recorder.baseDirectory
    val machineId: String get() = MachineIdUtil.getMachineId()

    val connectionState = client.connectionState
    val currentFrequencyKhz = client.currentFrequencyKhz
    val currentMode = client.currentMode
    val rssi = client.rssi
    val activeUsers = client.activeUsers
    val serverDxLabels = client.serverDxLabels
    val stationInfo = monitor.stationInfo

    val audioLevel = player.audioLevel
    val volume = player.volume
    val isMuted = player.isMuted

    val isRecording = recorder.isRecording
    val isRecordingSquelchArmed = recorder.isSquelchArmed
    val recordingElapsed = recorder.elapsedSeconds
    val currentFilePath = recorder.currentFilePath
    val recordedSegments = recorder.recordedSegments
    val recordingFormat = recorder.format

    val squelchState = squelch.state
    val squelchGateOpen = squelch.isGateOpen
    val squelchHangTimeRemaining = squelch.hangTimeRemaining
    val squelchThresholdDbm = squelch.thresholdDbm
    val squelchListenEnabled = squelch.listenSquelchEnabled
    val squelchRecordEnabled = squelch.recordSquelchEnabled

    val vadConfidence = squelch.vadConfidence
    val isVoiceDetected = squelch.isVoiceDetected
    val isQrnDetected = squelch.isQrnDetected
    val vadListenEnabled = squelch.vadListenEnabled
    val vadRecordEnabled = squelch.vadRecordEnabled
    val lastVadResult = squelch.lastVadResult

    val currentlyPlayingFile = filePlayer.currentlyPlayingPath
    val isFilePlaying = filePlayer.isPlaying
    val isFilePlaybackPaused = filePlayer.isPaused

    val waterfallState = waterfallClient.connectionState
    val waterfallZoom = waterfallClient.currentZoom
    val waterfallCenterFreqKhz = waterfallClient.centerFrequencyKhz
    val waterfallMinDb = waterfallClient.minDb
    val waterfallMaxDb = waterfallClient.maxDb
    val waterfallSpeed = waterfallClient.wfSpeed
    val isWaterfallPaused = waterfallClient.isPaused
    val latestWaterfallFrame = waterfallClient.latestFrame
    val waterfallBufferVersion = waterfallBuffer.version
    val waterfallPalette: StateFlow<WaterfallPaletteType> = waterfall.palette
    val waterfallAutoFollow: StateFlow<Boolean> = waterfall.autoFollow

    val userLogs: StateFlow<List<UserLogEntry>> = history.userLogs
    val recordingHistory: StateFlow<List<RecordingHistoryEntry>> = history.recordingHistory

    @Volatile private var wasRecordingBeforeDisconnect = false
    private var savedRecordingInterval = RecordingInterval.CONTINUOUS
    private var savedRecordingFormat = AudioRecordingFormat.MP3

    init {
        initializePersistedSettings(appSettings.settings.value)
        waterfall.start()
        initializeAudioPipeline()
        monitor.start(scope)
        observeConnectedUsers()
        observeConnectionLifecycle()
        observeConnectionErrors()
        history.resumePendingTranscriptions()
        history.refresh()
    }

    // ── Inizializzazione ─────────────────────────────────────────

    private fun initializePersistedSettings(settings: SettingsData) {
        AppLogger.init(File(appDataDir, "logs"), settings.logLevel)
        applyServerConfiguration()
        client.updatePassword(settings.serverPassword)
        waterfall.updatePassword(settings.serverPassword)
        client.clientName = settings.callsign
        applySquelchSettings(settings)

        val initialFrequency = settings.lastFrequencyKhz.coerceIn(0.0, MAX_FREQUENCY_KHZ)
        client.tune(initialFrequency, settings.lastMode)
        waterfall.applyPersistedSettings(settings, initialFrequency)
    }

    private fun applySquelchSettings(settings: SettingsData) {
        squelch.setThresholdDbm(settings.squelchThresholdDbm)
        squelch.setListenSquelchEnabled(settings.squelchListenEnabled)
        squelch.setRecordSquelchEnabled(settings.squelchRecordEnabled)
        squelch.softMuteMs = settings.softMuteMs
        squelch.preBufferMs = settings.preBufferMs
        squelch.hangTimeSeconds = settings.hangTimeSeconds
        val advancedFeaturesEnabled = hasAdvancedFeatures()
        squelch.setVadListenEnabled(advancedFeaturesEnabled && settings.vadListenEnabled)
        squelch.setVadRecordEnabled(advancedFeaturesEnabled && settings.vadRecordEnabled)
        squelch.setVadSensitivity(settings.vadSensitivity)
        squelch.setVadHangoverMs(settings.vadHangoverMs)
    }

    private fun initializeAudioPipeline() {
        squelch.onGateOpenCallback = recorder::onSquelchGateOpened
        squelch.onGateCloseCallback = recorder::onSquelchGateClosed
        squelch.onQsoStartedCallback = alerts::onQsoStarted
        squelch.onQsoEndedCallback = alerts::onQsoEnded
        recorder.onSegmentCompleted = ::archiveCompletedSegment

        player.start()
        client.audioFlow
            .onEach { frame ->
                val result = squelch.processFrame(frame)
                player.submitPcm(result.processedPcmBytes)
                recorder.submitAudio(frame)
            }
            .launchIn(scope)
    }

    private fun archiveCompletedSegment(filePath: String, durationSec: Long) {
        val frequency = currentFrequencyKhz.value
        val mode = currentMode.value.displayName
        val qsoContext = alerts.activeContext
        scope.launch(Dispatchers.IO) {
            try {
                if (!hasAdvancedFeatures()) return@launch
                val transcriptionRequested = history.isTranscriptionConfigured
                val id = history.archiveRecording(filePath, durationSec, frequency, mode, transcriptionRequested)
                alerts.trackRecording(id, qsoContext, filePath, transcriptionRequested)
                if (transcriptionRequested) history.enqueueTranscription(id, filePath)
                history.refresh()
            } catch (e: Exception) {
                AppLogger.error(TAG, "Impossibile archiviare la registrazione $filePath", e)
            }
        }
    }

    // ── Osservatori ──────────────────────────────────────────────

    private fun observeConnectedUsers() {
        var lastLoggedUserNames = ""
        client.activeUsers
            .filter { it.isNotEmpty() }
            .distinctUntilChanged()
            .onEach { users ->
                if (!hasAdvancedFeatures() || !appSettings.settings.value.recordConnectedUsers) {
                    lastLoggedUserNames = ""
                    return@onEach
                }
                if (client.connectionState.value !is ConnectionState.Connected) return@onEach
                val names = users.joinToString(", ") { it.name }.trim()
                if (names.isBlank() || names == lastLoggedUserNames) return@onEach
                lastLoggedUserNames = names

                val info = stationInfo.value
                history.logConnectedUsers(
                    activeUsers = users.size,
                    maxUsers = info.maxUsers.takeIf { it > 0 } ?: DEFAULT_MAX_USERS,
                    stationName = info.name.ifEmpty { client.host },
                    frequencyKhz = currentFrequencyKhz.value,
                    mode = currentMode.value.displayName,
                    userNames = names
                )
            }
            .launchIn(scope)
    }

    private fun observeConnectionLifecycle() {
        client.connectionState
            .onEach { state ->
                when (state) {
                    is ConnectionState.Connected -> resumeRecordingAfterReconnect()
                    is ConnectionState.Reconnecting -> {
                        waterfall.closeSocket()
                        suspendRecordingForReconnect()
                    }
                    is ConnectionState.Disconnected -> {
                        waterfall.closeSocket()
                        if (client.isManualDisconnected()) wasRecordingBeforeDisconnect = false
                    }
                    else -> Unit
                }
            }
            .launchIn(scope)
    }

    private suspend fun resumeRecordingAfterReconnect() {
        if (!wasRecordingBeforeDisconnect) return
        AppLogger.info(TAG, "Riconnessione riuscita: ripristino automatico registrazione...")
        wasRecordingBeforeDisconnect = false
        delay(RECORDING_RESUME_DELAY_MS)
        startRecording(savedRecordingInterval, savedRecordingFormat)
    }

    private fun suspendRecordingForReconnect() {
        if (!recorder.isRecording.value) return
        AppLogger.info(TAG, "Disconnessione non voluta durante la registrazione. Stato memorizzato per ripresa automatica.")
        wasRecordingBeforeDisconnect = true
        savedRecordingInterval = recorder.currentInterval
        savedRecordingFormat = recorder.format.value
        recorder.stopRecording()
    }

    private fun observeConnectionErrors() {
        merge(
            client.connectionErrors.map { "Audio/controllo: ${it.ifBlank { "errore senza dettagli" }}" },
            waterfallClient.connectionErrors.map { "Waterfall: ${it.ifBlank { "errore senza dettagli" }}" }
        )
            .distinctUntilChanged()
            .onEach { _connectionNotifications.emit(it) }
            .launchIn(scope)
    }

    // ── Connessione e sintonia ───────────────────────────────────

    private fun applyServerConfiguration() {
        val (host, port) = appSettings.getServerHostAndPort()
        client.updateServer(host, port)
        waterfall.updateServer(host, port)
        monitor.updateServer(host, port)
    }

    fun setServerAddress(address: String) {
        appSettings.setServerAddress(address)
        applyServerConfiguration()
    }

    fun setServerPassword(password: String) {
        appSettings.setServerPassword(password)
        client.updatePassword(password)
        waterfall.updatePassword(password)
    }

    fun connect() {
        applyServerConfiguration()
        waterfall.requestStream()
        client.connect(scope)
        waterfall.connectIfSessionReady()
    }

    fun disconnect() {
        wasRecordingBeforeDisconnect = false
        waterfall.stopStream()
        if (isRecording.value) stopRecording()
        client.disconnect()
    }

    fun tune(freqKhz: Double, mode: ModulationMode? = null) {
        val bounded = freqKhz.coerceIn(0.0, MAX_FREQUENCY_KHZ)
        val targetMode = mode ?: currentMode.value
        squelch.reset()
        client.tune(bounded, targetMode)
        appSettings.setLastTuned(bounded, targetMode)
        waterfall.followTuning(bounded)
    }

    /** Sintonia immediata da click o trascinamento sul waterfall o sullo spettro. */
    fun tuneFromWaterfall(freqKhz: Double) = tune(freqKhz, currentMode.value)

    fun stepFrequency(deltaKhz: Double) = tune(currentFrequencyKhz.value + deltaKhz, currentMode.value)

    fun setMode(mode: ModulationMode) {
        client.setMode(mode)
        appSettings.setLastTuned(currentFrequencyKhz.value, mode)
    }

    fun setAgc(enabled: Boolean) = client.setAgc(enabled)

    fun setIdentUser(name: String) {
        client.setIdentUser(name)
        appSettings.setCallsign(name)
    }

    // ── Waterfall ────────────────────────────────────────────────

    fun setWaterfallZoom(zoom: Int) = waterfall.setZoom(zoom)

    fun waterfallZoomIn() = waterfall.zoomBy(1)

    fun waterfallZoomOut() = waterfall.zoomBy(-1)

    fun setWaterfallCenterFrequency(cfKhz: Double) = waterfall.setCenterFrequency(cfKhz)

    fun setWaterfallPalette(palette: WaterfallPaletteType) = waterfall.setPalette(palette)

    fun setWaterfallDbRange(minDb: Int, maxDb: Int) = waterfall.setDbRange(minDb, maxDb)

    fun setWaterfallSpeed(speed: Int) = waterfall.setSpeed(speed)

    fun setWaterfallAutoFollow(autoFollow: Boolean) = waterfall.setAutoFollow(autoFollow, currentFrequencyKhz.value)

    fun toggleWaterfallPause() = waterfall.togglePause()

    fun clearWaterfall() = waterfall.clear()

    // ── Audio ────────────────────────────────────────────────────

    fun setVolume(vol: Float) = player.setVolume(vol)

    fun toggleMute() = player.toggleMute()

    // ── Preferiti e segnalibri DX ────────────────────────────────

    fun requestDxLabels() = client.requestDxLabels()

    fun syncFavoritesFromServer() {
        val labels = client.serverDxLabels.value
        if (labels.isEmpty()) {
            client.requestDxLabels()
            return
        }
        val favorites = labels.map { label ->
            FavoriteFrequency(
                name = label.i.ifBlank { "${label.f.toInt()} kHz" },
                frequencyKhz = label.f,
                modeCode = label.mode.code,
                group = label.g
            )
        }
        appSettings.updateFavorites(favorites)
        AppLogger.info(TAG, "Sincronizzati ${favorites.size} preferiti dal server KiwiSDR")
    }

    fun resetFavoritesToDefault() = appSettings.resetToDefaultFavorites()

    fun addFavorite(favorite: FavoriteFrequency) = appSettings.addFavorite(favorite)

    fun addCurrentAsFavorite(customName: String? = null) {
        val freq = currentFrequencyKhz.value
        val mode = currentMode.value
        val name = if (!customName.isNullOrBlank()) customName else "${mode.displayName} ${freq.toInt()} kHz"
        appSettings.addFavorite(
            FavoriteFrequency(name = name, frequencyKhz = freq, modeCode = mode.code, group = bandGroupFor(freq))
        )
    }

    private fun bandGroupFor(freqKhz: Double): String = when {
        freqKhz in 26965.0..27405.0 -> "CB"
        freqKhz in 1800.0..2000.0 || freqKhz in 3500.0..3800.0 || freqKhz in 7000.0..7200.0 ||
            freqKhz in 14000.0..14350.0 || freqKhz in 21000.0..21450.0 || freqKhz in 28000.0..29700.0 -> "HAM"
        freqKhz < 530.0 -> "LW"
        freqKhz in 530.0..1700.0 -> "MW"
        else -> "DX"
    }

    fun editFavorite(oldFav: FavoriteFrequency, newFav: FavoriteFrequency) = appSettings.editFavorite(oldFav, newFav)

    fun removeFavorite(fav: FavoriteFrequency) = appSettings.removeFavorite(fav)

    // ── Squelch e VAD ────────────────────────────────────────────

    fun setSquelchThreshold(dbm: Double) {
        squelch.setThresholdDbm(dbm)
        appSettings.setSquelchThreshold(dbm)
    }

    fun setSquelchListenEnabled(enabled: Boolean) {
        squelch.setListenSquelchEnabled(enabled)
        appSettings.setSquelchListenEnabled(enabled)
    }

    fun setSquelchRecordEnabled(enabled: Boolean) {
        if (!hasAdvancedFeatures()) return
        squelch.setRecordSquelchEnabled(enabled)
        appSettings.setSquelchRecordEnabled(enabled)
    }

    fun setSoftMuteMs(ms: Int) {
        squelch.softMuteMs = ms
        appSettings.setSoftMuteMs(ms)
    }

    fun setPreBufferMs(ms: Int) {
        squelch.preBufferMs = ms
        appSettings.setPreBufferMs(ms)
    }

    fun setHangTimeSeconds(seconds: Double) {
        squelch.hangTimeSeconds = seconds
        appSettings.setHangTimeSeconds(seconds)
    }

    fun setVadListenEnabled(enabled: Boolean) {
        val allowed = enabled && hasAdvancedFeatures()
        squelch.setVadListenEnabled(allowed)
        appSettings.setVadListenEnabled(allowed)
    }

    fun setVadRecordEnabled(enabled: Boolean) {
        val allowed = enabled && hasAdvancedFeatures()
        squelch.setVadRecordEnabled(allowed)
        appSettings.setVadRecordEnabled(allowed)
    }

    fun setVadSensitivity(sensitivity: Float) {
        if (!hasAdvancedFeatures()) return
        squelch.setVadSensitivity(sensitivity)
        appSettings.setVadSensitivity(sensitivity)
    }

    fun setVadHangoverMs(ms: Int) {
        if (!hasAdvancedFeatures()) return
        squelch.setVadHangoverMs(ms)
        appSettings.setVadHangoverMs(ms)
    }

    // ── Registrazione e riproduzione ─────────────────────────────

    fun setRecordingFormat(format: AudioRecordingFormat) {
        if (!hasAdvancedFeatures()) return
        recorder.setRecordingFormat(format)
    }

    fun startRecording(interval: RecordingInterval, format: AudioRecordingFormat = recorder.format.value) {
        if (!hasAdvancedFeatures()) {
            _connectionNotifications.tryEmit("Le funzioni avanzate richiedono l'attivazione della licenza.")
            return
        }
        val squelchMode = squelch.recordSquelchEnabled.value || squelch.vadRecordEnabled.value
        recorder.startRecording(interval, format, squelchMode = squelchMode)
    }

    fun stopRecording() {
        wasRecordingBeforeDisconnect = false
        recorder.stopRecording()
    }

    fun playRecordedFile(filePath: String) {
        if (!hasAdvancedFeatures()) return
        filePlayer.play(filePath)
    }

    fun stopFilePlayback() = filePlayer.stop()

    fun pauseFilePlayback() {
        if (!hasAdvancedFeatures()) return
        filePlayer.pause()
    }

    fun resumeFilePlayback() {
        if (!hasAdvancedFeatures()) return
        filePlayer.resume()
    }

    fun seekFilePlayback(deltaMs: Long) {
        if (!hasAdvancedFeatures()) return
        filePlayer.seekBy(deltaMs)
    }

    // ── Storico e trascrizioni ───────────────────────────────────

    fun refreshHistory() = history.refresh()

    fun searchRecordingHistory(query: String) = history.search(query)

    fun deleteRecording(id: Long, filePath: String, deleteFileFromDisk: Boolean = true) {
        if (!hasAdvancedFeatures()) return
        if (filePlayer.currentlyPlayingPath.value == filePath) filePlayer.stop()
        history.deleteRecording(id, filePath, deleteFileFromDisk)
    }

    fun deleteRecordings(items: List<Pair<Long, String>>, deleteFilesFromDisk: Boolean = true) {
        if (!hasAdvancedFeatures()) return
        val currentPlay = filePlayer.currentlyPlayingPath.value
        if (currentPlay != null && items.any { it.second == currentPlay }) filePlayer.stop()
        history.deleteRecordings(items, deleteFilesFromDisk)
    }

    fun deleteUserLogs(ids: List<Long>) = history.deleteUserLogs(ids)

    fun clearAllUserLogs() = history.clearAllUserLogs()

    fun transcribeRecording(id: Long, filePath: String) = history.requestTranscription(id, filePath)

    // ── Impostazioni ─────────────────────────────────────────────

    fun setSttModelPath(path: String) {
        if (!hasAdvancedFeatures()) return
        appSettings.setSttModelPath(path)
    }

    fun setRecordConnectedUsers(enabled: Boolean) {
        if (hasAdvancedFeatures()) appSettings.setRecordConnectedUsers(enabled)
    }

    fun setLogLevel(level: LogLevel) = appSettings.setLogLevel(level)

    fun completeInitialSetup() = appSettings.setSetupCompleted(true)

    fun updateTelegramSettings(
        botToken: String,
        chatId: String,
        enabled: Boolean,
        notifyAnyQso: Boolean,
        sensitiveWords: String,
        intervalMinutes: Int,
        sendAudio: Boolean,
        sendTranscription: Boolean,
        notifyQsoStart: Boolean = false,
        notifyQsoEnd: Boolean = false
    ) = appSettings.updateTelegramSettings(
        botToken, chatId, enabled, notifyAnyQso, sensitiveWords, intervalMinutes,
        sendAudio, sendTranscription, notifyQsoStart, notifyQsoEnd
    )

    /** Invia un messaggio di prova con le credenziali indicate, anche se non ancora salvate. */
    suspend fun sendTelegramTestMessage(botToken: String, chatId: String): Pair<Boolean, String> {
        val message = "KiwiSDR Grabber — Test\n" +
            "Connessione al bot configurata correttamente!\n" +
            "• Ricevitore: $serverEndpoint\n" +
            "• Frequenza VFO: ${currentFrequencyKhz.value} kHz\n" +
            "• Modalità: ${currentMode.value.displayName}"
        return TelegramAlertService.sendMessage(botToken, chatId, message)
    }

    // ── Licenza ──────────────────────────────────────────────────

    fun isLicenseValid(licenseKey: String): Boolean = LicenseManager.verifyLicenseForCurrentMachine(licenseKey)

    /** Salva la chiave solo se valida per questa macchina; la licenza attuale resta invariata altrimenti. */
    fun activateLicense(licenseKey: String): Boolean {
        val cleanKey = licenseKey.trim()
        if (!isLicenseValid(cleanKey)) return false
        appSettings.setLicense(cleanKey)
        return true
    }

    private fun hasAdvancedFeatures(): Boolean = isLicenseValid(appSettings.settings.value.licenseKey)

    fun shutdown() {
        appSettings.setLastTuned(currentFrequencyKhz.value, currentMode.value)
        if (isRecording.value) stopRecording()
        waterfall.stopStream()
        filePlayer.stop()
        client.disconnect()
        monitor.stop()
        player.stop()
        database.close()
        history.closeTranscriptionQueue()
        scope.cancel()
    }
}
