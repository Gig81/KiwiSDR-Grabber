package com.grabber.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val DWELL_MS = 3000L
private const val STEP_MS = SCAN_SETTLE_MS + DWELL_MS

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelScanTests {

    private val ch1 = FavoriteFrequency("CB 1", 26965.0, "am", "CB", scanEnabled = true)
    private val ch2 = FavoriteFrequency("CB 2", 26975.0, "am", "CB", scanEnabled = true)
    private val ch3 = FavoriteFrequency("CB 3", 26985.0, "am", "CB", scanEnabled = true)

    private class Harness(
        scope: TestScope,
        channels: List<FavoriteFrequency>,
        autoResume: Boolean = true
    ) {
        val selected = MutableStateFlow(channels)
        val gate = MutableStateFlow(false)
        val tuned = mutableListOf<ScanTarget>()
        val stoppedOn = mutableListOf<ScanTarget>()
        val scanner = ScanCoordinator(
            scope = scope.backgroundScope,
            signalGateOpen = gate,
            tuneTo = { tuned += it },
            resetSignalDetection = { gate.value = false },
            onStoppedOnSignal = { _, target -> stoppedOn += target }
        )
        val plan = ScanPlan(
            kind = ScanKind.CHANNELS,
            source = { ListScanSource(selected.value.map { it.toScanTarget() }) },
            dwellMillis = { DWELL_MS },
            autoResume = { autoResume }
        )
    }

    private val FavoriteFrequency.t: ScanTarget get() = toScanTarget()

    private fun TestScope.advanceStep(ms: Long = STEP_MS) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun cyclesThroughSelectedChannelsWithoutSignal() = runTest {
        val h = Harness(this, listOf(ch1, ch2, ch3))
        assertTrue(h.scanner.start(h.plan))
        runCurrent()
        assertEquals(listOf(ch1.t), h.tuned)
        assertEquals(ScanState.Scanning(ScanKind.CHANNELS, ch1.t, 1, 3), h.scanner.state.value)

        advanceStep()
        advanceStep()
        advanceStep()
        assertEquals(listOf(ch1, ch2, ch3, ch1).map { it.t }, h.tuned)
        h.scanner.stop()
    }

    @Test
    fun staysOnSignalAndResumesWhenSquelchCloses() = runTest {
        val h = Harness(this, listOf(ch1, ch2))
        h.scanner.start(h.plan)
        runCurrent()
        advanceStep(SCAN_SETTLE_MS + 500)
        h.gate.value = true
        runCurrent()
        assertIs<ScanState.SignalFound>(h.scanner.state.value)

        advanceStep(60_000)
        assertEquals(listOf(ch1.t), h.tuned, "durante il segnale la scansione non cambia canale")

        h.gate.value = false
        runCurrent()
        assertEquals(listOf(ch1, ch2).map { it.t }, h.tuned)
        assertEquals(ScanState.Scanning(ScanKind.CHANNELS, ch2.t, 2, 2), h.scanner.state.value)
        h.scanner.stop()
    }

    @Test
    fun stopsOnSignalWhenAutoResumeIsDisabled() = runTest {
        val h = Harness(this, listOf(ch1, ch2), autoResume = false)
        h.scanner.start(h.plan)
        runCurrent()
        advanceStep(SCAN_SETTLE_MS + 100)
        h.gate.value = true
        runCurrent()

        assertEquals(listOf(ch1.t), h.stoppedOn)
        assertEquals(ScanState.Idle, h.scanner.state.value)
        assertFalse(h.scanner.isActive)
        advanceStep(60_000)
        assertEquals(listOf(ch1.t), h.tuned)
    }

    @Test
    fun signalDuringSettleTimeIsIgnored() = runTest {
        val h = Harness(this, listOf(ch1, ch2))
        h.scanner.start(h.plan)
        runCurrent()
        // Frame residui della frequenza precedente: lo squelch viene azzerato dopo l'assestamento.
        h.gate.value = true
        advanceStep()
        assertEquals(listOf(ch1, ch2).map { it.t }, h.tuned)
        h.scanner.stop()
    }

    @Test
    fun stopPreventsFurtherTuning() = runTest {
        val h = Harness(this, listOf(ch1, ch2))
        h.scanner.start(h.plan)
        runCurrent()
        h.scanner.stop()
        advanceStep(60_000)
        assertEquals(listOf(ch1.t), h.tuned)
        assertEquals(ScanState.Idle, h.scanner.state.value)
    }

    @Test
    fun selectionChangesApplyDuringScan() = runTest {
        val h = Harness(this, listOf(ch1, ch2, ch3))
        h.scanner.start(h.plan)
        runCurrent()
        h.selected.value = listOf(ch1, ch3)
        advanceStep()
        advanceStep()
        assertEquals(listOf(ch1, ch3, ch1).map { it.t }, h.tuned)
        h.scanner.stop()
    }

    @Test
    fun cannotStartWithoutSelectedChannelsOrTwice() = runTest {
        val empty = Harness(this, emptyList())
        assertFalse(empty.scanner.start(empty.plan))

        val h = Harness(this, listOf(ch1))
        assertTrue(h.scanner.start(h.plan))
        assertFalse(h.scanner.start(h.plan))
        h.scanner.stop()
    }

    @Test
    fun scanSelectionAndDwellArePersisted() {
        val settingsDir = Files.createTempDirectory("kiwigrabber-scan-test").toFile()
        try {
            val settings = AppSettings(settingsDir)
            val first = settings.settings.value.favorites.take(2)
            settings.setFavoritesScanEnabled(first, true)
            settings.setScanDwellSeconds(99)
            settings.setScanAutoResume(false)

            val reloaded = AppSettings(settingsDir).settings.value
            assertEquals(2, reloaded.favorites.count { it.scanEnabled })
            assertTrue(reloaded.favorites.take(2).all { it.scanEnabled })
            assertEquals(SCAN_DWELL_SECONDS_RANGE.last, reloaded.scanDwellSeconds)
            assertFalse(reloaded.scanAutoResume)
        } finally {
            settingsDir.listFiles()?.forEach { file ->
                check(file.delete()) { "Unable to remove test settings ${file.absolutePath}" }
            }
            check(settingsDir.delete()) { "Unable to remove test directory ${settingsDir.absolutePath}" }
        }
    }
}
