package com.grabber.kiwi

import com.grabber.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Collections
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sessione SND completa su un trasporto finto: stessa logica su desktop (JDK) e Android (OkHttp). */
class KiwiClientTransportTests {

    private class FakeSocket : KiwiWebSocket {
        val sent: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override fun sendText(text: String): CompletableFuture<Unit> {
            sent += text
            return CompletableFuture.completedFuture(Unit)
        }

        override fun close(code: Int, reason: String): CompletableFuture<Unit> {
            sent += "CLOSE $code $reason"
            return CompletableFuture.completedFuture(Unit)
        }
    }

    private class FakeConnector(val socket: FakeSocket) : KiwiWebSocketConnector {
        @Volatile var uri: String? = null
        @Volatile var headers: Map<String, String> = emptyMap()
        @Volatile lateinit var listener: KiwiWebSocketListener

        override fun connect(uri: String, headers: Map<String, String>, listener: KiwiWebSocketListener): CompletableFuture<KiwiWebSocket> {
            this.uri = uri
            this.headers = headers
            this.listener = listener
            return CompletableFuture.completedFuture(socket)
        }
    }

    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(5_000) {
        while (!condition()) delay(10)
    }

    @Test
    fun sessionAuthenticatesHandshakesAndDecodesAudio() = runBlocking {
        val socket = FakeSocket()
        val connector = FakeConnector(socket)
        val client = KiwiClient(connector, host = "kiwi.test", port = 8073, password = "segreta", clientName = "Test", userAgent = "UA/1")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            client.connect(scope)
            withTimeout(5_000) { client.connectionState.first { it is ConnectionState.Connected } }

            val token = client.sessionToken.value
            assertEquals("ws://kiwi.test:8073/$token/SND", connector.uri)
            assertEquals("UA/1", connector.headers["User-Agent"])
            assertEquals("http://kiwi.test:8073", connector.headers["Origin"])
            waitFor { socket.sent.size >= 2 }
            assertEquals(KiwiProtocol.auth("segreta"), socket.sent[0])
            assertEquals(KiwiProtocol.identUser("Test"), socket.sent[1])

            connector.listener.onBinary("MSG audio_rate=12000 badp=0".toByteArray())
            waitFor { client.isAuthenticated.value && KiwiProtocol.arOk(12000, 12000) in socket.sent }

            val pcmBigEndian = byteArrayOf(0x01, 0x02, 0x03, 0x04)
            val frame = "SND".toByteArray() + byteArrayOf(0x00, 7, 0, 0, 0, 0x04, 0xB0.toByte()) + pcmBigEndian
            val audio = async { withTimeout(5_000) { client.audioFlow.first() } }
            delay(50)
            connector.listener.onBinary(frame)
            val decoded = audio.await()
            assertEquals(7L, decoded.sequence)
            assertEquals(12000, decoded.sampleRate)
            assertContentEquals(byteArrayOf(0x02, 0x01, 0x04, 0x03), decoded.pcmBytes)

            client.disconnect()
            assertTrue(socket.sent.last().startsWith("CLOSE $WEBSOCKET_NORMAL_CLOSURE"))
            assertEquals(ConnectionState.Disconnected, client.connectionState.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun badpRejectionStopsTheSessionWithoutRetrying() = runBlocking {
        val connector = FakeConnector(FakeSocket())
        val client = KiwiClient(connector, host = "kiwi.test", port = 8073)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            client.connect(scope)
            withTimeout(5_000) { client.connectionState.first { it is ConnectionState.Connected } }
            connector.listener.onBinary("MSG badp=1".toByteArray())
            waitFor { client.isManualDisconnected() }
            delay(100)
            assertTrue(client.connectionState.value is ConnectionState.Disconnected)
            client.retryReconnectNow()
            assertTrue(client.connectionState.value is ConnectionState.Disconnected)
        } finally {
            scope.cancel()
        }
    }
}
