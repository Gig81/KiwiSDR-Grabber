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
    /** Pre-buffer consegnato al registratore se in questo frame si è aperto un segmento. */
    val flushedPreBuffer: ByteArray? = null,
    val processedPcmBytes: ByteArray,
    val vadResult: VadResult? = null,
    /** Stato del gate di ascolto secondo la modalità corrente (sempre aperto con [GateMode.OFF]). */
    val isVoiceGateOpen: Boolean = false
)

/**
 * Controller Squelch RF integrato con Voice Activity Detection (VAD / Voice-Gate),
 * automa a stati finiti (FSM), rampa di soft mute e buffer circolare anti-troncamento.
 *
 * Il gate RF ([state], [isGateOpen]) segue sempre la soglia RSSI e serve anche alla scansione.
 * Ascolto e registrazione hanno ciascuno una [GateMode]:
 *
 * - **Ascolto:** `OFF` audio libero, `RF` gate RF, `VOICE` voce rilevata, `RF_AND_VOICE` entrambi.
 * - **Registrazione** (segmenti QSO): `RF` apre con il gate RF e chiude alla fine dell'hang time;
 *   `VOICE` apre alla prima voce e chiude dopo [hangTimeSeconds] senza voce; `RF_AND_VOICE` apre
 *   quando c'è voce con il gate RF aperto e chiude alla chiusura del gate RF oppure, se
 *   [voiceSilenceCloseSeconds] > 0, dopo quel tempo senza voce anche con il gate RF ancora aperto.
 *   Con `OFF` i segmenti seguono il gate RF solo per le notifiche QSO: il registratore non è armato.
 *
 * Alla chiusura di un segmento [onGateCloseCallback] riceve la durata "attiva" (dall'apertura
 * all'ultimo istante con segnale o voce), cioè al netto della coda di hang time o di silenzio.
 */
