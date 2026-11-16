package com.grabber.kiwi

import java.util.concurrent.CompletableFuture

/** Codice di chiusura WebSocket standard (RFC 6455) per una chiusura volontaria. */
const val WEBSOCKET_NORMAL_CLOSURE = 1000

/**
 * WebSocket già aperto, indipendente dalla libreria usata (JDK su desktop, OkHttp su Android).
 *
 * Ogni invio restituisce un future che si completa quando il trasporto ha accettato il messaggio:
 * gli invii vanno comunque serializzati da [WebSocketCommandQueue], unico punto di invio dei client.
 */
interface KiwiWebSocket {
    fun sendText(text: String): CompletableFuture<Unit>
    fun close(code: Int, reason: String): CompletableFuture<Unit>
}

/**
 * Eventi di un WebSocket. I messaggi arrivano già ricomposti (frammenti uniti dal trasporto)
 * e nell'ordine di ricezione, sempre dallo stesso thread del trasporto.
 */
interface KiwiWebSocketListener {
    fun onText(text: String)
    fun onBinary(bytes: ByteArray)
    fun onClosed(code: Int, reason: String)
    fun onError(error: Throwable)
}

/** Apre i WebSocket verso il KiwiSDR; l'implementazione dipende dalla piattaforma. */
fun interface KiwiWebSocketConnector {
    /** Il future si completa a connessione aperta, o fallisce se l'handshake non riesce. */
    fun connect(
        uri: String,
        headers: Map<String, String>,
        listener: KiwiWebSocketListener
    ): CompletableFuture<KiwiWebSocket>
}
