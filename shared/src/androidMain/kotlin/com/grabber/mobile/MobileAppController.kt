package com.grabber.mobile

import android.content.Context
import com.grabber.app.AppLogger
import com.grabber.app.FavoriteFrequency
import com.grabber.app.LogLevel
import com.grabber.app.WaterfallCoordinator
import com.grabber.app.bandGroupFor
import com.grabber.audio.squelch.SquelchController
import com.grabber.kiwi.KiwiClient
import com.grabber.kiwi.KiwiUser
import com.grabber.kiwi.OkHttpWebSocketConnector
import com.grabber.license.AndroidDeviceId
import com.grabber.license.BouncyCastleEd25519Verifier
import com.grabber.license.FeatureGate
import com.grabber.license.GatedFeature
import com.grabber.license.LicenseChecker
import com.grabber.model.ConnectionState
import com.grabber.model.KiwiDxLabel
import com.grabber.model.ModulationMode
import com.grabber.waterfall.WaterfallBuffer
import com.grabber.waterfall.WaterfallClient
import com.grabber.waterfall.WaterfallFrame
import com.grabber.waterfall.WaterfallPaletteType
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "MobileAppController"
private const val MAX_FREQUENCY_KHZ = 30000.0

/**
 * Punto di accesso unico della UI Android, con le stesse regole di `KiwiAppController` desktop:
 * la UI usa solo queste API e questi `StateFlow`, mai i servizi sottostanti. Unica eccezione,
 * come su desktop, [waterfallBuffer] letto direttamente dal renderer per prestazioni.
 *
 * Il controller vive per tutto il processo (creato dall'`Application`); il foreground service
 * tiene vivo il processo durante l'ascolto in background quando la licenza è attiva.
 */
