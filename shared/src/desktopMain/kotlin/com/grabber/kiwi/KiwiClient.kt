package com.grabber.kiwi

import com.grabber.app.AppLogger
import com.grabber.model.AudioFrame
import com.grabber.model.ConnectionState
import com.grabber.model.KiwiDxLabel
import com.grabber.model.ModulationMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.future.await
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.concurrent.CompletionStage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

data class KiwiUser(
    val slot: Int,
    val name: String,
    val freqHz: Long,
    val mode: String,
    val connected: String,
    val isMySession: Boolean = false
)

class KiwiClient(
    var host: String = KiwiServerDefaults.HOST,
    var port: Int = KiwiServerDefaults.PORT,
    private var password: String = "",
    var clientName: String = "KiwiGrabberDesktop"
) {
    companion object {
        private const val TAG = "KiwiClient"
        private const val KEEPALIVE_INTERVAL_MS = 1500L
        private const val KEEPALIVE_SEND_TIMEOUT_MS = 10_000L
        private const val USERS_POLL_INTERVAL_MS = 5000L
    }

    private fun authenticationFailureMessage(code: String?): String =
        when (code) {
        "1" -> "Accesso rifiutato: password errata oppure tutti i canali sono occupati. Controlla la password in Impostazioni > Configurazione server e riprova."
            "5" -> "KiwiSDR ha rifiutato la connessione: connessioni multiple dallo stesso IP non permesse."
            else -> "Accesso rifiutato dal server KiwiSDR (codice: ${code ?: "sconosciuto"})."
        }

    fun updateServer(newHost: String, newPort: Int) {
        val cleanHost = newHost.trim().ifEmpty { KiwiServerDefaults.HOST }
        val changed = (host != cleanHost || port != newPort)
        host = cleanHost
        port = newPort
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

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _sessionToken = MutableStateFlow<Long?>(null)
    val sessionToken: StateFlow<Long?> = _sessionToken.asStateFlow()

    private val _connectionErrors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val connectionErrors: SharedFlow<String> = _connectionErrors.asSharedFlow()

    private val _currentFrequencyKhz = MutableStateFlow(7100.0)
    val currentFrequencyKhz: StateFlow<Double> = _currentFrequencyKhz.asStateFlow()

    private val _currentMode = MutableStateFlow(ModulationMode.AM)
    val currentMode: StateFlow<ModulationMode> = _currentMode.asStateFlow()

    private val _rssi = MutableStateFlow(-127.0)
    val rssi: StateFlow<Double> = _rssi.asStateFlow()

    private val _audioFlow = MutableSharedFlow<AudioFrame>(extraBufferCapacity = 64)
    val audioFlow: SharedFlow<AudioFrame> = _audioFlow.asSharedFlow()

    private val _activeUsers = MutableStateFlow<List<KiwiUser>>(emptyList())
    val activeUsers: StateFlow<List<KiwiUser>> = _activeUsers.asStateFlow()

    private val _serverDxLabels = MutableStateFlow<List<KiwiDxLabel>>(emptyList())
    val serverDxLabels: StateFlow<List<KiwiDxLabel>> = _serverDxLabels.asStateFlow()

    private val _serverLogs = MutableSharedFlow<String>(replay = 50, extraBufferCapacity = 100)
    val serverLogs: SharedFlow<String> = _serverLogs.asSharedFlow()

    private val httpClient: HttpClient = HttpClient.newHttpClient()
    @Volatile private var commandQueue: WebSocketCommandQueue? = null
    private var clientScope: CoroutineScope? = null
    private var keepaliveJob: Job? = null
    private var usersPollingJob: Job? = null
    private var autoReconnectJob: Job? = null
    private val isConnected = AtomicBoolean(false)
    private val isManualDisconnect = AtomicBoolean(false)

    fun isManualDisconnected(): Boolean = isManualDisconnect.get()

    // Dispatcher serializzato per processare i frame audio in ordine FIFO.
    // Evita sovrapposizioni/echo causati da coroutine concorrenti su Dispatchers.Default.
    private val frameDispatcher = Dispatchers.Default.limitedParallelism(1)

    // Soglia di variazione minima e intervallo per aggiornare il RSSI nella UI (riduce ricomposizioni)
    private var lastEmittedRssi = -127.0
    private var lastRssiEmitTime = 0L

    private var negotiatedAudioRate = 12000
    private var agcEnabled = true
    private var agcGain = 50
    private var assignedChannel: Int? = null

    // Buffer per riassemblare frame binari frammentati
    private var binaryBuffer = ByteBuffer.allocate(65536)
    private var textBuffer = StringBuilder()

    fun connect(scope: CoroutineScope) {
        if (isConnected.get() || _connectionState.value is ConnectionState.Connecting) {
            return
        }

        isManualDisconnect.set(false)
        autoReconnectJob?.cancel()
        autoReconnectJob = null
        assignedChannel = null
        clientScope = scope
        isConnected.set(false)
        _isAuthenticated.value = false

        scope.launch(Dispatchers.IO) {
            _connectionState.value = ConnectionState.Connecting("Connessione a $host:$port...")
            AppLogger.info(TAG, "Connessione WebSocket a $host:$port...")
            _serverLogs.emit("[CLIENT] Connessione WebSocket a $host:$port...")

            val success = doConnect(scope)
            if (!success && !isManualDisconnect.get()) {
                scheduleAutoReconnect("Impossibile raggiungere il server")
            }
        }
    }

    private suspend fun doConnect(scope: CoroutineScope): Boolean {
        return withContext(Dispatchers.IO) {
            _isAuthenticated.value = false
            val token = KiwiTokenGenerator.generateToken()
            _sessionToken.value = token
            try {
                val wsUri = URI.create("ws://$host:$port/$token/SND")
                val ws = httpClient.newWebSocketBuilder()
                    .header("Origin", "http://$host:$port")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) GrabberClient/1.0")
                    .buildAsync(wsUri, KiwiWebSocketListener())
                    .get()

                commandQueue = WebSocketCommandQueue(ws)
                isConnected.set(true)
                _connectionState.value = ConnectionState.Connected
                AppLogger.info(TAG, "WebSocket connessa con successo!")
                _serverLogs.emit("[CLIENT] WebSocket connessa con successo.")

                sendCommand(KiwiProtocol.auth(password))
                sendCommand(KiwiProtocol.identUser(clientName))
                startKeepalive(scope)
                true
            } catch (e: Exception) {
                val errorMsg = e.message?.takeIf { it.isNotBlank() } ?: "Errore di connessione sconosciuto"
                AppLogger.error(TAG, "Errore connessione WebSocket", e)
                _serverLogs.emit("[ERRORE] $errorMsg")
                _connectionErrors.tryEmit(errorMsg)
                cleanup()
                false
            }
        }
    }

    private fun startKeepalive(scope: CoroutineScope) {
        keepaliveJob?.cancel()
        keepaliveJob = scope.launch {
            while (isActive && isConnected.get()) {
                delay(KEEPALIVE_INTERVAL_MS)
                val failure = sendKeepalive() ?: continue
                AppLogger.warn(TAG, "Keepalive fallito: $failure")
                if (!isManualDisconnect.get()) {
                    scheduleAutoReconnect("Keepalive fallito")
                }
                break
            }
        }
    }

    /** Restituisce la causa del fallimento, oppure null se il keepalive è stato inviato. */
    private suspend fun sendKeepalive(): String? =
        try {
            withTimeout(KEEPALIVE_SEND_TIMEOUT_MS) { sendCommand(KiwiProtocol.keepalive).await() }
            null
        } catch (_: TimeoutCancellationException) {
            "invio non completato entro $KEEPALIVE_SEND_TIMEOUT_MS ms"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }

    private fun scheduleAutoReconnect(reason: String = "Connessione interrotta") {
        if (isManualDisconnect.get()) return
        isConnected.set(false)
        _isAuthenticated.value = false
        cleanup()

        autoReconnectJob?.cancel()
        val scope = clientScope ?: return
        autoReconnectJob = scope.launch(Dispatchers.IO) {
            var attempt = 1
            while (isActive && !isManualDisconnect.get() && !isConnected.get()) {
                AppLogger.warn(TAG, "Riconnessione automatica tra 10s (Tentativo #$attempt)... Motivo: $reason")
                _serverLogs.emit("[CLIENT] Disconnessione non voluta. Riconnessione tra 10s (Tentativo #$attempt)...")
                for (sec in 10 downTo 1) {
                    if (!isActive || isManualDisconnect.get() || isConnected.get()) return@launch
                    _connectionState.value = ConnectionState.Reconnecting(attempt, sec, reason)
                    delay(1000)
                }
                if (!isActive || isManualDisconnect.get() || isConnected.get()) return@launch

                _connectionState.value = ConnectionState.Connecting("Tentativo #$attempt riconnessione a $host:$port...")
                _serverLogs.emit("[CLIENT] Tentativo #$attempt di riconnessione automatica in corso...")
                val success = doConnect(scope)
                if (success) {
                    AppLogger.info(TAG, "Riconnessione automatica riuscita al tentativo #$attempt!")
                    _serverLogs.emit("[CLIENT] Riconnessione automatica riuscita con successo!")
                    return@launch
                }
                attempt++
            }
        }
    }

    private inner class KiwiWebSocketListener : WebSocket.Listener {
        override fun onOpen(webSocket: WebSocket) {
            AppLogger.debug(TAG, "WebSocket aperta")
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
            textBuffer.append(data)
            if (last) {
                val fullText = textBuffer.toString()
                textBuffer.clear()
                AppLogger.trace(TAG, "[SERVER TEXT] $fullText")
            }
            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        override fun onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage<*> {
            if (binaryBuffer.remaining() < data.remaining()) {
                val newCapacity = binaryBuffer.position() + data.remaining() + 8192
                val newBuffer = ByteBuffer.allocate(newCapacity)
                binaryBuffer.flip()
                newBuffer.put(binaryBuffer)
                binaryBuffer = newBuffer
            }
            binaryBuffer.put(data)

            if (last) {
                binaryBuffer.flip()
                val bytes = ByteArray(binaryBuffer.remaining())
                binaryBuffer.get(bytes)
                binaryBuffer.clear()

                clientScope?.launch(frameDispatcher) {
                    try {
                        handleBinaryFrame(bytes)
                    } catch (e: Exception) {
                        AppLogger.error(TAG, "Errore processamento frame binario", e)
                    }
                }
            }

            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*> {
            AppLogger.info(TAG, "WebSocket chiusa dal server (code=$statusCode, reason=$reason)")
            _serverLogs.tryEmit("[CLIENT] Connessione chiusa dal server (code=$statusCode).")
            val manual = isManualDisconnect.get()
            isConnected.set(false)
            _isAuthenticated.value = false
            cleanup()
            if (!manual) {
                scheduleAutoReconnect("Connessione chiusa dal server (code=$statusCode)")
                _connectionErrors.tryEmit("Connessione audio/controllo chiusa dal server (codice $statusCode); riconnessione in corso.")
            } else {
                _connectionState.value = ConnectionState.Disconnected
            }
            return CompletableFuture.completedFuture(null)
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            val msg = error.message?.takeIf { it.isNotBlank() } ?: "Errore WebSocket senza dettagli"
            AppLogger.error(TAG, "Errore WebSocket: $msg", error)
            val manual = isManualDisconnect.get()
            isConnected.set(false)
            _isAuthenticated.value = false
            cleanup()
            if (!manual) {
                _connectionErrors.tryEmit(msg)
                _serverLogs.tryEmit("[ERRORE] $msg - Riconnessione automatica...")
                scheduleAutoReconnect(msg)
            } else {
                _serverLogs.tryEmit("[ERRORE] $msg")
                _connectionErrors.tryEmit(msg)
                _connectionState.value = ConnectionState.Error(msg)
            }
        }
    }

    private suspend fun handleBinaryFrame(bytes: ByteArray) {
        if (bytes.size < 4) return

        val tag = bytes.decodeToString(0, 3)
        when (tag) {
            "MSG" -> {
                val msgContent = if (bytes.size > 4) bytes.decodeToString(4, bytes.size) else ""
                parseServerMessage(msgContent)
            }
            "SND" -> {
                handleSoundFrame(bytes)
            }
        }
    }

    private suspend fun parseServerMessage(msg: String) {
        val logMsg = if (msg.length > 300) msg.substring(0, 300) + "..." else msg
        AppLogger.debug(TAG, "[SERVER MSG] $logMsg")

        if (msg.startsWith("user_cb=")) {
            parseUserCb(msg.substringAfter("user_cb="))
            return
        }

        if (msg.startsWith("load_dxcfg=")) {
            parseDxCfg(msg.substringAfter("load_dxcfg="))
            return
        }

        val pairs = msg.split(" ")
        for (pair in pairs) {
            val parts = pair.split("=", limit = 2)
            val key = parts[0]
            val value = parts.getOrNull(1)

            when (key) {
                "audio_rate" -> {
                    value?.toIntOrNull()?.let { rate ->
                        negotiatedAudioRate = rate
                        AppLogger.info(TAG, "Audio rate negoziato: $rate Hz. Invio handshake...")
                        sendCommand(KiwiProtocol.arOk(rate, rate))
                        sendCommand(KiwiProtocol.identUser(clientName))
                        sendCommand(KiwiProtocol.compression(false))
                        val tuneCmd = KiwiProtocol.tune(_currentMode.value, _currentFrequencyKhz.value)
                        sendCommand(tuneCmd)
                        AppLogger.info(TAG, "Handshake tune: $tuneCmd")
                        sendCommand(KiwiProtocol.agc(agcEnabled, agcGain))
                        sendCommand("SET GET_USERS")
                        sendCommand("SET load_dxcfg")
                        sendCommand("SET load_dxcomm_cfg")
                        startUsersPolling()
                    }
                }
                "is_local" -> {
                    value?.split(",")?.getOrNull(0)?.toIntOrNull()?.let { ch ->
                        assignedChannel = ch
                        AppLogger.info(TAG, "Canale SDR assegnato dal server: $ch")
                    }
                }
                "rx_chan" -> {
                    value?.toIntOrNull()?.let { ch ->
                        assignedChannel = ch
                    }
                }
                "badp" -> {
                    if (value == "0") {
                        _isAuthenticated.value = true
                    } else {
                        val reason = authenticationFailureMessage(value)
                        AppLogger.error(TAG, "Autenticazione fallita: $reason")
                        _connectionErrors.tryEmit(reason)
                        _connectionState.value = ConnectionState.Error(reason)
                        disconnect()
                    }
                }
            }
        }
    }

    private fun parseUserCb(json: String) {
        if (json.trim().removeSurrounding("[", "]").isBlank()) return
        try {
            val users = KiwiMessageParser.parseUserList(json).map(::toKiwiUser)
            _activeUsers.value = users
            AppLogger.trace(TAG, "Utenti aggiornati: ${users.size} attivi")
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Errore parsing user_cb: ${e.message}")
        }
    }

    private fun toKiwiUser(entry: KiwiUserEntry): KiwiUser {
        val isMy = (entry.slot == assignedChannel) ||
            (entry.name.equals(clientName, ignoreCase = true) && clientName.isNotBlank())
        return KiwiUser(
            slot = entry.slot,
            name = displayNameFor(entry, isMy),
            freqHz = entry.freqHz,
            mode = entry.mode,
            connected = entry.connected,
            isMySession = isMy
        )
    }

    private fun displayNameFor(entry: KiwiUserEntry, isMy: Boolean): String = when {
        isMy -> if (clientName.isNotBlank() && clientName != "KiwiGrabberDesktop") "★ $clientName (Tu)" else "★ Questo Ricevitore (Tu)"
        entry.name.isBlank() || entry.name == "(no identity)" || entry.name == "%28no%20identity%29" -> "Ascoltatore (Ch ${entry.slot})"
        else -> entry.name
    }

    private fun parseDxCfg(rawJson: String) {
        try {
            val labels = KiwiMessageParser.parseDxLabels(rawJson)
            if (labels.isNotEmpty()) {
                _serverDxLabels.value = labels
                AppLogger.info(TAG, "Ricevuti ${labels.size} segnalibri DX dal server KiwiSDR")
                _serverLogs.tryEmit("[DX] Ricevuti ${labels.size} canali/segnalibri DX dal server.")
            }
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Errore parsing load_dxcfg: ${e.message}")
        }
    }

    fun requestDxLabels() {
        AppLogger.info(TAG, "Richiesta segnalibri DX al server...")
        sendCommand("SET load_dxcfg")
        sendCommand("SET load_dxcomm_cfg")
    }

    private fun startUsersPolling() {
        usersPollingJob?.cancel()
        usersPollingJob = clientScope?.launch {
            while (isActive && isConnected.get()) {
                delay(USERS_POLL_INTERVAL_MS)
                sendCommand("SET GET_USERS")
            }
        }
    }

    private suspend fun handleSoundFrame(bytes: ByteArray) {
        val frame = KiwiMessageParser.decodeSoundFrame(bytes) ?: return
        updateRssi(frame.rssiDbm)
        if (frame.pcmLittleEndian.isNotEmpty()) {
            _audioFlow.emit(
                AudioFrame(
                    sequence = frame.sequence,
                    rssi = frame.rssiDbm,
                    pcmBytes = frame.pcmLittleEndian,
                    sampleRate = negotiatedAudioRate
                )
            )
        }
    }

    /** Aggiorna la UI solo per variazioni ≥ 0.6 dBm ogni 60 ms (~16 FPS) o ≥ 0.2 dBm ogni 250 ms. */
    private fun updateRssi(measuredRssi: Double) {
        val prev = _rssi.value
        val smoothedRssi = if (prev <= -126.9) measuredRssi else (prev * 0.70 + measuredRssi * 0.30)
        val now = System.currentTimeMillis()
        val elapsed = now - lastRssiEmitTime
        val delta = kotlin.math.abs(smoothedRssi - lastEmittedRssi)
        if ((elapsed >= 60L && delta >= 0.6) || (elapsed >= 250L && delta >= 0.2)) {
            _rssi.value = smoothedRssi
            lastEmittedRssi = smoothedRssi
            lastRssiEmitTime = now
        }
    }

    fun tune(freqKhz: Double, mode: ModulationMode = _currentMode.value) {
        _currentFrequencyKhz.value = freqKhz
        _currentMode.value = mode
        val cmd = KiwiProtocol.tune(mode, freqKhz)
        AppLogger.info(TAG, "TUNE -> $cmd")
        sendCommand(cmd)
        clientScope?.launch {
            delay(500)
            sendCommand("SET GET_USERS")
        }
    }

    fun setMode(mode: ModulationMode) {
        _currentMode.value = mode
        val cmd = KiwiProtocol.tune(mode, _currentFrequencyKhz.value)
        AppLogger.info(TAG, "SET MODE -> $cmd")
        sendCommand(cmd)
        clientScope?.launch {
            delay(500)
            sendCommand("SET GET_USERS")
        }
    }

    fun setAgc(enabled: Boolean, gain: Int = 50) {
        agcEnabled = enabled
        agcGain = gain
        sendCommand(KiwiProtocol.agc(enabled, gain))
    }

    fun setIdentUser(name: String) {
        clientName = name
        sendCommand(KiwiProtocol.identUser(name))
        clientScope?.launch {
            delay(500)
            sendCommand("SET GET_USERS")
        }
    }

    /**
     * Accoda il comando senza bloccare il chiamante (spesso il thread UI).
     * Il future restituito fallisce se l'invio non riesce; un comando ignorato perché
     * il client non è connesso risulta invece completato.
     */
    private fun sendCommand(cmd: String): CompletableFuture<*> {
        val queue = commandQueue
        val loggableCmd = KiwiProtocol.redactSecrets(cmd)
        if (queue == null || !isConnected.get()) {
            AppLogger.warn(TAG, "Invio ignorato (non connesso): $loggableCmd")
            return CompletableFuture.completedFuture(null)
        }
        AppLogger.debug(TAG, "[CLIENT -> SERVER] $loggableCmd")
        _serverLogs.tryEmit("[CLIENT -> SERVER] $loggableCmd")
        return queue.send(cmd).whenComplete { _, error ->
            if (error != null) AppLogger.error(TAG, "Errore invio comando: $loggableCmd", error)
        }
    }

    private fun cleanup() {
        keepaliveJob?.cancel()
        keepaliveJob = null
        usersPollingJob?.cancel()
        usersPollingJob = null
        assignedChannel = null
        _activeUsers.value = emptyList()
        binaryBuffer.clear()
        textBuffer.clear()
    }

    fun disconnect() {
        AppLogger.info(TAG, "Disconnessione manuale richiesta dall'utente...")
        isManualDisconnect.set(true)
        autoReconnectJob?.cancel()
        autoReconnectJob = null
        isConnected.set(false)
        _isAuthenticated.value = false
        _connectionState.value = ConnectionState.Disconnected
        _serverLogs.tryEmit("[CLIENT] Disconnessione manuale effettuata.")
        cleanup()
        commandQueue?.close("bye")
        commandQueue = null
    }
}
