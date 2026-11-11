package com.grabber.audio.squelch

import com.grabber.app.AppLogger
import com.grabber.audio.vad.HfSpectralVad
import com.grabber.audio.vad.VadResult
import com.grabber.audio.vad.VoiceActivityDetector
import com.grabber.model.AudioFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Stati dell'automa a stati finiti (FSM) del Gate Squelch.
 */
enum class SquelchGateState(val label: String) {
    CLOSED("CHIUSO"),
    OPEN_ACTIVE("APERTO (TX)"),
    OPEN_HANG_TIME("HANG TIME")
}

/**
 * Risultato dell'elaborazione di un frame audio attraverso lo Squelch e il Voice Activity Detector.
 */
data class SquelchAudioResult(
    val state: SquelchGateState,
    val isGateOpen: Boolean,
    val flushedPreBuffer: ByteArray? = null,
    val processedPcmBytes: ByteArray,
    val vadResult: VadResult? = null,
    val isVoiceGateOpen: Boolean = false
)

/**
 * Controller Squelch RF integrato con Voice Activity Detection (VAD / Voice-Gate),
 * automa a stati finiti (FSM), rampa di soft mute e buffer circolare anti-troncamento.
 */
class SquelchController(
    val sampleRate: Int = 12000,
    initialThresholdDbm: Double = -103.0, // Default S4 (-103 dBm)
    initialHysteresisDb: Double = 1.5,
    initialSoftMuteMs: Int = 10,
    initialPreBufferMs: Int = 400,
    initialHangTimeSeconds: Double = 3.5,
    val vad: VoiceActivityDetector = HfSpectralVad(sampleRate = sampleRate)
) {
    companion object {
        private const val TAG = "SquelchController"
    }

    private val _state = MutableStateFlow(SquelchGateState.CLOSED)
    val state: StateFlow<SquelchGateState> = _state.asStateFlow()

    private val _isGateOpen = MutableStateFlow(false)
    val isGateOpen: StateFlow<Boolean> = _isGateOpen.asStateFlow()

    private val _hangTimeRemaining = MutableStateFlow(0.0)
    val hangTimeRemaining: StateFlow<Double> = _hangTimeRemaining.asStateFlow()

    // Soglia Squelch in dBm
    private val _thresholdDbm = MutableStateFlow(initialThresholdDbm)
    val thresholdDbm: StateFlow<Double> = _thresholdDbm.asStateFlow()

    // Isteresi in dB per evitare oscillazioni rapide (chatter)
    var hysteresisDb: Double = initialHysteresisDb

    // Abilitazioni Squelch RF indipendenti per Ascolto e Registrazione
    private val _listenSquelchEnabled = MutableStateFlow(false)
    val listenSquelchEnabled: StateFlow<Boolean> = _listenSquelchEnabled.asStateFlow()

    private val _recordSquelchEnabled = MutableStateFlow(false)
    val recordSquelchEnabled: StateFlow<Boolean> = _recordSquelchEnabled.asStateFlow()

    // Abilitazioni Voice-Gate (VAD) indipendenti per Ascolto e Registrazione
    private val _vadListenEnabled = MutableStateFlow(false)
    val vadListenEnabled: StateFlow<Boolean> = _vadListenEnabled.asStateFlow()

    private val _vadRecordEnabled = MutableStateFlow(false)
    val vadRecordEnabled: StateFlow<Boolean> = _vadRecordEnabled.asStateFlow()

    // Stati reattivi VAD per UI
    private val _isVoiceDetected = MutableStateFlow(false)
    val isVoiceDetected: StateFlow<Boolean> = _isVoiceDetected.asStateFlow()

    private val _vadConfidence = MutableStateFlow(0f)
    val vadConfidence: StateFlow<Float> = _vadConfidence.asStateFlow()
    private var lastVadConfidenceEmitTime = 0L

    private val _isQrnDetected = MutableStateFlow(false)
    val isQrnDetected: StateFlow<Boolean> = _isQrnDetected.asStateFlow()

    private val _lastVadResult = MutableStateFlow<VadResult?>(null)
    val lastVadResult: StateFlow<VadResult?> = _lastVadResult.asStateFlow()

    var softMuteMs: Int = initialSoftMuteMs
        set(value) {
            field = value.coerceIn(2, 50)
            listenFader.setFadeDurationMs(field)
            recordFader.setFadeDurationMs(field)
        }

    var preBufferMs: Int = initialPreBufferMs
        set(value) {
            field = value.coerceIn(50, 1000)
        }

    var hangTimeSeconds: Double = initialHangTimeSeconds
        set(value) {
            field = value.coerceIn(0.5, 10.0)
        }

    // Componenti DSP interni
    private val preBuffer = CircularAudioBuffer(sampleRate = sampleRate, maxDurationMs = 1500)
    private val listenFader = SoftMuteFader(sampleRate = sampleRate, fadeDurationMs = initialSoftMuteMs)
    private val recordFader = SoftMuteFader(sampleRate = sampleRate, fadeDurationMs = initialSoftMuteMs)

    private var cooldownEndMs: Long = 0L
    private var isRecordingSegmentTriggered = false

    // Callback per la registrazione intelligente
    var onGateOpenCallback: ((preBufferBytes: ByteArray) -> Unit)? = null
    var onGateCloseCallback: (() -> Unit)? = null
    var onQsoStartedCallback: (() -> Unit)? = null
    var onQsoEndedCallback: (() -> Unit)? = null

    init {
        listenFader.reset(open = false)
        recordFader.reset(open = false)
    }

    fun setThresholdDbm(dbm: Double) {
        _thresholdDbm.value = dbm.coerceIn(SMeterUtils.MIN_DBM, SMeterUtils.MAX_DBM)
    }

    fun setListenSquelchEnabled(enabled: Boolean) {
        _listenSquelchEnabled.value = enabled
        updateListenFaderTarget()
        AppLogger.info(TAG, "Squelch Ascolto impostato a: $enabled")
    }

    fun setRecordSquelchEnabled(enabled: Boolean) {
        _recordSquelchEnabled.value = enabled
        AppLogger.info(TAG, "Squelch Registrazione impostato a: $enabled")
    }

    fun setVadListenEnabled(enabled: Boolean) {
        _vadListenEnabled.value = enabled
        updateListenFaderTarget()
        AppLogger.info(TAG, "Voice Gate Ascolto impostato a: $enabled")
    }

    fun setVadRecordEnabled(enabled: Boolean) {
        _vadRecordEnabled.value = enabled
        AppLogger.info(TAG, "Voice Gate Registrazione impostato a: $enabled")
    }

    fun setVadSensitivity(sensitivity: Float) {
        vad.sensitivityThreshold = sensitivity.coerceIn(0.1f, 0.9f)
    }

    fun setVadHangoverMs(ms: Int) {
        vad.hangoverMs = ms.coerceIn(50, 1000)
    }

    private fun updateListenFaderTarget() {
        val listenSql = _listenSquelchEnabled.value
        val vadListen = _vadListenEnabled.value
        val rfOpen = _isGateOpen.value
        val voice = _isVoiceDetected.value

        val targetOpen = when {
            listenSql -> rfOpen
            vadListen -> voice
            else -> true
        }
        listenFader.setTargetOpen(targetOpen)
    }

    /**
     * Elabora il frame audio in ingresso aggiornando la macchina a stati Squelch e il VAD.
     * Restituisce i campioni processati e l'eventuale buffer circolare da svuotare in caso di apertura.
     */
    fun processFrame(frame: AudioFrame): SquelchAudioResult {
        val now = System.currentTimeMillis()
        val currentRssi = frame.rssi
        val threshold = _thresholdDbm.value
        val currentState = _state.value

        // 1. Analisi Voice Activity Detection sul blocco PCM
        val vadRes = vad.process(frame.pcmBytes)
        _lastVadResult.value = vadRes

        val hasVoice = vadRes.isVoice
        val isQrn = vadRes.isImpulseNoise

        val voiceStateChanged = _isVoiceDetected.value != hasVoice
        if (voiceStateChanged) {
            _isVoiceDetected.value = hasVoice
        }
        if (_isQrnDetected.value != isQrn) {
            _isQrnDetected.value = isQrn
        }
        // Throttling StateFlow confidenza per evitare ricomposizioni Compose eccessive (Regola 6) - min 60ms
        if (voiceStateChanged || (now - lastVadConfidenceEmitTime >= 60L && abs(_vadConfidence.value - vadRes.confidence) >= 0.05f)) {
            _vadConfidence.value = vadRes.confidence
            lastVadConfidenceEmitTime = now
        }

        var flushedBytes: ByteArray? = null
        var nextState = currentState

        // 2. FSM RF Squelch
        when (currentState) {
            SquelchGateState.CLOSED -> {
                if (currentRssi >= threshold) {
                    // Segnale presente sopra la soglia: IL GATE RF SI APRE!
                    nextState = SquelchGateState.OPEN_ACTIVE
                    _isGateOpen.value = true
                    _hangTimeRemaining.value = 0.0

                    // Estraiamo il pre-buffer PRIMA di scrivere il frame corrente (Regola 7: Anti-Echo)
                    val rawPreBuffer = preBuffer.snapshot(preBufferMs)
                    if (rawPreBuffer.isNotEmpty()) {
                        val preFader = SoftMuteFader(sampleRate = sampleRate, fadeDurationMs = softMuteMs)
                        preFader.reset(open = false)
                        preFader.setTargetOpen(true)
                        flushedBytes = preFader.process(rawPreBuffer)
                    }

                    recordFader.setTargetOpen(true)
                    AppLogger.debug(TAG, "Squelch RF APERTO (RSSI: %.1f dBm >= Soglia: %.1f dBm, VAD Voice: $hasVoice, QRN: $isQrn)".format(currentRssi, threshold))

                    // Decisione apertura registrazione: se VAD è attivo, apre solo se c'è voce
                    val shouldTriggerRec = !_vadRecordEnabled.value ||
                        _recordSquelchEnabled.value ||
                        hasVoice

                    if (shouldTriggerRec) {
                        isRecordingSegmentTriggered = true
                        onGateOpenCallback?.invoke(flushedBytes ?: ByteArray(0))
                        onQsoStartedCallback?.invoke()
                    } else {
                        isRecordingSegmentTriggered = false
                    }

                    // Scrittura nel ring buffer DOPO lo snapshot
                    preBuffer.write(frame.pcmBytes)
                } else {
                    // Gate resta chiuso: bufferizziamo nel ring buffer circolare
                    preBuffer.write(frame.pcmBytes)
                }
            }

            SquelchGateState.OPEN_ACTIVE -> {
                preBuffer.write(frame.pcmBytes)

                // Se la registrazione era in attesa di voce e la voce arriva adesso
                if (_vadRecordEnabled.value &&
                    !_recordSquelchEnabled.value &&
                    !isRecordingSegmentTriggered &&
                    hasVoice
                ) {
                    val rawPreBuffer = preBuffer.snapshot(preBufferMs)
                    if (rawPreBuffer.isNotEmpty()) {
                        val preFader = SoftMuteFader(sampleRate = sampleRate, fadeDurationMs = softMuteMs)
                        preFader.reset(open = false)
                        preFader.setTargetOpen(true)
                        val flushed = preFader.process(rawPreBuffer)
                        isRecordingSegmentTriggered = true
                        AppLogger.debug(TAG, "Voice Gate REC: Voce rilevata durante RF attivo -> Inizio registrazione QSO")
                        onGateOpenCallback?.invoke(flushed)
                        onQsoStartedCallback?.invoke()
                    }
                }

                if (currentRssi >= threshold - hysteresisDb) {
                    // Il segnale permane sopra la soglia (con isteresi)
                    _hangTimeRemaining.value = 0.0
                } else {
                    // Il segnale è sceso: entriamo in HANG TIME (Cooldown FSM)
                    nextState = SquelchGateState.OPEN_HANG_TIME
                    cooldownEndMs = now + (hangTimeSeconds * 1000.0).toLong()
                    _hangTimeRemaining.value = hangTimeSeconds
                    AppLogger.debug(TAG, "Squelch HANG TIME avviato: %.1f s rimanenti".format(hangTimeSeconds))
                }
            }

            SquelchGateState.OPEN_HANG_TIME -> {
                preBuffer.write(frame.pcmBytes)

                // Se durante Hang Time compare voce su canale aperto
                if (_vadRecordEnabled.value &&
                    !_recordSquelchEnabled.value &&
                    !isRecordingSegmentTriggered &&
                    hasVoice
                ) {
                    val rawPreBuffer = preBuffer.snapshot(preBufferMs)
                    if (rawPreBuffer.isNotEmpty()) {
                        val preFader = SoftMuteFader(sampleRate = sampleRate, fadeDurationMs = softMuteMs)
                        preFader.reset(open = false)
                        preFader.setTargetOpen(true)
                        val flushed = preFader.process(rawPreBuffer)
                        isRecordingSegmentTriggered = true
                        onGateOpenCallback?.invoke(flushed)
                        onQsoStartedCallback?.invoke()
                    }
                }

                if (currentRssi >= threshold) {
                    // Il segnale è ripreso prima della scadenza: torniamo in ACTIVE
                    nextState = SquelchGateState.OPEN_ACTIVE
                    _hangTimeRemaining.value = 0.0
                    AppLogger.debug(TAG, "Squelch HANG TIME interrotto: nuova trasmissione rilevata!")
                } else if (now >= cooldownEndMs) {
                    // Tempo di Hang scaduto: CHIUSURA GATE
                    nextState = SquelchGateState.CLOSED
                    _isGateOpen.value = false
                    _hangTimeRemaining.value = 0.0
                    recordFader.setTargetOpen(false)

                    AppLogger.debug(TAG, "Squelch HANG TIME completato: GATE CHIUSO")
                    if (isRecordingSegmentTriggered) {
                        isRecordingSegmentTriggered = false
                        onGateCloseCallback?.invoke()
                        onQsoEndedCallback?.invoke()
                    }
                } else {
                    // Hang time in corso: throttling countdown UI (Regola 6)
                    val rem = (cooldownEndMs - now).coerceAtLeast(0L) / 1000.0
                    if (abs(_hangTimeRemaining.value - rem) >= 0.1) {
                        _hangTimeRemaining.value = rem
                    }
                }
            }
        }

        if (nextState != currentState) {
            _state.value = nextState
        }

        // 3. Decisione Gate di Ascolto (combinazione RF Squelch + Voice Gate VAD)
        val listenSql = _listenSquelchEnabled.value
        val vadListen = _vadListenEnabled.value
        val rfOpen = _isGateOpen.value

        val effectiveListenGateOpen = when {
            listenSql -> rfOpen
            vadListen -> hasVoice
            else -> true
        }

        listenFader.setTargetOpen(effectiveListenGateOpen)

        // Se almeno uno tra Squelch ascolto o VAD ascolto è attivo, applichiamo il Soft Mute Fader
        val playbackBytes = if (listenSql || vadListen) {
            listenFader.process(frame.pcmBytes)
        } else {
            frame.pcmBytes
        }

        return SquelchAudioResult(
            state = nextState,
            isGateOpen = _isGateOpen.value,
            flushedPreBuffer = flushedBytes,
            processedPcmBytes = playbackBytes,
            vadResult = vadRes,
            isVoiceGateOpen = effectiveListenGateOpen
        )
    }

    /**
     * Resetta lo stato interno dello Squelch e del VAD (es. al cambio frequenza o disconnessione).
     */
    fun reset() {
        _state.value = SquelchGateState.CLOSED
        _isGateOpen.value = false
        _hangTimeRemaining.value = 0.0
        _isVoiceDetected.value = false
        _isQrnDetected.value = false
        _vadConfidence.value = 0f
        isRecordingSegmentTriggered = false
        preBuffer.clear()
        vad.reset()
        updateListenFaderTarget()
        recordFader.reset(open = false)
    }
}