class MobileAppController(
    context: Context,
    appVersion: String
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            AppLogger.error(TAG, "Errore non gestito in una coroutine del controller", error)
        }
    )

    private val settingsStore = MobileSettings(appContext.filesDir, KeystoreSecretCipher())
    private val connector = OkHttpWebSocketConnector()
    private val userAgent = "Mozilla/5.0 (Linux; Android) KiwiGrabberAndroid/$appVersion"
    private val client = KiwiClient(connector, userAgent = userAgent)
    private val waterfallClient = WaterfallClient(connector, clientName = "KiwiGrabberAndroidWF", userAgent = userAgent)
    // VAD disattivato su Android: elaborazione pesante esclusa dall'ambito mobile.
    private val squelch = SquelchController(vad = null)
    private val audioOutput = AudioTrackOutput()
    private val audioPolicy = AudioSessionPolicy(appContext, audioOutput) { audioOutput.setMuted(true) }
    private val networkMonitor = NetworkMonitor(appContext) { client.retryReconnectNow() }

    private val licenseChecker = LicenseChecker({ AndroidDeviceId.read(appContext) }, BouncyCastleEd25519Verifier)
    private val featureGate = FeatureGate { licenseChecker.isValidForCurrentDevice(settingsStore.value.licenseKey) }

    /** Buffer di pixel letto direttamente dal renderer del waterfall. */
    val waterfallBuffer = WaterfallBuffer(width = 1024, height = 200)
    private val waterfall = WaterfallCoordinator(scope, client, waterfallClient, waterfallBuffer, settingsStore)

    private val recorder = MediaStoreWavRecorder(
        context = appContext,
        scope = scope,
        onPendingUriChanged = { uri -> settingsStore.update { it.copy(pendingRecordingUri = uri) } },
        onStoppedForLowSpace = { notify("Registrazione interrotta: spazio di archiviazione quasi esaurito.") }
    )

    private val _notifications = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Messaggi brevi per la UI (snackbar): errori di connessione, rifiuti del gate, avvisi. */
    val notifications: SharedFlow<String> = _notifications.asSharedFlow()

    private val _lockedFeatureRequests = MutableSharedFlow<GatedFeature>(extraBufferCapacity = 4)
    /** Funzione richiesta senza attivazione: la UI propone il collegamento alla sezione Licenza. */
    val lockedFeatureRequests: SharedFlow<GatedFeature> = _lockedFeatureRequests.asSharedFlow()

    private val _isActivated = MutableStateFlow(false)
    val isActivated: StateFlow<Boolean> = _isActivated.asStateFlow()

    val machineId: String get() = licenseChecker.machineId

    val settings: StateFlow<MobileSettingsData> = settingsStore.settings
    val connectionState: StateFlow<ConnectionState> = client.connectionState
    val currentFrequencyKhz: StateFlow<Double> = client.currentFrequencyKhz
    val currentMode: StateFlow<ModulationMode> = client.currentMode
    val rssi: StateFlow<Double> = client.rssi
    val activeUsers: StateFlow<List<KiwiUser>> = client.activeUsers
    val serverDxLabels: StateFlow<List<KiwiDxLabel>> = client.serverDxLabels

    val volume: StateFlow<Float> = audioOutput.volume
    val isMuted: StateFlow<Boolean> = audioOutput.isMuted
    val squelchGateOpen: StateFlow<Boolean> = squelch.isGateOpen
    val squelchEnabled: StateFlow<Boolean> = squelch.listenSquelchEnabled
    val squelchThresholdDbm: StateFlow<Double> = squelch.thresholdDbm

    val isRecording: StateFlow<Boolean> = recorder.isRecording
    val recordingElapsedSeconds: StateFlow<Long> = recorder.elapsedSeconds
    val recordingBytes: StateFlow<Long> = recorder.bytesWritten
    val recordingFileName: StateFlow<String?> = recorder.fileName

    val waterfallState: StateFlow<ConnectionState> = waterfallClient.connectionState
    val waterfallZoom: StateFlow<Int> = waterfallClient.currentZoom
    val waterfallCenterFreqKhz: StateFlow<Double> = waterfallClient.centerFrequencyKhz
    val waterfallMinDb: StateFlow<Int> = waterfallClient.minDb
    val waterfallMaxDb: StateFlow<Int> = waterfallClient.maxDb
    val latestWaterfallFrame: StateFlow<WaterfallFrame?> = waterfallClient.latestFrame
    val waterfallBufferVersion: StateFlow<Long> = waterfallBuffer.version
    val waterfallPalette: StateFlow<WaterfallPaletteType> = waterfall.palette
    val waterfallAutoFollow: StateFlow<Boolean> = waterfall.autoFollow

    /** Vero quando serve il foreground service: sessione attiva e ascolto in background consentito. */
    val needsForegroundService: StateFlow<Boolean> =
        combine(client.connectionState, _isActivated) { state, activated ->
            activated && state.isSessionActive()
        }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, false)

    @Volatile private var lastSampleRate = 12_000
    @Volatile private var isAppVisible = true
    @Volatile private var isWaterfallVisible = false
    @Volatile private var stoppedInBackground = false

    init {
        AppLogger.init(File(appContext.filesDir, "logs"), LogLevel.WARN)
        refreshActivation()
        applyPersistedSettings(settingsStore.value)
        settingsStore.value.pendingRecordingUri?.let { uri -> scope.launch(Dispatchers.IO) { recorder.repairPending(uri) } }
        waterfall.start()
        startAudioPipeline()
        observeConnection()
        observeErrors()
        observeMeteredNetwork()
    }

    // ── Inizializzazione ─────────────────────────────────────────

    private fun applyPersistedSettings(data: MobileSettingsData) {
        client.updateServer(data.serverHost, data.serverPort)
        waterfall.updateServer(data.serverHost, data.serverPort)
        client.updatePassword(data.serverPassword)
        waterfall.updatePassword(data.serverPassword)
        client.clientName = data.callsign
        audioOutput.setVolume(data.volume)
        squelch.setThresholdDbm(data.squelchThresholdDbm)
        squelch.setListenSquelchEnabled(data.squelchEnabled)
        val frequency = data.lastFrequencyKhz.coerceIn(0.0, MAX_FREQUENCY_KHZ)
        client.tune(frequency, data.lastMode)
        waterfall.applyPersistedSettings(
            clientName = "${data.callsign}-WF",
            minDb = data.waterfallMinDb,
            maxDb = data.waterfallMaxDb,
            speed = data.waterfallSpeed,
            zoom = data.waterfallZoom,
            initialFrequencyKhz = frequency
        )
    }

    private fun startAudioPipeline() {
        audioOutput.start()
        client.audioFlow
            .onEach { frame ->
                lastSampleRate = frame.sampleRate
                val result = squelch.processFrame(frame)
                audioOutput.submit(result.processedPcmBytes, frame.sampleRate)
                recorder.submit(frame.pcmBytes)
            }
            .launchIn(scope)
    }

    private fun observeConnection() {
        client.connectionState
            .onEach { state ->
                if (state !is ConnectionState.Connected && recorder.isRecording.value) {
                    recorder.stop()
                    notify("Registrazione interrotta: connessione al ricevitore persa. Il file è stato salvato.")
                }
                when (state) {
                    is ConnectionState.Connected -> updateWaterfallStream()
                    is ConnectionState.Disconnected, is ConnectionState.Error -> {
                        waterfall.closeSocket()
                        audioOutput.flush()
                        audioPolicy.deactivate()
                        networkMonitor.stop()
                    }
                    is ConnectionState.Reconnecting -> waterfall.closeSocket()
                    is ConnectionState.Connecting -> Unit
                }
            }
            .launchIn(scope)
    }

    private fun observeErrors() {
        merge(
            client.connectionErrors.map { "Audio/controllo: ${it.ifBlank { "errore senza dettagli" }}" },
            waterfallClient.connectionErrors.map { "Waterfall: ${it.ifBlank { "errore senza dettagli" }}" }
        )
            .distinctUntilChanged()
            .onEach { _notifications.emit(it) }
            .launchIn(scope)
    }

    /** Sulla rete a consumo il waterfall usa la velocità minima, indipendentemente dall'impostazione. */
    private fun observeMeteredNetwork() {
        combine(networkMonitor.isMetered, settingsStore.settings.map { it.waterfallSpeed }) { metered, speed ->
            if (metered) 1 else speed
        }
            .distinctUntilChanged()
            .onEach { waterfallClient.setSpeed(it) }
            .launchIn(scope)
    }

    // ── Connessione e sintonia ───────────────────────────────────

    fun connect() {
        stoppedInBackground = false
        val data = settingsStore.value
        client.updateServer(data.serverHost, data.serverPort)
        waterfall.updateServer(data.serverHost, data.serverPort)
        audioPolicy.activate()
        networkMonitor.start()
        client.connect(scope)
        updateWaterfallStream()
    }

    fun disconnect() {
        if (recorder.isRecording.value) recorder.stop()
        waterfall.stopStream()
        client.disconnect()
    }

    fun tune(freqKhz: Double, mode: ModulationMode? = null) {
        val bounded = freqKhz.coerceIn(0.0, MAX_FREQUENCY_KHZ)
        val targetMode = mode ?: currentMode.value
        squelch.reset()
        client.tune(bounded, targetMode)
        settingsStore.update { it.copy(lastFrequencyKhz = bounded, lastModeCode = targetMode.code) }
        waterfall.followTuning(bounded)
    }

    fun stepFrequency(steps: Int) = tune(currentFrequencyKhz.value + steps * settingsStore.value.frequencyStepKhz)

    fun setFrequencyStep(stepKhz: Double) = settingsStore.update { it.copy(frequencyStepKhz = stepKhz) }

    fun setMode(mode: ModulationMode) {
        client.setMode(mode)
        settingsStore.update { it.copy(lastModeCode = mode.code) }
    }

    // ── Audio e squelch ──────────────────────────────────────────

    fun setVolume(value: Float) {
        audioOutput.setVolume(value)
        settingsStore.update { it.copy(volume = audioOutput.volume.value) }
    }

    fun toggleMute() = audioOutput.setMuted(!audioOutput.isMuted.value)

    fun setSquelchEnabled(enabled: Boolean) {
        squelch.setListenSquelchEnabled(enabled)
        settingsStore.update { it.copy(squelchEnabled = enabled) }
    }

    fun setSquelchThreshold(dbm: Double) {
        squelch.setThresholdDbm(dbm)
        settingsStore.update { it.copy(squelchThresholdDbm = squelch.thresholdDbm.value) }
    }

    // ── Waterfall ────────────────────────────────────────────────

    /** Il socket W/F resta aperto solo con la schermata waterfall visibile e l'app in primo piano. */
    fun setWaterfallVisible(visible: Boolean) {
        isWaterfallVisible = visible
        updateWaterfallStream()
    }

    private fun updateWaterfallStream() {
        val wanted = isWaterfallVisible && isAppVisible && client.connectionState.value.isSessionActive()
        if (wanted) {
            waterfall.requestStream()
            waterfall.connectIfSessionReady()
        } else {
            waterfall.stopStream()
        }
    }

    fun tuneFromWaterfall(freqKhz: Double) = tune(freqKhz)

    fun setWaterfallCenterFrequency(cfKhz: Double) {
        if (waterfall.autoFollow.value) waterfall.setAutoFollow(false, currentFrequencyKhz.value)
        waterfall.setCenterFrequency(cfKhz)
    }

    fun setWaterfallZoom(zoom: Int) = waterfall.setZoom(zoom)

    fun waterfallZoomIn() = waterfall.zoomBy(1)

    fun waterfallZoomOut() = waterfall.zoomBy(-1)

    fun setWaterfallPalette(palette: WaterfallPaletteType) = waterfall.setPalette(palette)

    fun setWaterfallDbRange(minDb: Int, maxDb: Int) = waterfall.setDbRange(minDb, maxDb)

    fun setWaterfallAutoFollow(autoFollow: Boolean) = waterfall.setAutoFollow(autoFollow, currentFrequencyKhz.value)

    // ── Preferiti ────────────────────────────────────────────────

    fun requestDxLabels() = client.requestDxLabels()

    fun addCurrentAsFavorite(customName: String? = null): Boolean {
        val freq = currentFrequencyKhz.value
        val mode = currentMode.value
        val name = customName?.takeIf { it.isNotBlank() }?.trim() ?: "${mode.displayName} ${formatKhz(freq)} kHz"
        return addFavorite(FavoriteFrequency(name, freq, mode.code, bandGroupFor(freq)))
    }

    fun addFavorite(favorite: FavoriteFrequency): Boolean {
        if (!requireFeature(GatedFeature.PERSONAL_FAVORITES)) return false
        settingsStore.update { it.copy(personalFavorites = it.personalFavorites + favorite) }
        return true
    }

    fun editFavorite(old: FavoriteFrequency, new: FavoriteFrequency): Boolean {
        if (!requireFeature(GatedFeature.PERSONAL_FAVORITES)) return false
        settingsStore.update { data ->
            val index = data.personalFavorites.indexOf(old)
            if (index < 0) data else data.copy(personalFavorites = data.personalFavorites.toMutableList().also { it[index] = new })
        }
        return true
    }

    /** Senza attivazione i preferiti già salvati restano sintonizzabili ma non modificabili. */
    fun removeFavorite(favorite: FavoriteFrequency): Boolean {
        if (!requireFeature(GatedFeature.PERSONAL_FAVORITES)) return false
        settingsStore.update { it.copy(personalFavorites = it.personalFavorites - favorite) }
        return true
    }

    // ── Registrazione ────────────────────────────────────────────

    fun startRecording() {
        if (!requireFeature(GatedFeature.MANUAL_RECORDING)) return
        if (client.connectionState.value !is ConnectionState.Connected) {
            notify("Connettiti al ricevitore per registrare.")
            return
        }
        recorder.start(lastSampleRate, currentFrequencyKhz.value, currentMode.value)?.let(::notify)
    }

    fun stopRecording() = recorder.stop()

    // ── Impostazioni e licenza ───────────────────────────────────

    fun setServerAddress(address: String) {
        val clean = address.trim().ifEmpty { com.grabber.app.DEFAULT_SERVER_ADDRESS }
        settingsStore.update { it.copy(serverAddress = clean) }
        val data = settingsStore.value
        client.updateServer(data.serverHost, data.serverPort)
        waterfall.updateServer(data.serverHost, data.serverPort)
    }

    fun setServerPassword(password: String) {
        settingsStore.update { it.copy(serverPassword = password) }
        client.updatePassword(password)
        waterfall.updatePassword(password)
    }

    fun setCallsign(callsign: String) {
        val clean = callsign.trim().ifEmpty { DEFAULT_ANDROID_CLIENT_NAME }
        settingsStore.update { it.copy(callsign = clean) }
        client.setIdentUser(clean)
    }

    /** Salva il codice solo se valido per questo dispositivo; altrimenti la licenza resta invariata. */
    fun activateLicense(licenseKey: String): Boolean {
        val clean = licenseKey.trim()
        if (!licenseChecker.isValidForCurrentDevice(clean)) return false
        settingsStore.update { it.copy(licenseKey = clean) }
        refreshActivation()
        return true
    }

    private fun refreshActivation() {
        _isActivated.value = featureGate.isActivated
    }

    private fun requireFeature(feature: GatedFeature): Boolean {
        if (featureGate.isEnabled(feature)) return true
        _lockedFeatureRequests.tryEmit(feature)
        return false
    }

    // ── Ciclo di vita ────────────────────────────────────────────

    /**
     * L'app passa in primo piano o in background. Senza attivazione l'ascolto si ferma in
     * background (la connessione viene chiusa, liberando lo slot del ricevitore) e l'utente
     * viene avvisato al ritorno.
     */
    fun onAppVisibilityChanged(visible: Boolean) {
        isAppVisible = visible
        if (!visible && client.connectionState.value.isSessionActive() &&
            !featureGate.isEnabled(GatedFeature.BACKGROUND_PLAYBACK)
        ) {
            AppLogger.info(TAG, "App in background senza attivazione: ascolto interrotto")
            disconnect()
            stoppedInBackground = true
        }
        if (visible && stoppedInBackground) {
            stoppedInBackground = false
            notify("Ascolto interrotto in background. ${GatedFeature.BACKGROUND_PLAYBACK.lockedMessage}")
        }
        updateWaterfallStream()
    }

    /** Task rimosso dai recenti o "Disconnetti" dalla notifica: chiusura pulita della sessione. */
    fun stopSession() = disconnect()

    private fun notify(message: String) {
        _notifications.tryEmit(message)
    }

    private fun ConnectionState.isSessionActive(): Boolean =
        this is ConnectionState.Connected || this is ConnectionState.Connecting || this is ConnectionState.Reconnecting

    private fun formatKhz(freq: Double): String =
        if (freq % 1.0 == 0.0) freq.toLong().toString() else "%.1f".format(java.util.Locale.US, freq)
}
