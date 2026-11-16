package com.grabber.kiwi

import java.io.IOException
import java.util.concurrent.CompletableFuture
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
        assertEquals(listOf("SET tune", "CLOSE $WEBSOCKET_NORMAL_CLOSURE bye"), socket.started)
    }

    @Test
    fun transportThatAcceptsImmediatelyKeepsCommandOrder() {
        val socket = RecordingWebSocket(completeImmediately = true)
        val queue = WebSocketCommandQueue(socket)

        (1..5).forEach { queue.send("SET cmd$it") }
        queue.close("bye")

        assertEquals((1..5).map { "SET cmd$it" } + "CLOSE $WEBSOCKET_NORMAL_CLOSURE bye", socket.started)
    }

    /** Trasporto finto: registra l'ordine di avvio degli invii e li completa a comando. */
    private class RecordingWebSocket(private val completeImmediately: Boolean = false) : KiwiWebSocket {
        val started = mutableListOf<String>()
        private val pending = ArrayDeque<CompletableFuture<Unit>>()

        fun completeNext() {
            pending.removeFirst().complete(Unit)
        }

        fun failNext(error: Throwable) {
            pending.removeFirst().completeExceptionally(error)
        }

        private fun record(operation: String): CompletableFuture<Unit> {
            started += operation
            if (completeImmediately) return CompletableFuture.completedFuture(Unit)
            return CompletableFuture<Unit>().also { pending.addLast(it) }
        }

        override fun sendText(text: String) = record(text)
        override fun close(code: Int, reason: String) = record("CLOSE $code $reason")
    }
}
