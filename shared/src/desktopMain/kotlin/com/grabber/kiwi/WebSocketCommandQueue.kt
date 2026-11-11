package com.grabber.kiwi

import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture

/**
 * Serializza i comandi testuali verso un WebSocket del JDK senza bloccare il chiamante.
 *
 * `java.net.http.WebSocket` rifiuta un nuovo invio finché il precedente non è completato:
 * ogni operazione viene quindi accodata alla precedente, anche se quest'ultima è fallita.
 */
internal class WebSocketCommandQueue(private val webSocket: WebSocket) {
    private val lock = Any()
    private var tail: CompletableFuture<*> = CompletableFuture.completedFuture(null)

    fun send(command: String): CompletableFuture<*> = enqueue { webSocket.sendText(command, true) }

    fun close(reason: String): CompletableFuture<*> = enqueue { webSocket.sendClose(WebSocket.NORMAL_CLOSURE, reason) }

    private fun enqueue(operation: () -> CompletableFuture<WebSocket>): CompletableFuture<*> =
        synchronized(lock) {
            val next = tail.handle { _, _ -> Unit }.thenCompose { operation() }
            tail = next
            next
        }
}
