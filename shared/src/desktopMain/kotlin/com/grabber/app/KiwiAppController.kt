package com.grabber.app

import com.grabber.alerting.TelegramAlertService
import com.grabber.audio.DesktopAudioPlayer
import com.grabber.audio.AudioDspControl
import com.grabber.audio.AudioAnalysisTap
import com.grabber.audio.AudioPipeline
import com.grabber.audio.FileAudioPlayer
import com.grabber.audio.dsp.AudioDspConfig
import com.grabber.audio.dsp.DspPreset
import com.grabber.audio.dsp.DspTarget
import com.grabber.audio.squelch.GateMode
import com.grabber.audio.squelch.SquelchController
import com.grabber.decoder.DecodedOutputStore
import com.grabber.decoder.DigitalDecoderCoordinator
import com.grabber.decoder.DigitalModeAvailabilityRules
import com.grabber.decoder.LocalDecoderFactory
import com.grabber.decoder.fsk.FskDecoders
import com.grabber.kiwi.JdkWebSocketConnector
import com.grabber.kiwi.KiwiProtocol
import com.grabber.kiwi.ext.ExtensionClient
import com.grabber.kiwi.KiwiClient
import com.grabber.kiwi.KiwiWebSocketConnector
import com.grabber.license.LicenseManager
import com.grabber.license.MachineIdUtil
import com.grabber.model.ConnectionState
import com.grabber.model.ModulationMode
import com.grabber.model.Passband
import com.grabber.model.digital.DecodedImage
import com.grabber.model.digital.DecodedItem
import com.grabber.model.digital.DecoderMetrics
import com.grabber.model.digital.DecoderState
import com.grabber.model.digital.DigitalMode
import com.grabber.model.digital.DigitalModeAvailability
import com.grabber.model.digital.DigitalModeCatalog
import com.grabber.model.digital.DigitalModeParams
import com.grabber.model.digital.DigitalTuning
import com.grabber.model.digital.FskParams
import com.grabber.model.digital.FskPreset
import com.grabber.audio.analysis.ModeClassifier
import com.grabber.audio.analysis.ModeSuggestion
import com.grabber.model.TranscriptionStatus
import com.grabber.model.UiThemeMode
import com.grabber.monitor.HistoryDatabase
import com.grabber.monitor.RecordingHistoryEntry
import com.grabber.monitor.StationMonitor
import com.grabber.monitor.UserLogEntry
import com.grabber.recording.AudioRecorder
import com.grabber.recording.AudioRecordingFormat
import com.grabber.recording.RecordingInterval
import com.grabber.recording.RecordingNoiseFilter
import com.grabber.recording.RecordingNoiseFilterConfig
import com.grabber.update.UpdateCheckState
import com.grabber.update.UpdateChecker
import com.grabber.waterfall.WaterfallBuffer
import com.grabber.waterfall.WaterfallClient
import com.grabber.waterfall.WaterfallPaletteType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

