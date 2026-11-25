package com.grabber.decoder

import com.grabber.app.AppLogger
import com.grabber.decoder.server.ServerDecoderSession
import com.grabber.decoder.server.serverSessionFor
import com.grabber.kiwi.ext.ExtConnectionState
import com.grabber.kiwi.ext.ExtEvent
import com.grabber.kiwi.ext.ExtensionClient
import com.grabber.model.AudioFrame
import com.grabber.model.digital.DecodedImage
import com.grabber.model.digital.DecodedItem
import com.grabber.model.digital.DecoderMetrics
import com.grabber.model.digital.DecoderState
import com.grabber.model.digital.DigitalMode
import com.grabber.model.digital.DigitalModeParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Elemento appena completato da un decoder, con il modo che l'ha prodotto. */
data class DecodedEvent(val mode: DigitalMode, val item: DecodedItem)

/**
 * Un decoder alla volta: avvio e arresto, raccolta delle uscite e ripresa dopo la riconnessione.
 *
 * I decoder del server usano [ExtensionClient] (un solo `/EXT` per sessione, riaperto dopo la
 * riautenticazione della SND come il waterfall); i decoder locali ricevono l'audio da [onAudio].
 * Un rifiuto del ricevitore porta a [DecoderState.Unavailable] senza nuovi tentativi automatici.
 * Tutte le uscite sono elaborate su un dispatcher seriale.
 */