class SquelchController(
    val sampleRate: Int = 12000,
    initialThresholdDbm: Double = -103.0, // Default S4 (-103 dBm)
    initialHysteresisDb: Double = 1.5,
    initialSoftMuteMs: Int = 10,
    initialPreBufferMs: Int = 400,
    initialHangTimeSeconds: Double = 3.5,
    /** Voice Activity Detector; `null` lo disattiva (es. su Android, per ridurre i consumi). */
    val vad: VoiceActivityDetector? = HfSpectralVad(sampleRate = sampleRate),
    private val clock: () -> Long = System::currentTimeMillis
) {
    companion object {
        private const val TAG = "SquelchController"
        const val MIN_VOICE_SILENCE_CLOSE_SECONDS = 1.0
        const val MAX_VOICE_SILENCE_CLOSE_SECONDS = 30.0
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

    // Modalità del gate, indipendenti per Ascolto e Registrazione
    private val _listenMode = MutableStateFlow(GateMode.OFF)
    val listenMode: StateFlow<GateMode> = _listenMode.asStateFlow()

    private val _recordMode = MutableStateFlow(GateMode.OFF)
    val recordMode: StateFlow<GateMode> = _recordMode.asStateFlow()

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
        }

    var preBufferMs: Int = initialPreBufferMs
        set(value) {
            field = value.coerceIn(50, 1000)
        }

    var hangTimeSeconds: Double = initialHangTimeSeconds
        set(value) {
            field = value.coerceIn(0.5, 10.0)
        }

    /**
     * Con [GateMode.RF_AND_VOICE] in registrazione chiude il segmento dopo questo tempo senza voce,
     * anche se il gate RF è ancora aperto (portanti, rumore). 0 disattiva la chiusura anticipata.
     */
    var voiceSilenceCloseSeconds: Double = 0.0
        set(value) {
            field = if (value <= 0.0) 0.0 else value.coerceIn(MIN_VOICE_SILENCE_CLOSE_SECONDS, MAX_VOICE_SILENCE_CLOSE_SECONDS)
        }

    // Componenti DSP interni
    private val preBuffer = CircularAudioBuffer(sampleRate = sampleRate, maxDurationMs = 1500)
    private val listenFader = SoftMuteFader(sampleRate = sampleRate, fadeDurationMs = initialSoftMuteMs)

    private var cooldownEndMs: Long = 0L

    // Segmento di registrazione (QSO) in corso
    private var isSegmentOpen = false
    private var segmentStartMs = 0L
    private var segmentLastActiveMs = 0L
    private var lastVoiceMs = 0L

    // Callback per la registrazione intelligente
    var onGateOpenCallback: ((preBufferBytes: ByteArray) -> Unit)? = null
    /** Chiusura del segmento, con la durata attiva in millisecondi (senza la coda di hang time o silenzio). */
    var onGateCloseCallback: ((activeMillis: Long) -> Unit)? = null
    var onQsoStartedCallback: (() -> Unit)? = null
    var onQsoEndedCallback: (() -> Unit)? = null

    init {
        listenFader.reset(open = false)
    }

    fun setThresholdDbm(dbm: Double) {
        _thresholdDbm.value = dbm.coerceIn(SMeterUtils.MIN_DBM, SMeterUtils.MAX_DBM)
    }

    fun setListenMode(mode: GateMode) {
        _listenMode.value = mode
        updateListenFaderTarget()
        AppLogger.info(TAG, "Modalità gate Ascolto impostata a: $mode")
    }

    fun setRecordMode(mode: GateMode) {
        _recordMode.value = mode
        AppLogger.info(TAG, "Modalità gate Registrazione impostata a: $mode")
    }

    fun setVadSensitivity(sensitivity: Float) {
        vad?.sensitivityThreshold = sensitivity.coerceIn(0.1f, 0.9f)
    }

    fun setVadHangoverMs(ms: Int) {
        vad?.hangoverMs = ms.coerceIn(50, 1000)
    }

    private fun listenGateOpen(mode: GateMode, rfOpen: Boolean, voice: Boolean): Boolean = when (mode) {
        GateMode.OFF -> true
        GateMode.RF -> rfOpen
        GateMode.VOICE -> voice
        GateMode.RF_AND_VOICE -> rfOpen && voice
    }

    private fun updateListenFaderTarget() {
        listenFader.setTargetOpen(listenGateOpen(_listenMode.value, _isGateOpen.value, _isVoiceDetected.value))
    }

    /**
     * Elabora il frame audio in ingresso aggiornando la macchina a stati Squelch e il VAD.
     * Restituisce i campioni processati e l'eventuale buffer circolare da svuotare in caso di apertura.
     */
    fun processFrame(frame: AudioFrame): SquelchAudioResult {
        val now = clock()

        // 1. Analisi Voice Activity Detection sul blocco PCM
        val vadRes = vad?.process(frame.pcmBytes)
        _lastVadResult.value = vadRes

        val hasVoice = vadRes?.isVoice == true
        val isQrn = vadRes?.isImpulseNoise == true

        val voiceStateChanged = _isVoiceDetected.value != hasVoice
        if (voiceStateChanged) {
            _isVoiceDetected.value = hasVoice
        }
        if (_isQrnDetected.value != isQrn) {
            _isQrnDetected.value = isQrn
        }
        // Throttling StateFlow confidenza per evitare ricomposizioni Compose eccessive (Regola 6) - min 60ms
        if (voiceStateChanged || (now - lastVadConfidenceEmitTime >= 60L && abs(_vadConfidence.value - (vadRes?.confidence ?: 0f)) >= 0.05f)) {
            _vadConfidence.value = vadRes?.confidence ?: 0f
            lastVadConfidenceEmitTime = now
        }

        // 2. FSM RF Squelch
        val nextState = updateRfGate(now, frame.rssi, hasVoice, isQrn)

        // 3. Segmento di registrazione secondo la modalità. Il pre-buffer va estratto PRIMA di
        // scriverci il frame corrente, che arriva al registratore subito dopo (Regola 7: Anti-Echo).
        val flushedBytes = updateRecordSegment(now, hasVoice, rfActive = nextState == SquelchGateState.OPEN_ACTIVE)
        preBuffer.write(frame.pcmBytes)

        // 4. Gate di Ascolto secondo la modalità
        val listenMode = _listenMode.value
        val effectiveListenGateOpen = listenGateOpen(listenMode, _isGateOpen.value, hasVoice)
        listenFader.setTargetOpen(effectiveListenGateOpen)

        val playbackBytes = if (listenMode != GateMode.OFF) {
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

    private fun updateRfGate(now: Long, currentRssi: Double, hasVoice: Boolean, isQrn: Boolean): SquelchGateState {
        val threshold = _thresholdDbm.value
        val currentState = _state.value
        var nextState = currentState

        when (currentState) {
            SquelchGateState.CLOSED -> {
                if (currentRssi >= threshold) {
                    // Segnale presente sopra la soglia: IL GATE RF SI APRE!
                    nextState = SquelchGateState.OPEN_ACTIVE
                    _isGateOpen.value = true
                    _hangTimeRemaining.value = 0.0
                    AppLogger.debug(TAG, "Squelch RF APERTO (RSSI: %.1f dBm >= Soglia: %.1f dBm, VAD Voice: $hasVoice, QRN: $isQrn)".format(currentRssi, threshold))
                }
            }

            SquelchGateState.OPEN_ACTIVE -> {
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
                    AppLogger.debug(TAG, "Squelch HANG TIME completato: GATE CHIUSO")
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
        return nextState
    }

    /**
     * Apre o chiude il segmento di registrazione secondo [recordMode].
     * Restituisce il pre-buffer consegnato al registratore se il segmento si apre in questo frame.
     */
    private fun updateRecordSegment(now: Long, hasVoice: Boolean, rfActive: Boolean): ByteArray? {
        val mode = _recordMode.value
        val rfOpen = _isGateOpen.value
        if (hasVoice) lastVoiceMs = now

        if (!isSegmentOpen) {
            val shouldOpen = when (mode) {
                GateMode.OFF, GateMode.RF -> rfOpen
                GateMode.VOICE -> hasVoice
                GateMode.RF_AND_VOICE -> rfOpen && hasVoice
            }
            if (!shouldOpen) return null
            return openSegment(now, mode)
        }

        val isActive = if (mode.usesVoice) hasVoice else rfActive
        if (isActive) segmentLastActiveMs = now

        val silenceCloseMs = (voiceSilenceCloseSeconds * 1000.0).toLong()
        val shouldClose = when (mode) {
            GateMode.OFF, GateMode.RF -> !rfOpen
            GateMode.VOICE -> now - lastVoiceMs >= (hangTimeSeconds * 1000.0).toLong()
            GateMode.RF_AND_VOICE -> !rfOpen || (silenceCloseMs > 0 && now - lastVoiceMs >= silenceCloseMs)
        }
        if (shouldClose) closeSegment(mode, rfOpen)
        return null
    }

    private fun openSegment(now: Long, mode: GateMode): ByteArray {
        isSegmentOpen = true
        segmentStartMs = now
        segmentLastActiveMs = now
        lastVoiceMs = if (mode.usesVoice) now else lastVoiceMs

        val rawPreBuffer = preBuffer.snapshot(preBufferMs)
        val flushed = if (rawPreBuffer.isNotEmpty()) {
            val preFader = SoftMuteFader(sampleRate = sampleRate, fadeDurationMs = softMuteMs)
            preFader.reset(open = false)
            preFader.setTargetOpen(true)
            preFader.process(rawPreBuffer)
        } else {
            rawPreBuffer
        }
        AppLogger.debug(TAG, "Segmento di registrazione APERTO (modalità $mode)")
        onGateOpenCallback?.invoke(flushed)
        onQsoStartedCallback?.invoke()
        return flushed
    }

    private fun closeSegment(mode: GateMode, rfOpen: Boolean) {
        isSegmentOpen = false
        val activeMillis = (segmentLastActiveMs - segmentStartMs).coerceAtLeast(0L)
        val reason = if (mode == GateMode.RF_AND_VOICE && rfOpen) "nessuna voce per ${voiceSilenceCloseSeconds}s" else "fine segnale"
        AppLogger.debug(TAG, "Segmento di registrazione CHIUSO ($reason, attivo ${activeMillis} ms)")
        onGateCloseCallback?.invoke(activeMillis)
        onQsoEndedCallback?.invoke()
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
        isSegmentOpen = false
        preBuffer.clear()
        vad?.reset()
        updateListenFaderTarget()
    }
}
