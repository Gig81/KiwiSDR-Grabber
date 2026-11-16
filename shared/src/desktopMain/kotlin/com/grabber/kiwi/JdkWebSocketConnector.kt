package com.grabber.kiwi

import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Trasporto desktop basato su `java.net.http.WebSocket`. Ricompone i messaggi frammentati
 * prima di passarli al listener, così i client ricevono sempre messaggi completi.
 */
class JdkWebSocketConnector(
    private val httpClient: HttpClient = HttpClient.newHttpClient()
) : KiwiWebSocketConnector {

    override fun connect(
        uri: String,
        headers: Map<String, String>,
        listener: KiwiWebSocketListener
    ): CompletableFuture<KiwiWebSocket> {
        val builder = httpClient.newWebSocketBuilder()
        headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.buildAsync(URI.create(uri), ReassemblingListener(listener))
            .thenApply { JdkKiwiWebSocket(it) }
    }

    private class JdkKiwiWebSocket(private val webSocket: WebSocket) : KiwiWebSocket {
        override fun sendText(text: String): CompletableFuture<Unit> =
            webSocket.sendText(text, true).thenApply { }

        override fun close(code: Int, reason: String): CompletableFuture<Unit> =
            webSocket.sendClose(code, reason).thenApply { }
    }

    private class ReassemblingListener(private val listener: KiwiWebSocketListener) : WebSocket.Listener {
        private var binaryBuffer = ByteBuffer.allocate(65536)
        private val textBuffer = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
            textBuffer.append(data)
            if (last) {
                val fullText = textBuffer.toString()
                textBuffer.clear()
                listener.onText(fullText)
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
                listener.onBinary(bytes)
            }

            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*> {
            listener.onClosed(statusCode, reason)
            return CompletableFuture.completedFuture(null)
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            listener.onError(error)
        }
    }
}