class DigitalDecoderCoordinator(
    private val scope: CoroutineScope,
    private val extensionClient: ExtensionClient,
    private val isAuthenticated: StateFlow<Boolean>,
    private val sessionToken: () -> Long?,
    private val carrierKhz: () -> Double,
    private val localDecoders: LocalDecoderFactory,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private companion object {
        const val TAG = "DigitalDecoder"
        const val PCM_SCALE = 1f / 32768f
    }

    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val buffer = DecoderOutputBuffer(clock)

    private val _state = MutableStateFlow<DecoderState>(DecoderState.Idle)
    val state: StateFlow<DecoderState> = _state.asStateFlow()

    private val _items = MutableStateFlow<List<DecodedItem>>(emptyList())
    val items: StateFlow<List<DecodedItem>> = _items.asStateFlow()

    private val _image = MutableStateFlow<DecodedImage?>(null)
    val image: StateFlow<DecodedImage?> = _image.asStateFlow()

    private val _metrics = MutableStateFlow(DecoderMetrics())
    val metrics: StateFlow<DecoderMetrics> = _metrics.asStateFlow()

    private val _completedItems = MutableSharedFlow<DecodedEvent>(extraBufferCapacity = 256)
    /** Elementi completati (righe chiuse, messaggi, spot), per notifiche e registri. */
    val completedItems: SharedFlow<DecodedEvent> = _completedItems.asSharedFlow()

    private val _completedImages = MutableSharedFlow<DecodedImage>(extraBufferCapacity = 8)
    val completedImages: SharedFlow<DecodedImage> = _completedImages.asSharedFlow()

    @Volatile private var activeMode: DigitalMode? = null
    @Volatile private var serverSession: ServerDecoderSession? = null
    @Volatile private var localDecoder: LocalDecoder? = null
    @Volatile private var params = DigitalModeParams()
    @Volatile private var centerKhz = 0.0
    private var floatBuffer = FloatArray(0)
    private var lastSequence = -1L

    init {
        extensionClient.events.onEach { event -> handleExtEvent(event) }.launchIn(scope)
        extensionClient.state.onEach(::handleExtState).launchIn(scope)
        isAuthenticated.onEach(::handleAuthentication).launchIn(scope)
    }

    val currentMode: DigitalMode? get() = activeMode

    /**
     * Avvia [mode] fermando il decoder in corso. [centerKhz] è la frequenza pubblicata scelta
     * (WSPR la invia al server). Il controller ha già verificato licenza e connessione.
     */
    fun start(mode: DigitalMode, params: DigitalModeParams, centerKhz: Double) = scope.launch(dispatcher) {
        stopInternal()
        this@DigitalDecoderCoordinator.params = params
        this@DigitalDecoderCoordinator.centerKhz = centerKhz
        activeMode = mode
        buffer.clear()
        buffer.setImageMode(mode)
        publish()
        if (mode.isServer) {
            val session = serverSessionFor(mode, params, centerKhz, carrierKhz)
            if (session == null) {
                _state.value = DecoderState.Unavailable(mode, "Decoder non disponibile.")
                return@launch
            }
            serverSession = session
            openExtension(session)
        } else {
            val decoder = localDecoders.create(mode, params)
            if (decoder == null) {
                _state.value = DecoderState.Unavailable(mode, "Decoder locale non ancora disponibile.")
                return@launch
            }
            localDecoder = decoder
            lastSequence = -1L
            _state.value = DecoderState.Running(mode)
            AppLogger.info(TAG, "Decoder locale ${mode.displayName} avviato")
        }
    }

    fun stop() = scope.launch(dispatcher) {
        stopInternal()
        publish()
    }

    /** Nuovi parametri: inviati al decoder attivo quando possibile, altrimenti il decoder viene ricreato. */
    fun updateParams(newParams: DigitalModeParams) = scope.launch(dispatcher) {
        params = newParams
        val mode = activeMode ?: return@launch
        serverSession?.let { session ->
            val commands = session.paramsChangedCommands(newParams)
            if (commands.isNotEmpty()) commands.forEach(extensionClient::send)
            return@launch
        }
        localDecoder?.let { decoder ->
            if (!decoder.updateParams(newParams)) {
                localDecoder = localDecoders.create(mode, newParams)
            }
        }
    }

    fun clear() = scope.launch(dispatcher) {
        val mode = activeMode
        buffer.clear()
        mode?.let(buffer::setImageMode)
        publish()
    }

    /** Riprova dopo un rifiuto (es. dopo aver liberato una connessione chiudendo il waterfall). */
    fun retry() = scope.launch(dispatcher) {
        val session = serverSession ?: return@launch
        openExtension(session)
    }

    /** La sintonia è cambiata: i decoder locali ripartono da zero (filtri e sincronismo). */
    fun onRetuned() = scope.launch(dispatcher) {
        localDecoder?.reset()
    }

    /**
     * Copia del frame audio originale dal tap di analisi. Va chiamato in ordine da un solo flusso
     * (il consumatore del tap), che non è il dispatcher della riproduzione.
     */
    fun onAudio(frame: AudioFrame) {
        val decoder = localDecoder ?: return
        val samples = frame.pcmBytes.size / 2
        if (floatBuffer.size < samples) floatBuffer = FloatArray(samples)
        val pcm = frame.pcmBytes
        for (i in 0 until samples) {
            val value = (pcm[2 * i].toInt() and 0xFF) or (pcm[2 * i + 1].toInt() shl 8)
            floatBuffer[i] = value.toShort() * PCM_SCALE
        }
        val gap = lastSequence >= 0 && frame.sequence != lastSequence + 1
        lastSequence = frame.sequence
        val copy = floatBuffer.copyOf(samples)
        scope.launch(dispatcher) {
            if (localDecoder !== decoder) return@launch
            try {
                if (gap) decoder.reset()
                decoder.process(copy, samples, frame.sampleRate, buffer.sink)
            } catch (e: Exception) {
                AppLogger.error(TAG, "Errore nel decoder locale ${decoder.mode.displayName}", e)
            }
            publish()
        }
    }

    /** Il tap ha perso frame per saturazione: il decoder locale risincronizza. */
    fun onAudioGap() = scope.launch(dispatcher) { localDecoder?.reset() }

    val isLocalDecoderActive: Boolean get() = localDecoder != null

    private fun openExtension(session: ServerDecoderSession) {
        val token = sessionToken()
        if (!isAuthenticated.value || token == null) {
            _state.value = DecoderState.Error(session.mode, "In attesa della connessione al ricevitore.")
            return
        }
        _state.value = DecoderState.Starting(session.mode)
        extensionClient.open(scope, token, session.extensionName, session.startCommands())
    }

    private fun stopInternal() {
        val mode = activeMode
        serverSession?.let { session ->
            extensionClient.close(session.stopCommands())
            AppLogger.info(TAG, "Decoder del server ${session.mode.displayName} fermato")
        }
        if (localDecoder != null) AppLogger.info(TAG, "Decoder locale ${mode?.displayName} fermato")
        serverSession = null
        localDecoder = null
        activeMode = null
        flushCompleted(mode)
        _state.value = DecoderState.Idle
    }

    private fun handleExtEvent(event: ExtEvent) = scope.launch(dispatcher) {
        val session = serverSession ?: return@launch
        try {
            when (event) {
                is ExtEvent.Text -> session.onText(event.message, buffer.sink, clock)
                is ExtEvent.Data -> session.onData(event.frame, buffer.sink)
            }
        } catch (e: Exception) {
            AppLogger.error(TAG, "Messaggio di ${session.mode.displayName} non interpretabile", e)
        }
        publish()
    }

    private fun handleExtState(state: ExtConnectionState) = scope.launch(dispatcher) {
        val session = serverSession ?: return@launch
        when (state) {
            is ExtConnectionState.Ready -> _state.value = DecoderState.Running(session.mode)
            is ExtConnectionState.Failed -> _state.value = if (state.permanent) {
                DecoderState.Unavailable(session.mode, state.message)
            } else {
                DecoderState.Error(session.mode, "${state.message} Il decoder riparte alla riconnessione.")
            }
            is ExtConnectionState.Connecting, ExtConnectionState.Idle -> Unit
        }
    }

    private fun handleAuthentication(authenticated: Boolean) = scope.launch(dispatcher) {
        val session = serverSession ?: return@launch
        if (!authenticated) {
            // Con la SND il server chiude anche l'estensione: si riapre alla prossima autenticazione.
            extensionClient.closeSocket("connessione audio persa")
            if (_state.value !is DecoderState.Unavailable) {
                _state.value = DecoderState.Error(session.mode, "Connessione persa: il decoder riparte dopo la riconnessione.")
            }
            return@launch
        }
        if (_state.value is DecoderState.Error) {
            AppLogger.info(TAG, "Riconnessione: riapertura di ${session.mode.displayName}")
            openExtension(session)
        }
    }

    private fun publish() {
        _items.value = buffer.items
        _image.value = buffer.image
        _metrics.value = buffer.metrics
        flushCompleted(activeMode)
    }

    private fun flushCompleted(mode: DigitalMode?) {
        val completed = buffer.drainCompleted()
        if (mode != null) completed.forEach { _completedItems.tryEmit(DecodedEvent(mode, it)) }
        buffer.drainCompletedImages().forEach { _completedImages.tryEmit(it) }
    }
}
