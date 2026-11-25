package com.grabber.waterfall

import com.grabber.app.AppLogger
import com.grabber.kiwi.KiwiProtocol
import com.grabber.kiwi.KiwiServerDefaults
import com.grabber.kiwi.KiwiWebSocketConnector
import com.grabber.kiwi.KiwiWebSocketListener
import com.grabber.kiwi.WebSocketCommandQueue
import com.grabber.model.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Client WebSocket dedicato allo streaming dello spettro e del waterfall di KiwiSDR.
 * Si collega all'endpoint `ws://<host>:<port>/<token>/W/F`.
 */
class WaterfallClient(
    private val connector: KiwiWebSocketConnector,
    var host: String = KiwiServerDefaults.HOST,
    var port: Int = KiwiServerDefaults.PORT,
    private var password: String = "",
    var clientName: String = "KiwiGrabberWaterfall",
    private val userAgent: String = DEFAULT_USER_AGENT
) {
    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) KiwiGrabber/1.0"
        private const val TAG = "WaterfallClient"
        const val MAX_ZOOM = 14
        const val TOTAL_SPECTRUM_KHZ = 30000.0

        fun calculateSpanKhz(zoom: Int): Double {
            val clamped = zoom.coerceIn(0, MAX_ZOOM)
            return TOTAL_SPECTRUM_KHZ / (1 shl clamped)
        }
    }

    fun updateServer(newHost: String, newPort: Int) {
        val cleanHost = newHost.trim().ifEmpty { KiwiServerDefaults.HOST }
        val changed = (host != cleanHost || port != newPort)
        host = cleanHost
        port = newPort
        if (changed) _installedExtensions.value = null
        if (changed && _connectionState.value !is ConnectionState.Disconnected) {
            disconnect()
        }
    }

    fun updatePassword(newPassword: String) {
        if (password == newPassword) return
        password = newPassword
        if (_connectionState.value !is ConnectionState.Disconnected) disconnect()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectionErrors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val connectionErrors: SharedFlow<String> = _connectionErrors.asSharedFlow()

    private val _currentZoom = MutableStateFlow(0)
    val currentZoom: StateFlow<Int> = _currentZoom.asStateFlow()

    private val _centerFrequencyKhz = MutableStateFlow(15000.0)
    val centerFrequencyKhz: StateFlow<Double> = _centerFrequencyKhz.asStateFlow()

    private val _minDb = MutableStateFlow(-110)
    val minDb: StateFlow<Int> = _minDb.asStateFlow()

    private val _maxDb = MutableStateFlow(-10)
    val maxDb: StateFlow<Int> = _maxDb.asStateFlow()

    private val _wfSpeed = MutableStateFlow(2) // 1..4 (default 2 per fluidità senza sovraccarico)
    val wfSpeed: StateFlow<Int> = _wfSpeed.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _waterfallFlow = MutableSharedFlow<WaterfallFrame>(extraBufferCapacity = 16)
    val waterfallFlow: SharedFlow<WaterfallFrame> = _waterfallFlow.asSharedFlow()

    private val _installedExtensions = MutableStateFlow<Set<String>?>(null)
    /** Estensioni installate sul ricevitore (`MSG extint_list_json=` su W/F); null finché non arriva. */
    val installedExtensions: StateFlow<Set<String>?> = _installedExtensions.asStateFlow()

    private val _latestFrame = MutableStateFlow<WaterfallFrame?>(null)
    val latestFrame: StateFlow<WaterfallFrame?> = _latestFrame.asStateFlow()

    @Volatile private var commandQueue: WebSocketCommandQueue? = null
    private var clientScope: CoroutineScope? = null
    private var keepaliveJob: Job? = null
    private val isConnected = AtomicBoolean(false)
    private val isManualDisconnect = AtomicBoolean(false)

    private val frameDispatcher = Dispatchers.Default.limitedParallelism(1)

    fun connect(scope: CoroutineScope, sessionToken: Long) {
        if (isConnected.get() || _connectionState.value is ConnectionState.Connecting) {
            return
        }

        isManualDisconnect.set(false)
        clientScope = scope
        isConnected.set(false)

        scope.launch(Dispatchers.IO) {
            _connectionState.value = ConnectionState.Connecting("Connessione Waterfall a $host:$port...")
            AppLogger.info(TAG, "Connessione Waterfall WebSocket a $host:$port/W/F...")

            try {
                val ws = connector.connect(
                    uri = "ws://$host:$port/$sessionToken/W/F",
                    headers = mapOf("Origin" to "http://$host:$port", "User-Agent" to userAgent),
                    listener = WaterfallWsListener()
                ).join()
                commandQueue = WebSocketCommandQueue(ws)
                isConnected.set(true)
                sendAuthentication()
            } catch (e: Exception) {
                val message = e.message?.takeIf { it.isNotBlank() } ?: "Connessione Waterfall fallita senza dettagli"
                AppLogger.error(TAG, "Errore durante la connessione Waterfall", e)
                if (!isManualDisconnect.get()) {
                    _connectionErrors.tryEmit(message)
                    _connectionState.value = ConnectionState.Error(message)
                } else {
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        }
    }

    fun disconnect() {
        isManualDisconnect.set(true)
        cleanup()
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun cleanup() {
        isConnected.set(false)
        keepaliveJob?.cancel()
        keepaliveJob = null
        commandQueue?.close("Chiusura Waterfall da client")
        commandQueue = null
    }

    private fun startKeepalive() {
        keepaliveJob?.cancel()
        keepaliveJob = clientScope?.launch(Dispatchers.IO) {
            while (isActive && isConnected.get()) {
                delay(1500)
                sendCommand("SET keepalive")
            }
        }
    }

    private fun sendAuthentication() {
        if (password.isNotEmpty()) {
            sendCommand(KiwiProtocol.auth(password))
        }
    }

    private fun sendInitCommands() {
        sendCommand(KiwiProtocol.identUser(clientName))
        sendCommand("SET wf_comp=0")
        sendCommand("SET wf_speed=${_wfSpeed.value}")
        sendCommand("SET wf_interp=13")
        sendCommand("SET maxdb=${_maxDb.value} mindb=${_minDb.value}")
        sendZoomAndCf(_currentZoom.value, _centerFrequencyKhz.value)
    }

    fun setZoom(zoom: Int) {
        val clamped = zoom.coerceIn(0, MAX_ZOOM)
        if (_currentZoom.value == clamped) return
        _currentZoom.value = clamped
        sendZoomAndCf(clamped, _centerFrequencyKhz.value)
    }

    fun setCenterFrequency(cfKhz: Double) {
        val clamped = cfKhz.coerceIn(0.0, TOTAL_SPECTRUM_KHZ)
        if (kotlin.math.abs(_centerFrequencyKhz.value - clamped) < 0.001) return
        _centerFrequencyKhz.value = clamped
        sendZoomAndCf(_currentZoom.value, clamped)
    }

    fun zoomIn() {
        if (_currentZoom.value < MAX_ZOOM) {
            setZoom(_currentZoom.value + 1)
        }
    }

    fun zoomOut() {
        if (_currentZoom.value > 0) {
            setZoom(_currentZoom.value - 1)
        }
    }

    fun setDbRange(minDb: Int, maxDb: Int) {
        val clampedMin = minDb.coerceIn(-150, -10)
        val clampedMax = maxDb.coerceIn(clampedMin + 5, 0)
        _minDb.value = clampedMin
        _maxDb.value = clampedMax
        sendCommand("SET maxdb=$clampedMax mindb=$clampedMin")
    }

    fun setSpeed(speed: Int) {
        val clamped = speed.coerceIn(1, 4)
        if (_wfSpeed.value == clamped) return
        _wfSpeed.value = clamped
        sendCommand("SET wf_speed=$clamped")
    }

    fun setPaused(paused: Boolean) {
        _isPaused.value = paused
    }

    fun togglePause() {
        _isPaused.value = !_isPaused.value
    }

    private fun sendZoomAndCf(zoom: Int, cfKhz: Double) {
        val formattedCf = String.format(java.util.Locale.US, "%.3f", cfKhz)
        sendCommand("SET zoom=$zoom cf=$formattedCf")
    }

    fun sendCommand(cmd: String) {
        val queue = commandQueue
        if (queue == null || !isConnected.get()) return
        queue.send(cmd).whenComplete { _, error ->
            if (error != null) AppLogger.warn(TAG, "Comando Waterfall non inviato: ${KiwiProtocol.redactSecrets(cmd)} (${error.message})")
        }
    }

    private inner class WaterfallWsListener : KiwiWebSocketListener {
        override fun onText(text: String) {
            handleServerText(text)
        }

        override fun onBinary(bytes: ByteArray) {
            clientScope?.launch(frameDispatcher) {
                try {
                    handleBinaryFrame(bytes)
                } catch (e: Exception) {
                    AppLogger.error(TAG, "Errore processamento frame W/F binario", e)
                }
            }
        }

        override fun onClosed(code: Int, reason: String) {
            AppLogger.info(TAG, "Waterfall WebSocket chiuso (code=$code, reason=$reason)")
            val authRejected = _connectionState.value is ConnectionState.Error
            isConnected.set(false)
            cleanup()
            if (!authRejected) {
                if (!isManualDisconnect.get()) {
                    _connectionErrors.tryEmit("Connessione Waterfall chiusa dal server (codice $code).")
                }
                _connectionState.value = ConnectionState.Disconnected
            }
        }

        override fun onError(error: Throwable) {
            val msg = error.message?.takeIf { it.isNotBlank() } ?: "Errore Waterfall WebSocket senza dettagli"
            AppLogger.error(TAG, msg, error)
            val manual = isManualDisconnect.get()
            isConnected.set(false)
            cleanup()
            if (manual) {
                _connectionState.value = ConnectionState.Disconnected
            } else {
                _connectionErrors.tryEmit(msg)
                _connectionState.value = ConnectionState.Error(msg)
            }
        }
    }

    private fun handleServerText(text: String) {
        AppLogger.trace(TAG, "[WF TEXT] $text")
        if (text.startsWith("extint_list_json=")) {
            _installedExtensions.value = parseExtensionList(text.substringAfter("extint_list_json="))
            return
        }
        val authResult = Regex("(?:^|\\s)badp=(\\d+)").find(text)?.groupValues?.get(1) ?: return
        if (authResult == "0") {
            if (_connectionState.value !is ConnectionState.Connected) {
                _connectionState.value = ConnectionState.Connected
                sendInitCommands()
                startKeepalive()
            }
            return
        }

        val message = when (authResult) {
            "1" -> "Autenticazione Waterfall rifiutata: password errata o canali occupati. Controlla la password in Impostazioni > Configurazione server e riprova."
            "5" -> "Autenticazione Waterfall rifiutata (badp=5): KiwiSDR non consente connessioni multiple dallo stesso IP."
            else -> "Autenticazione Waterfall rifiutata dal server (badp=$authResult)."
        }
        AppLogger.error(TAG, message)
        _connectionErrors.tryEmit(message)
        _connectionState.value = ConnectionState.Error(message)
        isConnected.set(false)
        keepaliveJob?.cancel()
        keepaliveJob = null
        commandQueue?.close("Autenticazione Waterfall rifiutata")
    }

    private fun handleBinaryFrame(bytes: ByteArray) {
        if (bytes.size < 4) return

        val tag = bytes.decodeToString(0, 3)
        when (tag) {
            "W/F" -> parseWaterfallFrame(bytes)
            "MSG" -> {
                val msg = if (bytes.size > 4) bytes.decodeToString(4, bytes.size) else ""
                handleServerText(msg)
            }
        }
    }

    /**
     * Decodifica il frame binario W/F.
     * Header a 12 byte (dopo "W/F "):
     * - byte 4..7: x_bin (uint32 LE)
     * - byte 8..11: flags_x_zoom (uint32 LE)
     * - byte 12..15: seq (uint32 LE)
     * - byte 16..end: campioni spettrali (1024 bin a 8-bit unsigned)
     */
    fun parseWaterfallFrame(bytes: ByteArray) {
        if (_isPaused.value) return
        if (bytes.size < 16) return

        val headerBuffer = ByteBuffer.wrap(bytes, 4, 12).order(ByteOrder.LITTLE_ENDIAN)
        val xBin = headerBuffer.getInt().toLong() and 0xFFFFFFFFL
        val flagsZoom = headerBuffer.getInt().toLong() and 0xFFFFFFFFL
        val seq = headerBuffer.getInt().toLong() and 0xFFFFFFFFL

        val binsLen = bytes.size - 16
        if (binsLen <= 0) return

        val bins = ByteArray(binsLen)
        System.arraycopy(bytes, 16, bins, 0, binsLen)

        val zoom = _currentZoom.value
        val cf = _centerFrequencyKhz.value

        // Calcolo span e limiti di frequenza
        val spanKhz = calculateSpanKhz(zoom)
        val halfSpan = spanKhz / 2.0
        val maxStart = (TOTAL_SPECTRUM_KHZ - spanKhz).coerceAtLeast(0.0)
        val startKhz = (cf - halfSpan).coerceIn(0.0, maxStart)
        val stopKhz = (startKhz + spanKhz).coerceAtMost(TOTAL_SPECTRUM_KHZ)

        val frame = WaterfallFrame(
            sequenceNumber = seq,
            bins = bins,
            zoom = zoom,
            centerFreqKhz = cf,
            startFreqKhz = startKhz,
            spanKhz = spanKhz,
            stopFreqKhz = stopKhz
        )

        _latestFrame.value = frame
        _waterfallFlow.tryEmit(frame)
    }
}

/** Elenco JSON (percent-encoded) dei nomi delle estensioni: `["ALE_2G","FT8",...]`. */
internal fun parseExtensionList(encoded: String): Set<String>? =
    try {
        val decoded = com.grabber.kiwi.ext.ExtMessageParser.decodeComponent(encoded.trim())
        (kotlinx.serialization.json.Json.parseToJsonElement(decoded) as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            ?.toSet()
    } catch (e: Exception) {
        AppLogger.warn("WaterfallClient", "Elenco estensioni non leggibile", e)
        null
    }
