package com.grabber.kiwi.ext

import com.grabber.decoder.DigitalDecoderCoordinator
import com.grabber.decoder.LocalDecoderFactory
import com.grabber.kiwi.ExtProtocol
import com.grabber.kiwi.KiwiProtocol
import com.grabber.kiwi.KiwiWebSocket
import com.grabber.kiwi.KiwiWebSocketConnector
import com.grabber.kiwi.KiwiWebSocketListener
import com.grabber.model.digital.DecoderState
import com.grabber.model.digital.DigitalMode
import com.grabber.model.digital.DigitalModeParams
import com.grabber.model.digital.Ft8Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Collections
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sessione `/EXT` su un trasporto finto: sequenza dei comandi, rifiuti, timeout e chiusura. */
class ExtensionClientTests {

    private class FakeSocket : KiwiWebSocket {
        val sent: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override fun sendText(text: String): CompletableFuture<Unit> {
            sent += text
            return CompletableFuture.completedFuture(Unit)
        }

        override fun close(code: Int, reason: String): CompletableFuture<Unit> {
            sent += "CLOSE"
            return CompletableFuture.completedFuture(Unit)
        }
    }

    private class FakeConnector(private val refuse: Boolean = false) : KiwiWebSocketConnector {
        val sockets: MutableList<FakeSocket> = Collections.synchronizedList(mutableListOf())
        val uris: MutableList<String> = Collections.synchronizedList(mutableListOf())
        @Volatile var listener: KiwiWebSocketListener? = null

        val socket: FakeSocket get() = sockets.last()

        override fun connect(uri: String, headers: Map<String, String>, listener: KiwiWebSocketListener): CompletableFuture<KiwiWebSocket> {
            uris += uri
            if (refuse) return CompletableFuture.failedFuture(IllegalStateException("handshake rifiutato"))
            this.listener = listener
            val socket = FakeSocket()
            sockets += socket
            return CompletableFuture.completedFuture(socket)
        }

        fun receive(text: String) = requireNotNull(listener).onBinary(text.toByteArray())
    }

    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(5_000) {
        while (!condition()) delay(10)
    }

