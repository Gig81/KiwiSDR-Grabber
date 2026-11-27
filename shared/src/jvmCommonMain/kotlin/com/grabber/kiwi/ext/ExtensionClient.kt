package com.grabber.kiwi.ext

import com.grabber.app.AppLogger
import com.grabber.kiwi.ExtProtocol
import com.grabber.kiwi.KiwiProtocol
import com.grabber.kiwi.KiwiServerDefaults
import com.grabber.kiwi.KiwiWebSocketConnector
import com.grabber.kiwi.KiwiWebSocketListener
import com.grabber.kiwi.WebSocketCommandQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Stato del socket `/EXT`. */
sealed interface ExtConnectionState {
    data object Idle : ExtConnectionState
    data class Connecting(val extensionName: String) : ExtConnectionState
    data class Ready(val extensionName: String) : ExtConnectionState

    /**
     * Socket chiuso o rifiutato. [permanent] indica un rifiuto del ricevitore (limite connessioni,
     * estensione assente, DRM in uso, password): niente nuovi tentativi automatici.
     */
    data class Failed(val extensionName: String, val message: String, val permanent: Boolean) : ExtConnectionState
}

/** Evento ricevuto dall'estensione dopo `EXT ready`, nell'ordine di arrivo. */
sealed interface ExtEvent {
    data class Text(val message: ExtTextMessage) : ExtEvent
    class Data(val frame: ExtDataFrame) : ExtEvent
}

/**
 * Client del WebSocket `/EXT` di KiwiSDR, aperto con lo stesso token della sessione SND (come W/F).
 *
 * Sequenza: autenticazione, `ext_switch_to_client`, attesa di `EXT ready` (con timeout: un nome
 * sconosciuto non riceve risposta), comandi dell'estensione e keepalive periodico. La chiusura
 * invia prima i comandi di stop dell'estensione e poi `ext_blur` (che da solo non ferma il decoder).
 * Tutti gli invii passano da [WebSocketCommandQueue]; i frame sono elaborati in ordine su un
 * dispatcher seriale.
 */