private const val TAG = "KiwiAppController"
private const val MAX_FREQUENCY_KHZ = 30000.0
private const val DEFAULT_MAX_USERS = 4
private const val FREE_CONNECTION_DELAY_MS = 1500L
private const val AUTO_START_CONFIDENCE = 0.9

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
    val waterfallBuffer: WaterfallBuffer = WaterfallBuffer(width = 1024, height = 250),
    /** Decoder eseguiti nell'app (RTTY/FSK, NAVTEX, DSC). */
    private val localDecoderFactory: LocalDecoderFactory = FskDecoders
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + uncaughtErrorLogger())

    private val appDataDir = File(System.getProperty("user.home"), ".kiwigrabber").also { it.mkdirs() }
    private val appSettings = AppSettings(appDataDir)

    private val _connectionNotifications = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val connectionNotifications: SharedFlow<String> = _connectionNotifications.asSharedFlow()

    private val waterfall = WaterfallCoordinator(scope, client, waterfallClient, waterfallBuffer, AppSettingsWaterfallPreferences(appSettings))

    // ── Modi digitali: /EXT, tap di analisi audio e decoder ──
    private val extensionClient = ExtensionClient(connector, host = client.host, port = client.port)

    /** Copia dell'audio originale per decoder locali, AFC e riconoscimento del modo. */
    private val analysisTap = AudioAnalysisTap(scope)

    private val decoders = DigitalDecoderCoordinator(
        scope = scope,
        extensionClient = extensionClient,
        isAuthenticated = client.isAuthenticated,
        sessionToken = { client.sessionToken.value },
        carrierKhz = ::currentCarrierKhz,
        localDecoders = localDecoderFactory
    )

    private val decodedOutputStore = DecodedOutputStore { recorder.baseDirectory }

    private val decoderAlerts = DecoderAlertCoordinator(
        scope = scope,
        appSettings = appSettings,
        hasAdvancedFeatures = ::hasAdvancedFeatures,
        currentFrequencyKhz = { client.currentFrequencyKhz.value },
        onDeliveryFailure = { _connectionNotifications.tryEmit(it) }
    )

    private val classifier = ModeClassifier()
    private val _modeSuggestion = MutableStateFlow<ModeSuggestion?>(null)

    /** Modo suggerito dall'analisi dell'audio (null se nessun candidato o riconoscimento spento). */
    val modeSuggestion: StateFlow<ModeSuggestion?> = _modeSuggestion.asStateFlow()

    /** Autocentraggio one-shot dopo una sintonia dell'utente (funzione con licenza, spento di default). */
    private val autoCenter = AutoCenterCoordinator(
        scope = scope,
        isEnabled = { appSettings.settings.value.autoCenterEnabled && hasAdvancedFeatures() },
        context = {
            val params = appSettings.settings.value.digitalModeParams
            val active = decoders.currentMode
            val (fskCenter, fskShift) = afcFskParameters(active, params.rtty.centerHz, params.rtty.shiftHz)
            AfcContext(client.currentMode.value, client.currentFrequencyKhz.value, client.currentPassband.value, active, fskCenter, fskShift)
        },
        latestWaterfall = { waterfallClient.latestFrame.value },
        applyCorrection = { applyTuning(it, client.currentMode.value, source = TuneSource.AFC) }
    )

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
        onTranscriptionFinished = alerts::onTranscriptionFinished,
        onRecordingDiscarded = { id, filePath, _ ->
            alerts.forgetRecording(id)
            if (filePlayer.currentlyPlayingPath.value == filePath) filePlayer.stop()
        }
    )

    private val rangeScan = RangeScanSession(
        params = { appSettings.settings.value.rangeScanParams },
        dwellMillis = { appSettings.settings.value.rangeScanDwellMs.toLong() },
        autoResume = { appSettings.settings.value.rangeScanAutoResume }
    )

    /** Segnali trovati dalla scansione dell'intervallo in questa sessione, dal più recente. */
    val rangeScanHits: StateFlow<List<ScanHit>> = rangeScan.hits

    private val scanner = ScanCoordinator(
        scope = scope,
        signalGateOpen = squelch.isGateOpen,
        tuneTo = { applyTuning(it.frequencyKhz, it.mode, source = TuneSource.SCAN) },
        resetSignalDetection = squelch::reset,
        onSignalFound = { kind, target -> if (kind == ScanKind.RANGE) rangeScan.recordHit(target, client.rssi.value) },
        onStoppedOnSignal = { _, target -> _connectionNotifications.tryEmit("Scansione fermata: segnale su ${target.label}") }
    )

    private val channelScanPlan = ScanPlan(
        kind = ScanKind.CHANNELS,
        source = { ListScanSource(appSettings.settings.value.favorites.filter { it.scanEnabled }.map { it.toScanTarget() }) },
        dwellMillis = { appSettings.settings.value.scanDwellSeconds * 1000L },
        autoResume = { appSettings.settings.value.scanAutoResume }
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
    /** Modalità del gate di ascolto (libero, solo RF, solo voce, RF e voce). */
    val listenGateMode: StateFlow<GateMode> = squelch.listenMode
    /** Modalità del gate di registrazione (continua, solo RF, solo voce, RF e voce). */
    val recordGateMode: StateFlow<GateMode> = squelch.recordMode

    val vadConfidence = squelch.vadConfidence
    val isVoiceDetected = squelch.isVoiceDetected
    val isQrnDetected = squelch.isQrnDetected
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

    /** Banda passante corrente del canale (Hz rispetto alla portante). */
    val currentPassband: StateFlow<Passband> = client.currentPassband

    // ── Modi digitali ──
    val digitalDecoderState: StateFlow<DecoderState> = decoders.state
    val decodedItems: StateFlow<List<DecodedItem>> = decoders.items
    val decodedImage: StateFlow<DecodedImage?> = decoders.image
    val decoderMetrics: StateFlow<DecoderMetrics> = decoders.metrics

    /** Modi digitali con disponibilità sul ricevitore corrente (estensioni, abilitazioni, 12 kHz). */
    val digitalModes: StateFlow<List<DigitalModeAvailability>> = combine(
        client.isAuthenticated,
        waterfallClient.installedExtensions,
        client.receiverConfig,
        client.audioSampleRate
    ) { authenticated, installed, config, rate ->
        DigitalModeAvailabilityRules.compute(authenticated, installed, config, rate, implemented = ::isDigitalModeImplemented)
    }.stateIn(scope, SharingStarted.Eagerly, DigitalModeAvailabilityRules.compute(false, null, null, null, ::isDigitalModeImplemented))

    /** Limite `ext_api_nchans` del ricevitore (null se sconosciuto), mostrato come avviso nella scheda. */
    val receiverAppConnectionLimit: StateFlow<Int?> = client.receiverConfig
        .map { it?.extApiChannels }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val userLogs: StateFlow<List<UserLogEntry>> = history.userLogs
    val recordingHistory: StateFlow<List<RecordingHistoryEntry>> = history.recordingHistory

    // ── Pipeline audio e DSP ──
    private val audioPipeline = AudioPipeline(
        squelch = squelch,
        playbackSink = player::submitPcm,
        recordSink = recorder::submitAudio,
        analysisSink = analysisTap::submit
    )
    private val dsp = AudioDspControl(
        scope = scope,
        pipeline = audioPipeline,
        initialConfig = appSettings.settings.value.audioDsp,
        isLicensed = ::hasAdvancedFeatures,
        persist = appSettings::setAudioDsp
    )

    /** Configurazione DSP corrente; l'effetto audio è immediato, il salvataggio su disco è differito. */
    val dspConfig: StateFlow<AudioDspConfig> = dsp.config

    /** Vero se la catena DSP sta elaborando l'audio (destinazione attiva, almeno un filtro, licenza valida). */
    val isDspActive: StateFlow<Boolean> = dsp.isActive

    @Volatile private var savedRecordingInterval = RecordingInterval.CONTINUOUS
    @Volatile private var savedRecordingFormat = AudioRecordingFormat.MP3

    /** Ripresa di registrazione e scansione dopo una caduta di rete (logica condivisa con Android). */
    private val connectionResume = ConnectionResumeCoordinator(
        scope = scope,
        isAuthenticated = client.isAuthenticated,
        scanState = scanner.state,
        isRecording = { recorder.isRecording.value },
        suspendRecording = {
            savedRecordingInterval = recorder.currentInterval
            savedRecordingFormat = recorder.format.value
            recorder.stopRecording()
        },
        resumeRecording = { startRecording(savedRecordingInterval, savedRecordingFormat) },
        resumeScan = ::resumeScan
    )

    /** Versione in uso: proprietà `app.version` impostata dal launcher, "dev" se assente. */
    val appVersion: String = System.getProperty("app.version")?.takeIf { it.isNotBlank() } ?: "dev"

    private val updateChecker = UpdateChecker(appVersion)

    /** Esito del controllo degli aggiornamenti su GitHub, eseguito all'avvio (mostrato dallo splash screen). */
    val updateCheck: StateFlow<UpdateCheckState> = updateChecker.state

    init {
        initializePersistedSettings(appSettings.settings.value)
        waterfall.start()
        initializeAudioPipeline()
        initializeDigitalModes()
        monitor.start(scope)
        observeConnectedUsers()
        observeConnectionLifecycle()
        observeConnectionErrors()
        history.resumePendingTranscriptions()
        history.refresh()
        updateChecker.start(scope)
        restoreConnectionOnStartup()
    }

    /** Se l'app è stata chiusa con la connessione attiva, si riconnette all'ultimo server usato. */
    private fun restoreConnectionOnStartup() {
        val settings = appSettings.settings.value
        if (!settings.resumeConnectionOnStartup || !settings.setupCompleted) return
        AppLogger.info(TAG, "Connessione attiva alla chiusura: riconnessione a ${settings.serverAddress}")
        connect()
    }

    // ── Inizializzazione ─────────────────────────────────────────

    private fun initializePersistedSettings(settings: SettingsData) {
        AppLogger.init(File(appDataDir, "logs"), settings.logLevel)
        applyServerConfiguration()
        client.updatePassword(settings.serverPassword)
        waterfall.updatePassword(settings.serverPassword)
        extensionClient.password = settings.serverPassword
        client.clientName = settings.callsign
        extensionClient.clientName = settings.callsign
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
        squelch.softMuteMs = settings.softMuteMs
        squelch.preBufferMs = settings.preBufferMs
        squelch.hangTimeSeconds = settings.hangTimeSeconds
        squelch.voiceSilenceCloseSeconds = settings.effectiveVoiceSilenceCloseSeconds
        // Senza licenza il VAD resta spento: le modalità con voce perdono la componente vocale.
        val advancedFeaturesEnabled = hasAdvancedFeatures()
        squelch.setListenMode(settings.listenGateMode.restrictedTo(voiceAllowed = advancedFeaturesEnabled))
        squelch.setRecordMode(settings.recordGateMode.restrictedTo(voiceAllowed = advancedFeaturesEnabled))
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
            .onEach(audioPipeline::process)
            .launchIn(scope)
        // Attivazione o perdita della licenza: il DSP si accende o si spegne senza riavvio.
        appSettings.settings
            .map { it.licenseKey }
            .distinctUntilChanged()
            .onEach { dsp.refreshLicense() }
            .launchIn(scope)
    }

    private fun initializeDigitalModes() {
        analysisTap.addConsumer(AudioAnalysisTap.Consumer("decoder locale", { decoders.isLocalDecoderActive }, decoders::onAudio))
        analysisTap.addConsumer(AudioAnalysisTap.Consumer("AFC", { autoCenter.isCapturing }, autoCenter::onAudio))
        analysisTap.addConsumer(AudioAnalysisTap.Consumer("riconoscimento", ::isRecognitionActive, ::classifyAudio))
        decoderAlerts.start(decoders.completedItems)
        // Salvataggio automatico facoltativo delle immagini FAX/SSTV completate.
        decoders.completedImages
            .onEach { image ->
                val params = appSettings.settings.value.digitalModeParams
                val autoSave = when (image.mode) {
                    DigitalMode.FAX -> params.faxAutoSave
                    DigitalMode.SSTV -> params.sstvAutoSave
                    else -> false
                }
                if (!autoSave || !hasAdvancedFeatures()) return@onEach
                try {
                    decodedOutputStore.saveImage(image)?.let { _connectionNotifications.tryEmit("Immagine ${image.mode.displayName} salvata: ${it.name}") }
                } catch (e: Exception) {
                    AppLogger.error(TAG, "Salvataggio automatico dell'immagine non riuscito", e)
                }
            }
            .launchIn(scope)
        // Licenza revocata o scaduta: il decoder attivo si ferma.
        appSettings.settings
            .map { it.licenseKey }
            .distinctUntilChanged()
            .onEach { if (!hasAdvancedFeatures()) decoders.stop() }
            .launchIn(scope)
    }

    /**
     * Archivia un segmento completato. I segmenti QSO (con `activeMillis`, chiusi da squelch o VAD)
     * troppo brevi vengono cancellati subito; gli altri passano dal filtro anti-rumore dopo la trascrizione.
     */
    private fun archiveCompletedSegment(filePath: String, durationSec: Long, activeMillis: Long?) {
        val frequency = currentFrequencyKhz.value
        val mode = currentMode.value.displayName
        val qsoContext = alerts.activeContext
        scope.launch(Dispatchers.IO) {
            try {
                if (!hasAdvancedFeatures()) return@launch
                val noiseFilter = appSettings.settings.value.recordingNoiseFilter
                if (activeMillis != null && RecordingNoiseFilter.isTooShort(noiseFilter, activeMillis)) {
                    discardShortSegment(filePath, activeMillis)
                    return@launch
                }
                val transcriptionRequested = history.isTranscriptionConfigured
                val id = history.archiveRecording(filePath, durationSec, frequency, mode, transcriptionRequested)
                alerts.trackRecording(id, qsoContext, filePath, transcriptionRequested)
                if (transcriptionRequested) {
                    history.enqueueTranscription(id, filePath, applyNoiseFilter = activeMillis != null)
                }
                history.refresh()
            } catch (e: Exception) {
                AppLogger.error(TAG, "Impossibile archiviare la registrazione $filePath", e)
            }
        }
    }

    private fun discardShortSegment(filePath: String, activeMillis: Long) {
        val file = File(filePath)
        if (file.delete() || !file.exists()) {
            AppLogger.info(TAG, "Segmento QSO scartato dal filtro anti-rumore (attivo $activeMillis ms): $filePath")
        } else {
            AppLogger.warn(TAG, "Impossibile cancellare il segmento QSO troppo breve $filePath")
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
                // Caduta di rete: registrazione e scansione vanno ricordate prima di fermarle.
                if (state is ConnectionState.Reconnecting) connectionResume.onConnectionLost()
                if (state !is ConnectionState.Connected) scanner.stop()
                when (state) {
                    is ConnectionState.Connected -> connectionResume.onConnected()
                    is ConnectionState.Reconnecting -> waterfall.closeSocket()
                    is ConnectionState.Disconnected -> {
                        waterfall.closeSocket()
                        if (client.isManualDisconnected()) connectionResume.clear()
                    }
                    // Rifiuto del server (es. password errata): niente riconnessione al prossimo avvio.
                    is ConnectionState.Error -> {
                        connectionResume.clear()
                        appSettings.setResumeConnectionOnStartup(false)
                    }
                    is ConnectionState.Connecting -> Unit
                }
            }
            .launchIn(scope)
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
        if (extensionClient.host != host || extensionClient.port != port) {
            decoders.stop()
            extensionClient.host = host
            extensionClient.port = port
        }
    }

    fun setServerAddress(address: String) {
        appSettings.setServerAddress(address)
        applyServerConfiguration()
    }

    fun setServerPassword(password: String) {
        appSettings.setServerPassword(password)
        client.updatePassword(password)
        waterfall.updatePassword(password)
        extensionClient.password = password
    }

    fun connect() {
        appSettings.setResumeConnectionOnStartup(true)
        applyServerConfiguration()
        waterfall.requestStream()
        client.connect(scope)
        waterfall.connectIfSessionReady()
    }

    /** Disconnessione voluta dall'utente: al prossimo avvio l'app non si riconnette. */
    fun disconnect() {
        appSettings.setResumeConnectionOnStartup(false)
        connectionResume.clear()
        scanner.stop()
        decoders.stop()
        waterfall.stopStream()
        if (isRecording.value) stopRecording()
        client.disconnect()
    }

    /** Sintonia richiesta dall'utente: interrompe un'eventuale scansione (canali o intervallo). */
    fun tune(freqKhz: Double, mode: ModulationMode? = null, source: TuneSource = TuneSource.OTHER) {
        connectionResume.forgetScan()
        scanner.stop()
        applyTuning(freqKhz, mode, source = source)
    }

    private fun applyTuning(
        freqKhz: Double,
        mode: ModulationMode?,
        passband: Passband? = null,
        source: TuneSource = TuneSource.OTHER
    ) {
        val bounded = freqKhz.coerceIn(0.0, MAX_FREQUENCY_KHZ)
        val targetMode = mode ?: currentMode.value
        squelch.reset()
        audioPipeline.resetFilters()
        client.tune(bounded, targetMode, passband)
        appSettings.setLastTuned(bounded, targetMode)
        waterfall.followTuning(bounded)
        decoders.onRetuned()
        autoCenter.onTuned(source)
    }

    /** Portante corrente (in CW diversa dalla frequenza mostrata, vedi [KiwiProtocol.carrierFrequencyKhz]). */
    private fun currentCarrierKhz(): Double {
        val passband = client.currentPassband.value
        return KiwiProtocol.carrierFrequencyKhz(currentMode.value, currentFrequencyKhz.value, passband.lowCut, passband.highCut)
    }

    /** Sintonia immediata da click o trascinamento sul waterfall o sullo spettro. */
    fun tuneFromWaterfall(freqKhz: Double) = tune(freqKhz, currentMode.value, TuneSource.WATERFALL_CLICK)

    fun stepFrequency(deltaKhz: Double) = tune(currentFrequencyKhz.value + deltaKhz, currentMode.value, TuneSource.STEP)

    fun setMode(mode: ModulationMode) {
        client.setMode(mode)
        appSettings.setLastTuned(currentFrequencyKhz.value, mode)
    }

    fun setAgc(enabled: Boolean) = client.setAgc(enabled)

    fun setIdentUser(name: String) {
        client.setIdentUser(name)
        extensionClient.clientName = name
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
    fun startChannelScan() = startChannelScanFrom(0)

    private fun startChannelScanFrom(stepIndex: Int) {
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
        connectionResume.forgetScan()
        scanner.stop()
        stopDecoderForScan()
        scanner.start(channelScanPlan, stepIndex)
    }

    /** Ferma la scansione in corso, di qualsiasi tipo (anche quella in attesa di ripresa dopo la riconnessione). */
    fun stopScan() {
        connectionResume.forgetScan()
        scanner.stop()
    }

    /** Ripresa dopo la riconnessione dal passo su cui la scansione si trovava; i controlli restano quelli dell'avvio manuale. */
    private fun resumeScan(point: ScanResumePoint) = when (point.kind) {
        ScanKind.CHANNELS -> startChannelScanFrom(point.stepIndex)
        ScanKind.RANGE -> startRangeScanFrom(point.stepIndex)
    }

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
    fun startRangeScan() = startRangeScanFrom(0)

    private fun startRangeScanFrom(stepIndex: Int) {
        val problem = when {
            !hasAdvancedFeatures() -> "La scansione dell'intervallo richiede l'attivazione della licenza."
            client.connectionState.value !is ConnectionState.Connected -> "Connettiti al ricevitore per avviare la scansione."
            else -> rangeScan.validationError()
        }
        if (problem != null) {
            _connectionNotifications.tryEmit(problem)
            return
        }
        connectionResume.forgetScan()
        scanner.stop()
        stopDecoderForScan()
        scanner.start(rangeScan.plan, stepIndex)
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
        rangeScan.removeHit(frequencyKhz)
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
        rangeScan.clearHits()
    }

    /** Aggiunge ai preferiti un segnale trovato (gruppo "SCAN"). */
    fun addScanHitToFavorites(hit: ScanHit) {
        val name = "Scan ${"%.3f".format(java.util.Locale.US, hit.frequencyKhz)}"
        appSettings.addFavorite(FavoriteFrequency(name, hit.frequencyKhz, hit.mode.code, "SCAN"))
        _connectionNotifications.tryEmit("$name aggiunto ai preferiti.")
    }

    // ── DSP: equalizzatore e filtri (funzione avanzata) ──────────

    // La logica è in `AudioDspControl`, condivisa con Android; senza licenza i comandi non hanno effetto.

    fun setDspTarget(target: DspTarget) { dsp.setTarget(target) }

    fun setDspEqGain(band: Int, gainDb: Double) { dsp.setEqGain(band, gainDb) }

    /** Frequenze dei filtri in Hz; 0 disattiva il filtro. */
    fun setDspHighPass(hz: Int) { dsp.setHighPass(hz) }

    fun setDspLowPass(hz: Int) { dsp.setLowPass(hz) }

    fun setDspNotch(hz: Int, q: Double = dsp.config.value.notchQ) { dsp.setNotch(hz, q) }

    fun setDspNoiseReduction(percent: Int) { dsp.setNoiseReduction(percent) }

    fun applyDspPreset(preset: DspPreset) { dsp.applyPreset(preset) }

    /** Toglie tutti i filtri mantenendo la destinazione. */
    fun resetDsp() { dsp.reset() }

    /** Bypass momentaneo per il confronto A/B: non viene salvato. */
    fun setDspBypass(bypassed: Boolean) = dsp.setBypass(bypassed)

    // ── Squelch e VAD ────────────────────────────────────────────

    fun setSquelchThreshold(dbm: Double) {
        squelch.setThresholdDbm(dbm)
        appSettings.setSquelchThreshold(dbm)
    }

    /** Modalità del gate di ascolto; senza licenza sono ammesse solo "libero" e "solo RF". */
    fun setListenGateMode(mode: GateMode) {
        val allowed = mode.restrictedTo(voiceAllowed = hasAdvancedFeatures())
        squelch.setListenMode(allowed)
        appSettings.setListenGateMode(allowed)
    }

    /**
     * Modalità del gate di registrazione (funzione con licenza). Bloccata durante la registrazione:
     * il registratore decide all'avvio se lavorare a segmenti QSO o in continuo.
     */
    fun setRecordGateMode(mode: GateMode) {
        if (!hasAdvancedFeatures() || recorder.isRecording.value) return
        squelch.setRecordMode(mode)
        appSettings.setRecordGateMode(mode)
    }

    /** Chiusura del segmento "RF e voce" dopo [seconds] senza voce, anche con il gate RF aperto. */
    fun setVoiceSilenceClose(enabled: Boolean, seconds: Double) {
        if (!hasAdvancedFeatures()) return
        appSettings.setVoiceSilenceClose(enabled, seconds)
        squelch.voiceSilenceCloseSeconds = appSettings.settings.value.effectiveVoiceSilenceCloseSeconds
    }

    /** Filtro dei segmenti QSO brevi o trascritti come rumore (cancellati dal disco). */
    fun setRecordingNoiseFilter(config: RecordingNoiseFilterConfig) {
        if (!hasAdvancedFeatures()) return
        appSettings.setRecordingNoiseFilter(config)
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
        recorder.startRecording(interval, format, squelchMode = squelch.recordMode.value != GateMode.OFF)
    }

    fun stopRecording() {
        connectionResume.forgetRecording()
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

    // ── Modi digitali (funzione avanzata) ────────────────────────

    /**
     * Avvia il decoder di [mode] (ferma quello in corso e un'eventuale scansione). Con [tuneForMode]
     * applica modo e banda passante richiesti, alla frequenza [presetKhz] (frequenza pubblicata) o,
     * se assente, a quella corrente.
     */
    fun startDigitalDecoder(mode: DigitalMode, tuneForMode: Boolean, presetKhz: Double? = null) {
        val availability = digitalModes.value.firstOrNull { it.mode == mode }
        val problem = when {
            !hasAdvancedFeatures() -> "I modi digitali richiedono l'attivazione della licenza."
            !client.isAuthenticated.value -> "Connettiti al ricevitore per avviare un decoder."
            availability?.isAvailable == false -> availability.reason
            else -> null
        }
        if (problem != null) {
            _connectionNotifications.tryEmit(problem)
            return
        }
        connectionResume.forgetScan()
        scanner.stop()
        val params = appSettings.settings.value.digitalModeParams
        val published = presetKhz ?: publishedFrequencyFor(mode)
        if (tuneForMode) applyDigitalTuning(DigitalModeCatalog.tuningFor(mode, published, params))
        appSettings.setLastDigitalMode(mode)
        AppLogger.info(TAG, "Avvio decoder ${mode.displayName} a ${"%.3f".format(java.util.Locale.US, published)} kHz")
        decoders.start(mode, params, published)
    }

    /** Ferma il decoder; la banda passante torna quella predefinita del modo corrente. */
    fun stopDigitalDecoder() {
        if (decoders.currentMode == null && decoders.state.value == DecoderState.Idle) return
        decoders.stop()
        restoreDefaultPassband()
    }

    /** Nuovi parametri dei decoder: salvati e applicati al decoder attivo. */
    fun updateDigitalModeParams(params: DigitalModeParams) {
        if (!hasAdvancedFeatures()) return
        val normalized = params.normalized()
        val previous = appSettings.settings.value.digitalModeParams
        appSettings.setDigitalModeParams(normalized)
        decoders.updateParams(normalized)
        // RTTY: shift e centro audio cambiano la banda passante richiesta.
        if (decoders.currentMode == DigitalMode.RTTY && normalized.rtty != previous.rtty) {
            applyDigitalTuning(DigitalModeCatalog.tuningFor(DigitalMode.RTTY, currentFrequencyKhz.value, normalized))
        }
    }

    /** Riapplica modo e banda del decoder attivo alla frequenza corrente (pulsante "Ripristina"). */
    fun restoreTuningForDecoder() {
        val mode = decoders.currentMode ?: return
        val params = appSettings.settings.value.digitalModeParams
        val tuning = DigitalModeCatalog.tuningFor(mode, publishedFrequencyFor(mode), params)
        applyDigitalTuning(tuning)
    }

    // ── Riconoscimento automatico del modo ──

    private fun isRecognitionActive(): Boolean {
        val settings = appSettings.settings.value
        val active = settings.modeRecognitionEnabled && client.isAuthenticated.value && hasAdvancedFeatures()
        if (!active && _modeSuggestion.value != null) _modeSuggestion.value = null
        return active
    }

    /** Consumatore del tap di analisi (un solo flusso): aggiorna il suggerimento a ogni finestra. */
    private fun classifyAudio(frame: com.grabber.model.AudioFrame) {
        val passband = client.currentPassband.value
        classifier.setContext(ModeClassifier.Context(currentMode.value, currentFrequencyKhz.value, passband.lowCut, passband.highCut))
        val count = frame.pcmBytes.size / 2
        val samples = FloatArray(count) { i ->
            ((frame.pcmBytes[2 * i].toInt() and 0xFF) or (frame.pcmBytes[2 * i + 1].toInt() shl 8)).toShort() / 32768f
        }
        if (!classifier.process(samples, count, frame.sampleRate)) return
        val suggestion = classifier.suggestion
        _modeSuggestion.value = suggestion
        if (suggestion != null) AppLogger.debug(TAG, "Riconoscimento: ${suggestion.label} ${suggestion.percent}% ${suggestion.detail}")
        maybeAutoStart(suggestion)
    }

    /** D3: avvio automatico solo dei decoder locali e solo con confidenza ≥ 90 %, se nessun decoder è attivo. */
    private fun maybeAutoStart(suggestion: ModeSuggestion?) {
        val mode = suggestion?.mode ?: return
        val settings = appSettings.settings.value
        if (!settings.modeRecognitionAutoStart || mode.isServer || suggestion.confidence < AUTO_START_CONFIDENCE) return
        if (decoders.currentMode != null) return
        if (mode == DigitalMode.RTTY) {
            val shift = suggestion.fskShiftHz
            val preset = when (shift) {
                170 -> FskPreset.HAM
                450 -> FskPreset.DWD
                else -> null
            }
            val rtty = preset?.let { FskParams.forPreset(it) }
                ?: settings.digitalModeParams.rtty.copy(preset = FskPreset.CUSTOM, shiftHz = shift ?: settings.digitalModeParams.rtty.shiftHz)
            val center = suggestion.audioCenterHz?.toInt() ?: rtty.centerHz
            appSettings.setDigitalModeParams(settings.digitalModeParams.copy(rtty = rtty.copy(centerHz = center)))
        }
        AppLogger.info(TAG, "Avvio automatico del decoder ${mode.displayName} (${suggestion.percent}%)")
        _connectionNotifications.tryEmit("Riconosciuto ${mode.displayName} (${suggestion.percent}%): decoder avviato.")
        startDigitalDecoder(mode, tuneForMode = false)
    }

    fun setModeRecognition(enabled: Boolean, autoStart: Boolean) {
        if (enabled && !hasAdvancedFeatures()) {
            _connectionNotifications.tryEmit("Il riconoscimento automatico richiede l'attivazione della licenza.")
            return
        }
        appSettings.setModeRecognition(enabled, autoStart)
        if (!enabled) {
            classifier.reset()
            _modeSuggestion.value = null
        }
    }

    /** Allarga la banda per analizzare (in CW il segnale finisce a 1 kHz in USB). */
    fun tuneForAnalysis() {
        val freq = currentFrequencyKhz.value
        when (currentMode.value) {
            ModulationMode.CW -> applyTuning(freq - 1.0, ModulationMode.USB, Passband(100, 3100), TuneSource.PRESET)
            ModulationMode.LSB -> applyTuning(freq, ModulationMode.LSB, Passband(-3100, -100), TuneSource.PRESET)
            else -> applyTuning(freq, ModulationMode.USB, Passband(100, 3100), TuneSource.PRESET)
        }
    }

    /** Notifiche Telegram dei decoder con parole chiave dedicate (funzione con licenza). */
    fun setDecoderAlerts(enabled: Boolean, words: String) {
        if (!hasAdvancedFeatures()) return
        appSettings.setDecoderAlerts(enabled, words)
    }

    // ── Autocentraggio (AFC) ──

    /** Preferenza AFC; senza licenza resta spenta in pratica (il controller la ignora). */
    val autoCenterEnabled: StateFlow<Boolean> = appSettings.settings
        .map { it.autoCenterEnabled }
        .stateIn(scope, SharingStarted.Eagerly, appSettings.settings.value.autoCenterEnabled)

    /** Esito dell'ultima correzione (offset, "nessun segnale", "fuori portata"…). */
    val lastAutoCenter: StateFlow<AutoCenterResult?> = autoCenter.lastResult

    fun setAutoCenterEnabled(enabled: Boolean) {
        if (enabled && !hasAdvancedFeatures()) {
            _connectionNotifications.tryEmit("L'autocentraggio richiede l'attivazione della licenza.")
            return
        }
        appSettings.setAutoCenterEnabled(enabled)
        if (!enabled) autoCenter.cancel()
    }

    /** Vero se modo e banda correnti vanno bene per [mode]. */
    fun isTuningCompatible(mode: DigitalMode): Boolean =
        DigitalModeCatalog.isTuningCompatible(mode, currentMode.value, currentPassband.value, appSettings.settings.value.digitalModeParams)

    /** Sintonizza la frequenza pubblicata di un modo senza avviare il decoder. */
    fun tuneForDigitalMode(mode: DigitalMode, presetKhz: Double) {
        val params = appSettings.settings.value.digitalModeParams
        connectionResume.forgetScan()
        scanner.stop()
        applyDigitalTuning(DigitalModeCatalog.tuningFor(mode, presetKhz, params))
    }

    fun clearDecodedOutput() = decoders.clear()

    /** Salva testo o immagine del decoder attivo; restituisce il percorso, null se non c'è nulla da salvare. */
    fun saveDecodedOutput(): String? {
        if (!hasAdvancedFeatures()) return null
        val mode = decoders.currentMode ?: appSettings.settings.value.lastDigitalMode ?: return null
        return try {
            val file = if (mode.output == com.grabber.model.digital.DecoderOutputKind.IMAGE) {
                decoders.image.value?.let(decodedOutputStore::saveImage)
            } else {
                decodedOutputStore.saveText(mode, currentFrequencyKhz.value, decoders.items.value)
            }
            file?.absolutePath?.also { _connectionNotifications.tryEmit("Salvato: $it") }
        } catch (e: Exception) {
            AppLogger.error(TAG, "Salvataggio delle decodifiche non riuscito", e)
            _connectionNotifications.tryEmit("Salvataggio non riuscito: ${e.message ?: e.javaClass.simpleName}")
            null
        }
    }

    /** Cartella dei file salvati dai decoder. */
    val decodedOutputDirectory: File get() = decodedOutputStore.directory

    /**
     * Limite delle connessioni per le app raggiunto (D8): solo su richiesta dell'utente chiude il
     * waterfall e riprova ad aprire il decoder. Il waterfall si riapre con il pulsante esistente.
     */
    fun freeConnectionForDecoder() {
        if (!hasAdvancedFeatures()) return
        waterfall.stopStream()
        scope.launch {
            delay(FREE_CONNECTION_DELAY_MS)
            decoders.retry()
        }
    }

    private fun stopDecoderForScan() {
        if (decoders.currentMode != null) {
            decoders.stop()
            _connectionNotifications.tryEmit("Decoder fermato: la scansione cambia frequenza.")
        }
    }

    private fun applyDigitalTuning(tuning: DigitalTuning) =
        applyTuning(tuning.frequencyKhz, tuning.modulation, tuning.passband, TuneSource.PRESET)

    private fun restoreDefaultPassband() {
        val mode = currentMode.value
        if (!currentPassband.value.isDefaultFor(mode)) {
            applyTuning(currentFrequencyKhz.value, mode, Passband.defaultFor(mode))
        }
    }

    /**
     * Frequenza pubblicata corrispondente alla sintonia corrente (inverso del catalogo): WSPR e FAX
     * hanno la frequenza mostrata diversa da quella pubblicata.
     */
    private fun publishedFrequencyFor(mode: DigitalMode): Double {
        val freq = currentFrequencyKhz.value
        val probe = DigitalModeCatalog.tuningFor(mode, freq, appSettings.settings.value.digitalModeParams)
        return freq + (freq - probe.frequencyKhz)
    }

    private fun isDigitalModeImplemented(mode: DigitalMode): Boolean = when (mode) {
        DigitalMode.RTTY, DigitalMode.NAVTEX, DigitalMode.DSC -> localDecoderFactory.create(mode, DigitalModeParams()) != null
        else -> true
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
        decoders.stop()
        dsp.flush() // salva subito una modifica DSP ancora in attesa del debounce
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
