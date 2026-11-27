package com.grabber.app

import com.grabber.model.ModulationMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionResumeTests {

    private val target = ScanTarget(27005.0, ModulationMode.AM, "27005")

    private class Harness(scope: TestScope) {
        val authenticated = MutableStateFlow(true)
        val scanState = MutableStateFlow<ScanState>(ScanState.Idle)
        var recording = false
        val events = mutableListOf<String>()
        val resumedScans = mutableListOf<ScanResumePoint>()
        val resume = ConnectionResumeCoordinator(
            scope = scope.backgroundScope,
            isAuthenticated = authenticated,
            scanState = scanState,
            isRecording = { recording },
            suspendRecording = { recording = false; events += "suspend" },
            resumeRecording = { recording = true; events += "resume" },
            resumeScan = { resumedScans += it }
        )
    }

    /** Caduta di rete: il controller memorizza e poi vede la scansione fermarsi (stato Idle). */
    private fun Harness.loseConnection() {
        authenticated.value = false
        resume.onConnectionLost()
        scanState.value = ScanState.Idle
    }

    private fun TestScope.finishResume() {
        advanceTimeBy(RESUME_DELAY_MS + 1)
        runCurrent()
    }

    @Test
    fun resumesRecordingAndScanAfterReconnectAndAuthentication() = runTest {
        val h = Harness(this)
        h.recording = true
        h.scanState.value = ScanState.Scanning(ScanKind.RANGE, target, position = 7, total = 45)

        h.loseConnection()
        assertEquals(listOf("suspend"), h.events)
        assertFalse(h.recording)

        // Tentativi falliti: un secondo Reconnecting non cancella quanto memorizzato.
        h.resume.onConnectionLost()
        h.resume.onConnected()
        finishResume()
        assertEquals(listOf("suspend"), h.events, "senza autenticazione non si riprende nulla")

        h.authenticated.value = true
        runCurrent()
        finishResume()
        assertEquals(listOf("suspend", "resume"), h.events)
        assertEquals(listOf(ScanResumePoint(ScanKind.RANGE, 6)), h.resumedScans)
        assertFalse(h.resume.hasPendingResume)
    }

    @Test
    fun scanStoppedOnSignalResumesFromSameStep() = runTest {
        val h = Harness(this)
        h.scanState.value = ScanState.SignalFound(ScanKind.CHANNELS, target, position = 3, total = 5)
        h.loseConnection()
        h.authenticated.value = true
        h.resume.onConnected()
        finishResume()
        assertEquals(listOf(ScanResumePoint(ScanKind.CHANNELS, 2)), h.resumedScans)
        assertTrue(h.events.isEmpty())
    }

    @Test
    fun manualDisconnectForgetsEverything() = runTest {
        val h = Harness(this)
        h.recording = true
        h.scanState.value = ScanState.Scanning(ScanKind.RANGE, target, 1, 10)
        h.loseConnection()
        h.resume.clear()
        h.authenticated.value = true
        h.resume.onConnected()
        finishResume()
        assertEquals(listOf("suspend"), h.events)
        assertTrue(h.resumedScans.isEmpty())
    }

    @Test
    fun userTuningDuringReconnectCancelsOnlyTheScan() = runTest {
        val h = Harness(this)
        h.recording = true
        h.scanState.value = ScanState.Scanning(ScanKind.RANGE, target, 1, 10)
        h.loseConnection()
        h.resume.forgetScan()
        h.authenticated.value = true
        h.resume.onConnected()
        finishResume()
        assertEquals(listOf("suspend", "resume"), h.events)
        assertTrue(h.resumedScans.isEmpty())
    }

    @Test
    fun nothingToResumeWhenIdle() = runTest {
        val h = Harness(this)
        h.loseConnection()
        assertFalse(h.resume.hasPendingResume)
        h.resume.onConnected()
        finishResume()
        assertTrue(h.events.isEmpty() && h.resumedScans.isEmpty())
    }

    @Test
    fun scanCoordinatorStartsFromGivenStep() = runTest {
        val tuned = mutableListOf<ScanTarget>()
        val targets = (0 until 5).map { ScanTarget(27000.0 + it * 10, ModulationMode.AM, "ch$it") }
        val scanner = ScanCoordinator(
            scope = backgroundScope,
            signalGateOpen = MutableStateFlow(false),
            tuneTo = { tuned += it },
            resetSignalDetection = {}
        )
        val plan = ScanPlan(ScanKind.RANGE, { ListScanSource(targets) }, { 1000L }, { true })
        assertTrue(scanner.start(plan, startIndex = 3))
        runCurrent()
        assertEquals(listOf(targets[3]), tuned)
        assertEquals(ScanState.Scanning(ScanKind.RANGE, targets[3], 4, 5), scanner.state.value)
        advanceTimeBy(SCAN_SETTLE_MS + 1000L)
        runCurrent()
        advanceTimeBy(SCAN_SETTLE_MS + 1000L)
        runCurrent()
        assertEquals(listOf(targets[3], targets[4], targets[0]), tuned)
        scanner.stop()
    }
}