    private fun withScope(block: suspend (CoroutineScope) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            block(scope)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun handshakeSendsStartCommandsOnlyAfterReady() = withScope { scope ->
        val connector = FakeConnector()
        val client = ExtensionClient(connector, host = "kiwi.test", port = 8073, password = "pw", clientName = "Test")
        client.open(scope, 42L, "FT8", listOf(ExtProtocol.Ft8.start(0)))
        waitFor { connector.sockets.isNotEmpty() && connector.socket.sent.size >= 3 }
        assertEquals("ws://kiwi.test:8073/42/EXT", connector.uris.single())
        assertEquals(listOf(KiwiProtocol.auth("pw"), KiwiProtocol.identUser("Test"), ExtProtocol.switchToClient("FT8")), connector.socket.sent.take(3))
        assertTrue(ExtProtocol.Ft8.start(0) !in connector.socket.sent)

        connector.receive("MSG badp=0")
        connector.receive("MSG ext_client_init=0")
        connector.receive("EXT ready")
        waitFor { client.state.value is ExtConnectionState.Ready }
        waitFor { ExtProtocol.Ft8.start(0) in connector.socket.sent }

        client.close(listOf(ExtProtocol.Ft8.CLOSE))
        waitFor { "CLOSE" in connector.socket.sent }
        val sent = connector.socket.sent
        assertTrue(sent.indexOf(ExtProtocol.Ft8.CLOSE) < sent.indexOf(ExtProtocol.blur()))
        assertTrue(sent.indexOf(ExtProtocol.blur()) < sent.indexOf("CLOSE"))
        assertEquals(ExtConnectionState.Idle, client.state.value)
    }

    @Test
    fun lockedReceiverIsAPermanentRejection() = withScope { scope ->
        val connector = FakeConnector()
        val client = ExtensionClient(connector)
        client.open(scope, 1L, "FT8", emptyList())
        waitFor { connector.listener != null }
        connector.receive("MSG ext_client_init=1")
        waitFor { client.state.value is ExtConnectionState.Failed }
        val failed = client.state.value as ExtConnectionState.Failed
        assertTrue(failed.permanent)
        assertEquals(ExtensionClient.MESSAGE_LOCKED, failed.message)
    }

    @Test
    fun refusedSocketExplainsTheAppConnectionLimit() = withScope { scope ->
        val client = ExtensionClient(FakeConnector(refuse = true))
        client.open(scope, 1L, "FT8", emptyList())
        waitFor { client.state.value is ExtConnectionState.Failed }
        assertEquals(ExtensionClient.MESSAGE_NOT_ALLOWED, (client.state.value as ExtConnectionState.Failed).message)
    }

    @Test
    fun closeWithoutAnyMessageIsTheAppConnectionLimit() = withScope { scope ->
        val connector = FakeConnector()
        val client = ExtensionClient(connector)
        client.open(scope, 1L, "FT8", emptyList())
        waitFor { connector.listener != null && connector.sockets.isNotEmpty() }
        requireNotNull(connector.listener).onClosed(1005, "")
        waitFor { client.state.value is ExtConnectionState.Failed }
        val failed = client.state.value as ExtConnectionState.Failed
        assertTrue(failed.permanent)
        assertEquals(ExtensionClient.MESSAGE_NOT_ALLOWED, failed.message)
    }

    @Test
    fun missingReadyTimesOut() = withScope { scope ->
        val connector = FakeConnector()
        val client = ExtensionClient(connector, readyTimeoutMs = 200)
        client.open(scope, 1L, "NONEXISTENT", emptyList())
        waitFor { client.state.value is ExtConnectionState.Failed }
        assertEquals(ExtensionClient.MESSAGE_EXTENSION_MISSING, (client.state.value as ExtConnectionState.Failed).message)
    }

    @Test
    fun coordinatorDecodesAndReopensAfterReconnection() = withScope { scope ->
        val connector = FakeConnector()
        val client = ExtensionClient(connector)
        val authenticated = MutableStateFlow(true)
        val coordinator = DigitalDecoderCoordinator(
            scope = scope,
            extensionClient = client,
            isAuthenticated = authenticated,
            sessionToken = { 7L },
            carrierKhz = { 7074.0 },
            localDecoders = LocalDecoderFactory { _, _ -> null }
        )
        coordinator.start(DigitalMode.FT8, DigitalModeParams(), 7074.0)
        waitFor { connector.listener != null }
        connector.receive("EXT ready")
        waitFor { coordinator.state.value == DecoderState.Running(DigitalMode.FT8) }
        connector.receive("EXT chars=16%3a50%3a15%20-05.5%20-1.08%201484%20%20%20%20%20%20%20%20%20%20%201.0%20%20R4CGV%20EA1AM%20-17%0a")
        waitFor { coordinator.items.value.isNotEmpty() }
        assertEquals("R4CGV EA1AM -17", (coordinator.items.value.single() as Ft8Message).message)

        authenticated.value = false
        waitFor { coordinator.state.value is DecoderState.Error }
        authenticated.value = true
        waitFor { connector.sockets.size == 2 }
        connector.receive("EXT ready")
        waitFor { coordinator.state.value == DecoderState.Running(DigitalMode.FT8) }

        coordinator.stop()
        waitFor { coordinator.state.value == DecoderState.Idle }
        waitFor { ExtProtocol.Ft8.CLOSE in connector.socket.sent }
    }

    @Test
    fun permanentRejectionIsNotRetriedOnReconnection() = withScope { scope ->
        val connector = FakeConnector()
        val client = ExtensionClient(connector)
        val authenticated = MutableStateFlow(true)
        val coordinator = DigitalDecoderCoordinator(scope, client, authenticated, { 7L }, { 0.0 }, LocalDecoderFactory { _, _ -> null })
        coordinator.start(DigitalMode.FT8, DigitalModeParams(), 7074.0)
        waitFor { connector.listener != null }
        connector.receive("MSG ext_client_init=1")
        waitFor { coordinator.state.value is DecoderState.Unavailable }
        authenticated.value = false
        delay(100)
        authenticated.value = true
        delay(300)
        assertEquals(1, connector.sockets.size)
        assertTrue(coordinator.state.value is DecoderState.Unavailable)
    }
}
