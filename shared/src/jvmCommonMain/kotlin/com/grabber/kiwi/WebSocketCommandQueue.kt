package com.grabber.kiwi

import java.util.concurrent.CompletableFuture

/**
 * Serializza i comandi testuali verso un [KiwiWebSocket] senza bloccare il chiamante.
 *
 * `java.net.http.WebSocket` rifiuta un nuovo invio finché il precedente non è completato:
 * ogni operazione viene quindi accodata alla precedente, anche se quest'ultima è fallita.
 * Anche con trasporti che accodano da soli (OkHttp) la coda resta l'unico punto di invio.
 */
internal class WebSocketCommandQueue(private val webSocket: KiwiWebSocket) {
    private val lock = Any()
    private var tail: CompletableFuture<*> = CompletableFuture.completedFuture(null)

    fun send(command: String): CompletableFuture<*> = enqueue { webSocket.sendText(command) }

    fun close(reason: String): CompletableFuture<*> = enqueue { webSocket.close(WEBSOCKET_NORMAL_CLOSURE, reason) }

    private fun enqueue(operation: () -> CompletableFuture<Unit>): CompletableFuture<*> =
        synchronized(lock) {
            val next = tail.handle { _, _ -> Unit }.thenCompose { operation() }
            tail = next
            next
        }
}
