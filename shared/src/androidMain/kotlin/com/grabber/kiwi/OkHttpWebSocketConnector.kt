package com.grabber.kiwi

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Trasporto Android basato su OkHttp. OkHttp consegna già messaggi completi e accoda gli invii;
 * [WebSocketCommandQueue] resta comunque l'unico punto di invio dei client.
 */
class OkHttpWebSocketConnector(
    private val client: OkHttpClient = defaultClient()
) : KiwiWebSocketConnector {

    override fun connect(
        uri: String,
        headers: Map<String, String>,
        listener: KiwiWebSocketListener
    ): CompletableFuture<KiwiWebSocket> {
        val opened = CompletableFuture<KiwiWebSocket>()
        // OkHttp accetta anche gli schemi ws:// e wss:// e li converte in http(s).
        val request = Request.Builder().url(uri).apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        client.newWebSocket(request, ForwardingListener(listener, opened, AtomicBoolean(false)))
        return opened
    }

    private class OkHttpKiwiWebSocket(
        private val webSocket: WebSocket,
        private val closeRequested: AtomicBoolean
    ) : KiwiWebSocket {
        override fun sendText(text: String): CompletableFuture<Unit> =
            if (webSocket.send(text)) {
                CompletableFuture.completedFuture(Unit)
            } else {
                CompletableFuture<Unit>().also { it.completeExceptionally(IOException("WebSocket chiuso: invio rifiutato")) }
            }

        override fun close(code: Int, reason: String): CompletableFuture<Unit> {
            closeRequested.set(true)
            if (!webSocket.close(code, reason)) webSocket.cancel()
            return CompletableFuture.completedFuture(Unit)
        }
    }

    private class ForwardingListener(
        private val listener: KiwiWebSocketListener,
        private val opened: CompletableFuture<KiwiWebSocket>,
        private val closeRequested: AtomicBoolean
    ) : WebSocketListener() {
        private val finished = AtomicBoolean(false)

        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened.complete(OkHttpKiwiWebSocket(webSocket, closeRequested))
        }

        override fun onMessage(webSocket: WebSocket, text: String) = listener.onText(text)

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = listener.onBinary(bytes.toByteArray())

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(WEBSOCKET_NORMAL_CLOSURE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (finished.compareAndSet(false, true)) listener.onClosed(code, reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            // Fallimento dell'handshake: lo segnala il future di connect(), non il listener.
            if (opened.completeExceptionally(t)) return
            if (!finished.compareAndSet(false, true)) return
            // Chiusura chiesta da noi ma non confermata (es. rete assente): non è un errore.
            if (closeRequested.get()) listener.onClosed(WEBSOCKET_NORMAL_CLOSURE, "chiusura locale") else listener.onError(t)
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            // Ping di controllo: rileva in fretta le connessioni morte dopo un cambio di rete.
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
}
