package com.grabber.app

import com.grabber.alerting.TelegramAlertService
import com.grabber.audio.DesktopAudioPlayer
import com.grabber.audio.AudioPipeline
import com.grabber.audio.FileAudioPlayer
import com.grabber.audio.dsp.AudioDspConfig
import com.grabber.audio.dsp.DspPreset
import com.grabber.audio.dsp.DspTarget
import com.grabber.audio.squelch.SquelchController
import com.grabber.kiwi.JdkWebSocketConnector
import com.grabber.kiwi.KiwiClient
import com.grabber.kiwi.KiwiWebSocketConnector
import com.grabber.license.LicenseManager
import com.grabber.license.MachineIdUtil
import com.grabber.model.ConnectionState
import com.grabber.model.ModulationMode
import com.grabber.model.TranscriptionStatus
import com.grabber.model.UiThemeMode
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
private const val DSP_SAVE_DEBOUNCE_MS = 300L

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
    connector: KiwiWebSocketConnector = JdkWebSocketConnector(),
    private val client: KiwiClient = KiwiClient(connector),
    private val player: DesktopAudioPlayer = DesktopAudioPlayer(),
    private val filePlayer: FileAudioPlayer = FileAudioPlayer(),
    private val recorder: AudioRecorder = AudioRecorder(),
    private val database: HistoryDatabase = HistoryDatabase(),
    private val monitor: StationMonitor = StationMonitor(),
    private val squelch: SquelchController = SquelchController(),
    private val waterfallClient: WaterfallClient = WaterfallClient(connector, host = client.host, port = client.port),
    /** Buffer di pixel letto direttamente dal renderer del waterfall. */
    val waterfallBuffer: WaterfallBuffer = WaterfallBuffer(width = 1024, height = 250)
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + uncaughtErrorLogger())

    private val appDataDir = File(System.getProperty("user.home"), ".kiwigrabber").also { it.mkdirs() }
    private val appSettings = AppSettings(appDataDir)

    private val _connectionNotifications = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val connectionNotifications: SharedFlow<String> = _connectionNotifications.asSharedFlow()

    private val waterfall = WaterfallCoordinator(scope, client, waterfallClient, waterfallBuffer, AppSettingsWaterfallPreferences(appSettings))

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

    private val rangeScanHitLog = ScanHitLog()
    private val _rangeScanHits = MutableStateFlow<List<ScanHit>>(emptyList())

    /** Segnali trovati dalla scansione dell'intervallo in questa sessione, dal più recente. */
    val rangeScanHits: StateFlow<List<ScanHit>> = _rangeScanHits.asStateFlow()

    private val scanner = ScanCoordinator(
        scope = scope,
        signalGateOpen = squelch.isGateOpen,
        tuneTo = { applyTuning(it.frequencyKhz, it.mode) },
        resetSignalDetection = squelch::reset,
        onSignalFound = { kind, target ->
            if (kind == ScanKind.RANGE) {
                _rangeScanHits.value = rangeScanHitLog.record(target, client.rssi.value, System.currentTimeMillis())
            }
        },
        onStoppedOnSignal = { _, target -> _connectionNotifications.tryEmit("Scansione fermata: segnale su ${target.label}") }
    )

    /** La sorgente dell'intervallo viene ricreata solo quando cambiano i parametri, non a ogni passo. */
    @Volatile private var cachedRangeSource: Pair<RangeScanParams, FrequencyRangeScanSource>? = null

    private fun rangeScanSource(): ScanSource {
        val params = appSettings.settings.value.rangeScanParams
        cachedRangeSource?.let { (cachedParams, source) -> if (cachedParams == params) return source }
        return FrequencyRangeScanSource(params).also { cachedRangeSource = params to it }
    }

    private val channelScanPlan = ScanPlan(
        kind = ScanKind.CHANNELS,
        source = { ListScanSource(appSettings.settings.value.favorites.filter { it.scanEnabled }.map { it.toScanTarget() }) },
        dwellMillis = { appSettings.settings.value.scanDwellSeconds * 1000L },
        autoResume = { appSettings.settings.value.scanAutoResume }
    )

    private val rangeScanPlan = ScanPlan(
        kind = ScanKind.RANGE,
        source = ::rangeScanSource,
        dwellMillis = { appSettings.settings.value.rangeScanDwellMs.toLong() },
        autoResume = { appSettings.settings.value.rangeScanAutoResume }
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

    /** Stato della scansione in corso (canali o intervallo). */
    val scanState: StateFlow<ScanState> = scanner.state

    val userLogs: StateFlow<List<UserLogEntry>> = history.userLogs
    val recordingHistory: StateFlow<List<RecordingHistoryEntry>> = history.recordingHistory

    // ── Pipeline audio e DSP ──
    private val audioPipeline = AudioPipeline(
        squelch = squelch,
        playbackSink = player::submitPcm,
        recordSink = recorder::submitAudio
    )
    private val _dspConfig = MutableStateFlow(appSettings.settings.value.audioDsp.normalized())

    /** Configurazione DSP corrente; l'effetto audio è immediato, il salvataggio su disco è differito. */
    val dspConfig: StateFlow<AudioDspConfig> = _dspConfig.asStateFlow()
    private var dspSaveJob: Job? = null

    /** Vero se la catena DSP sta elaborando l'audio (destinazione attiva, almeno un filtro, licenza valida). */
    val isDspActive: StateFlow<Boolean> = combine(
        _dspConfig,
        appSettings.settings.map { it.licenseKey }.distinctUntilChanged()
    ) { config, licenseKey ->
        config.target != DspTarget.OFF && !config.isNeutral && isLicenseValid(licenseKey)
    }.stateIn(scope, SharingStarted.Eagerly, false)

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
        waterfall.applyPersistedSettings(
            clientName = settings.callsign,
            minDb = settings.waterfallMinDb,
            maxDb = settings.waterfallMaxDb,
            speed = settings.waterfallSpeed,
            zoom = settings.waterfallZoom,
            initialFrequencyKhz = initialFrequency
        )
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
        applyDspConfiguration()
        client.audioFlow
            .onEach(audioPipeline::process)
            .launchIn(scope)
        // Attivazione o perdita della licenza: il DSP si accende o si spegne senza riavvio.
        appSettings.settings
            .map { it.licenseKey }
            .distinctUntilChanged()
            .onEach { applyDspConfiguration() }
            .launchIn(scope)
    }

    private fun applyDspConfiguration() {
        audioPipeline.configure(_dspConfig.value, licensed = hasAdvancedFeatures())
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
                if (state !is ConnectionState.Connected) scanner.stop()
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
        scanner.stop()
        waterfall.stopStream()
        if (isRecording.value) stopRecording()
        client.disconnect()
    }

    /** Sintonia richiesta dall'utente: interrompe un'eventuale scansione (canali o intervallo). */
    fun tune(freqKhz: Double, mode: ModulationMode? = null) {
        scanner.stop()
        applyTuning(freqKhz, mode)
    }

    private fun applyTuning(freqKhz: Double, mode: ModulationMode?) {
        val bounded = freqKhz.coerceIn(0.0, MAX_FREQUENCY_KHZ)
        val targetMode = mode ?: currentMode.value
        squelch.reset()
        audioPipeline.resetFilters()
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
        // Le spunte di scansione restano sui canali con stessa frequenza e modo.
        val scanSelection = appSettings.settings.value.favorites
            .filter { it.scanEnabled }
            .map { it.frequencyKhz to it.modeCode }
            .toSet()
        val favorites = labels.map { label ->
            FavoriteFrequency(
                name = label.name.ifBlank { "${label.frequencyKhz.toInt()} kHz" },
                frequencyKhz = label.frequencyKhz,
                modeCode = label.mode.code,
                group = bandGroupFor(label.frequencyKhz),
                scanEnabled = (label.frequencyKhz to label.mode.code) in scanSelection
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

    fun editFavorite(oldFav: FavoriteFrequency, newFav: FavoriteFrequency) = appSettings.editFavorite(oldFav, newFav)

    fun removeFavorite(fav: FavoriteFrequency) = appSettings.removeFavorite(fav)

    // ── Scansione dei canali (funzione avanzata) ─────────────────

    fun setFavoritesScanEnabled(favorites: Collection<FavoriteFrequency>, enabled: Boolean) {
        if (!hasAdvancedFeatures()) return
        appSettings.setFavoritesScanEnabled(favorites, enabled)
    }

    fun setScanDwellSeconds(seconds: Int) {
        if (!hasAdvancedFeatures()) return
        appSettings.setScanDwellSeconds(seconds)
    }

    fun setScanAutoResume(enabled: Boolean) {
        if (!hasAdvancedFeatures()) return
        appSettings.setScanAutoResume(enabled)
    }

    /** Avvia la scansione dei canali selezionati; ferma un'eventuale scansione dell'intervallo. */
    fun startChannelScan() {
        val problem = when {
            !hasAdvancedFeatures() -> "La scansione dei canali richiede l'attivazione della licenza."
            client.connectionState.value !is ConnectionState.Connected -> "Connettiti al ricevitore per avviare la scansione."
            appSettings.settings.value.favorites.none { it.scanEnabled } -> "Seleziona almeno un canale da scansionare."
            else -> null
        }
        if (problem != null) {
            _connectionNotifications.tryEmit(problem)
            return
        }
        scanner.stop()
        scanner.start(channelScanPlan)
    }

    /** Ferma la scansione in corso, di qualsiasi tipo. */
    fun stopScan() = scanner.stop()

    // ── Scansione di un intervallo di frequenze (funzione avanzata) ──

    /**
     * Salva inizio, fine, passo e modo dell'intervallo. Restituisce il messaggio d'errore se i valori
     * non sono validi (in quel caso nulla viene salvato), altrimenti null.
     */
    fun setRangeScanParameters(startKhz: Double, endKhz: Double, stepKhz: Double, mode: ModulationMode): String? {
        if (!hasAdvancedFeatures()) return "La scansione dell'intervallo richiede l'attivazione della licenza."
        return appSettings.setRangeScanParameters(startKhz, endKhz, stepKhz, mode)
    }

    fun setRangeScanDwellMs(ms: Int) {
        if (!hasAdvancedFeatures()) return
        appSettings.setRangeScanDwellMs(ms)
    }

    fun setRangeScanAutoResume(enabled: Boolean) {
        if (!hasAdvancedFeatures()) return
        appSettings.setRangeScanAutoResume(enabled)
    }

    /** Avvia la scansione dell'intervallo salvato; ferma un'eventuale scansione dei canali. */
    fun startRangeScan() {
        val problem = when {
            !hasAdvancedFeatures() -> "La scansione dell'intervallo richiede l'attivazione della licenza."
            client.connectionState.value !is ConnectionState.Connected -> "Connettiti al ricevitore per avviare la scansione."
            else -> validateRangeScan(appSettings.settings.value.rangeScanParams)
        }
        if (problem != null) {
            _connectionNotifications.tryEmit(problem)
            return
        }
        scanner.stop()
        scanner.start(rangeScanPlan)
    }

    /** Esclude dalla scansione dell'intervallo la frequenza su cui la scansione è ferma (o quella sintonizzata). */
    fun excludeCurrentFromRangeScan() {
        val state = scanner.state.value
        val frequency = when (state) {
            is ScanState.SignalFound -> state.target.frequencyKhz
            is ScanState.Scanning -> state.target.frequencyKhz
            ScanState.Idle -> currentFrequencyKhz.value
        }
        excludeFromRangeScan(frequency)
    }

    fun excludeFromRangeScan(frequencyKhz: Double) {
        if (!hasAdvancedFeatures()) return
        appSettings.addRangeScanExclusion(frequencyKhz)
        _rangeScanHits.value = rangeScanHitLog.remove(frequencyKhz)
        _connectionNotifications.tryEmit("Frequenza ${"%.3f".format(java.util.Locale.US, frequencyKhz)} kHz esclusa dalla scansione.")
    }

    fun removeRangeScanExclusion(frequencyKhz: Double) {
        if (!hasAdvancedFeatures()) return
        appSettings.removeRangeScanExclusion(frequencyKhz)
    }

    fun clearRangeScanExclusions() {
        if (!hasAdvancedFeatures()) return
        appSettings.clearRangeScanExclusions()
    }

    fun clearRangeScanHits() {
        _rangeScanHits.value = rangeScanHitLog.clear()
    }

    /** Aggiunge ai preferiti un segnale trovato (gruppo "SCAN"). */
    fun addScanHitToFavorites(hit: ScanHit) {
        val name = "Scan ${"%.3f".format(java.util.Locale.US, hit.frequencyKhz)}"
        appSettings.addFavorite(FavoriteFrequency(name, hit.frequencyKhz, hit.mode.code, "SCAN"))
        _connectionNotifications.tryEmit("$name aggiunto ai preferiti.")
    }

    // ── DSP: equalizzatore e filtri (funzione avanzata) ──────────

    fun setDspTarget(target: DspTarget) = updateDsp { it.copy(targetName = target.name) }

    fun setDspEqGain(band: Int, gainDb: Double) = updateDsp { config ->
        if (band !in config.eqGainsDb.indices) return@updateDsp config
        config.copy(eqGainsDb = config.eqGainsDb.toMutableList().also { it[band] = gainDb }).markCustom()
    }

    /** Frequenze dei filtri in Hz; 0 disattiva il filtro. */
    fun setDspHighPass(hz: Int) = updateDsp { it.copy(highPassHz = hz).markCustom() }

    fun setDspLowPass(hz: Int) = updateDsp { it.copy(lowPassHz = hz).markCustom() }

    fun setDspNotch(hz: Int, q: Double = _dspConfig.value.notchQ) = updateDsp { it.copy(notchHz = hz, notchQ = q).markCustom() }

    fun setDspNoiseReduction(percent: Int) = updateDsp { it.copy(noiseReductionPercent = percent).markCustom() }

    fun applyDspPreset(preset: DspPreset) = updateDsp { it.withPreset(preset) }

    /** Toglie tutti i filtri mantenendo la destinazione. */
    fun resetDsp() = updateDsp { it.withPreset(DspPreset.NEUTRAL) }

    /** Bypass momentaneo per il confronto A/B: non viene salvato. */
    fun setDspBypass(bypassed: Boolean) {
        if (!hasAdvancedFeatures()) return
        if (bypassed) {
            audioPipeline.configure(_dspConfig.value.copy(targetName = DspTarget.OFF.name), licensed = true)
        } else {
            applyDspConfiguration()
        }
    }

    private fun AudioDspConfig.markCustom(): AudioDspConfig = copy(presetName = DspPreset.CUSTOM.name)

    /** Applica subito la modifica all'audio e la salva su disco dopo una breve pausa (cursori). */
    private fun updateDsp(transform: (AudioDspConfig) -> AudioDspConfig) {
        if (!hasAdvancedFeatures()) return
        val updated = _dspConfig.updateAndGet { transform(it).normalized() }
        applyDspConfiguration()
        dspSaveJob?.cancel()
        dspSaveJob = scope.launch {
            delay(DSP_SAVE_DEBOUNCE_MS)
            appSettings.setAudioDsp(updated)
        }
    }

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

    /** Tema dell'interfaccia: funzione libera, applicata subito dalla UI tramite [settings]. */
    fun setUiTheme(mode: UiThemeMode) = appSettings.setUiTheme(mode)

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
        scanner.stop()
        // Salvataggio immediato di una modifica DSP ancora in attesa del debounce.
        if (dspSaveJob?.isActive == true) {
            dspSaveJob?.cancel()
            appSettings.setAudioDsp(_dspConfig.value)
        }
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
