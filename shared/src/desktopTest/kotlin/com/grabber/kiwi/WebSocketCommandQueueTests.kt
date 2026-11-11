package com.grabber.kiwi

import java.io.IOException
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebSocketCommandQueueTests {

    @Test
    fun nextCommandStartsOnlyAfterPreviousSendCompletes() {
        val socket = RecordingWebSocket()
        val queue = WebSocketCommandQueue(socket)

        queue.send("SET first")
        queue.send("SET second")
        assertEquals(listOf("SET first"), socket.started)

        socket.completeNext()
        assertEquals(listOf("SET first", "SET second"), socket.started)
    }

    @Test
    fun failedSendDoesNotBlockFollowingCommands() {
        val socket = RecordingWebSocket()
        val queue = WebSocketCommandQueue(socket)

        val failed = queue.send("SET broken")
        queue.send("SET keepalive")
        socket.failNext(IOException("output closed"))

        assertTrue(failed.isCompletedExceptionally)
        assertEquals(listOf("SET broken", "SET keepalive"), socket.started)
    }

    @Test
    fun closeIsQueuedAfterPendingCommands() {
        val socket = RecordingWebSocket()
        val queue = WebSocketCommandQueue(socket)

        queue.send("SET tune")
        queue.close("bye")
        assertEquals(listOf("SET tune"), socket.started)

        socket.completeNext()
        assertEquals(listOf("SET tune", "CLOSE bye"), socket.started)
    }

    private class RecordingWebSocket : WebSocket {
        val started = mutableListOf<String>()
        private val pending = ArrayDeque<CompletableFuture<WebSocket>>()

        fun completeNext() {
            pending.removeFirst().complete(this)
        }

        fun failNext(error: Throwable) {
            pending.removeFirst().completeExceptionally(error)
        }

        private fun record(operation: String): CompletableFuture<WebSocket> {
            started += operation
            return CompletableFuture<WebSocket>().also { pending.addLast(it) }
        }

        override fun sendText(data: CharSequence, last: Boolean) = record(data.toString())
        override fun sendClose(statusCode: Int, reason: String) = record("CLOSE $reason")
        override fun sendBinary(data: ByteBuffer, last: Boolean): CompletableFuture<WebSocket> = throw UnsupportedOperationException()
        override fun sendPing(message: ByteBuffer): CompletableFuture<WebSocket> = throw UnsupportedOperationException()
        override fun sendPong(message: ByteBuffer): CompletableFuture<WebSocket> = throw UnsupportedOperationException()
        override fun request(n: Long) = Unit
        override fun getSubprotocol() = ""
        override fun isOutputClosed() = false
        override fun isInputClosed() = false
        override fun abort() = Unit
    }
}