class ExtensionClient(
    private val connector: KiwiWebSocketConnector,
    var host: String = KiwiServerDefaults.HOST,
    var port: Int = KiwiServerDefaults.PORT,
    var password: String = "",
    var clientName: String = "KiwiGrabber",
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val readyTimeoutMs: Long = READY_TIMEOUT_MS,
    private val keepaliveIntervalMs: Long = KEEPALIVE_INTERVAL_MS
) {
    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) GrabberClient/1.0"
        const val READY_TIMEOUT_MS = 5_000L
        const val KEEPALIVE_INTERVAL_MS = 10_000L
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val TAG = "ExtensionClient"

        const val MESSAGE_NOT_ALLOWED =
            "Il ricevitore non consente decoder a client esterni (limite delle connessioni per le app raggiunto o pari a zero)."
        const val MESSAGE_EXTENSION_MISSING = "Estensione non installata o disabilitata dall'amministratore del ricevitore."
        const val MESSAGE_LOCKED = "Un altro utente usa DRM: i decoder del server sono sospesi su questo ricevitore."
        const val MESSAGE_AUTH = "Accesso all'estensione rifiutato dal ricevitore (password o canali)."
    }

    private val _state = MutableStateFlow<ExtConnectionState>(ExtConnectionState.Idle)
    val state: StateFlow<ExtConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ExtEvent>(extraBufferCapacity = 1024)
    val events: SharedFlow<ExtEvent> = _events.asSharedFlow()

    /** Versione del server da `MSG version_maj/min`, per il log (es. "1.902"). */
    @Volatile var serverVersion: String? = null
        private set

    @Volatile private var queue: WebSocketCommandQueue? = null
    @Volatile private var extensionName: String? = null
    @Volatile private var closingByClient = false
    @Volatile private var receivedAnyMessage = false
    private var scope: CoroutineScope? = null
    private var keepaliveJob: Job? = null
    private var readyTimeoutJob: Job? = null
    private val sessionId = AtomicLong()
    private val frameDispatcher = Dispatchers.Default.limitedParallelism(1)

    /** Comandi da inviare appena arriva `EXT ready` (avvio dell'estensione). */
    @Volatile private var pendingStartCommands: List<String> = emptyList()

    /**
     * Apre `/EXT` per [extension] con il token della SND autenticata e invia [startCommands] dopo
     * `EXT ready`. Un'eventuale sessione precedente viene chiusa senza comandi di stop.
     */
    fun open(scope: CoroutineScope, sessionToken: Long, extension: String, startCommands: List<String>) {
        closeSocket("nuova sessione")
        val id = sessionId.incrementAndGet()
        this.scope = scope
        extensionName = extension
        pendingStartCommands = startCommands
        closingByClient = false
        receivedAnyMessage = false
        _state.value = ExtConnectionState.Connecting(extension)
        AppLogger.info(TAG, "Apertura /EXT per $extension su $host:$port")
        scope.launch(Dispatchers.IO) {
            try {
                val socket = connector.connect(
                    uri = "ws://$host:$port/$sessionToken/EXT",
                    headers = mapOf("Origin" to "http://$host:$port", "User-Agent" to userAgent),
                    listener = Listener(id)
                ).get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (id != sessionId.get()) {
                    socket.close(1000, "sessione superata")
                    return@launch
                }
                val commandQueue = WebSocketCommandQueue(socket)
                queue = commandQueue
                send(KiwiProtocol.auth(password))
                send(KiwiProtocol.identUser(clientName))
                send(ExtProtocol.switchToClient(extension))
                startReadyTimeout(id, extension)
            } catch (e: Exception) {
                if (id != sessionId.get()) return@launch
                AppLogger.warn(TAG, "/EXT rifiutato per $extension: ${e.message ?: e.javaClass.simpleName}")
                fail(id, MESSAGE_NOT_ALLOWED, permanent = true)
            }
        }
    }

    /** Invia un comando all'estensione (ignorato se il socket non è aperto). */
    fun send(command: String) {
        val commandQueue = queue ?: return
        val loggable = KiwiProtocol.redactSecrets(command)
        AppLogger.debug(TAG, "[CLIENT -> EXT] $loggable")
        commandQueue.send(command).whenComplete { _, error ->
            if (error != null) AppLogger.warn(TAG, "Comando /EXT non inviato: $loggable (${error.message})")
        }
    }

    /** Chiusura ordinata: comandi di stop dell'estensione, `ext_blur` e chiusura del socket. */
    fun close(stopCommands: List<String>) {
        val commandQueue = queue
        if (commandQueue != null) {
            closingByClient = true
            stopCommands.forEach(::send)
            send(ExtProtocol.blur())
        }
        closeSocket("chiusura decoder")
        _state.value = ExtConnectionState.Idle
    }

    /** Chiude il socket senza comandi (sessione SND già persa: il server ha chiuso tutto). */
    fun closeSocket(reason: String) {
        sessionId.incrementAndGet()
        closingByClient = true
        keepaliveJob?.cancel()
        keepaliveJob = null
        readyTimeoutJob?.cancel()
        readyTimeoutJob = null
        queue?.close(reason)
        queue = null
        if (_state.value !is ExtConnectionState.Failed) _state.value = ExtConnectionState.Idle
    }

    private fun startReadyTimeout(id: Long, extension: String) {
        readyTimeoutJob?.cancel()
        readyTimeoutJob = scope?.launch {
            delay(readyTimeoutMs)
            if (id == sessionId.get() && _state.value is ExtConnectionState.Connecting) {
                AppLogger.warn(TAG, "Nessun 'EXT ready' da $extension entro $readyTimeoutMs ms")
                fail(id, MESSAGE_EXTENSION_MISSING, permanent = true)
            }
        }
    }

    private fun startKeepalive(id: Long) {
        keepaliveJob?.cancel()
        keepaliveJob = scope?.launch {
            while (isActive && id == sessionId.get()) {
                delay(keepaliveIntervalMs)
                send(ExtProtocol.KEEPALIVE)
            }
        }
    }

    private fun fail(id: Long, message: String, permanent: Boolean) {
        if (id != sessionId.get()) return
        val name = extensionName ?: "?"
        closeSocket("errore")
        _state.value = ExtConnectionState.Failed(name, message, permanent)
    }

    private fun handleFrame(id: Long, bytes: ByteArray) {
        if (id != sessionId.get()) return
        receivedAnyMessage = true
        when (ExtMessageParser.tagOf(bytes)) {
            "DAT" -> ExtMessageParser.parseData(bytes)?.let { if (isReady()) _events.tryEmit(ExtEvent.Data(it)) }
            "MSG", "EXT" -> ExtMessageParser.parseText(bytes)?.let { handleText(id, it) }
        }
    }

    private fun isReady() = _state.value is ExtConnectionState.Ready

    private fun handleText(id: Long, message: ExtTextMessage) {
        if (message.tag == "MSG") {
            message.value("version_maj")?.let { major -> serverVersion = "$major.${message.value("version_min").orEmpty()}" }
            message.value("badp")?.let { if (it != "0") fail(id, MESSAGE_AUTH, permanent = true) }
            if (message.value("ext_client_init") == "1") {
                fail(id, MESSAGE_LOCKED, permanent = true)
                return
            }
            if (message.has("keepalive")) return
            if (message.has("too_busy") || message.has("kiwi_kick")) {
                fail(id, MESSAGE_NOT_ALLOWED, permanent = true)
                return
            }
        }
        if (message.tag == "EXT" && message.has("ready") && _state.value is ExtConnectionState.Connecting) {
            readyTimeoutJob?.cancel()
            val name = extensionName ?: "?"
            AppLogger.info(TAG, "Estensione $name pronta (server ${serverVersion ?: "?"})")
            _state.value = ExtConnectionState.Ready(name)
            pendingStartCommands.forEach(::send)
            startKeepalive(id)
        }
        if (isReady()) _events.tryEmit(ExtEvent.Text(message))
    }

    private inner class Listener(private val id: Long) : KiwiWebSocketListener {
        override fun onText(text: String) {
            AppLogger.trace(TAG, "[EXT TEXT] $text")
        }

        override fun onBinary(bytes: ByteArray) {
            scope?.launch(frameDispatcher) {
                try {
                    handleFrame(id, bytes)
                } catch (e: Exception) {
                    AppLogger.error(TAG, "Errore nell'elaborazione di un frame /EXT", e)
                }
            }
        }

        override fun onClosed(code: Int, reason: String) {
            scope?.launch(frameDispatcher) { handleClosed(id, "Connessione /EXT chiusa dal server (codice $code).") }
        }

        override fun onError(error: Throwable) {
            scope?.launch(frameDispatcher) {
                handleClosed(id, error.message?.takeIf { it.isNotBlank() } ?: "Errore /EXT senza dettagli")
            }
        }
    }

    private fun handleClosed(id: Long, message: String) {
        if (id != sessionId.get() || closingByClient) return
        AppLogger.info(TAG, message)
        // Chiusura senza alcun messaggio: è il rifiuto del limite `ext_api_nchans`.
        if (!receivedAnyMessage) fail(id, MESSAGE_NOT_ALLOWED, permanent = true) else fail(id, message, permanent = false)
    }
}
