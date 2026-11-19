package com.grabber.app

import com.grabber.model.ModulationMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val RANGE_DWELL_MS = 1000L
private const val RANGE_STEP_MS = SCAN_SETTLE_MS + RANGE_DWELL_MS

class FrequencyRangeScanSourceTests {

    private fun params(start: Double, end: Double, step: Double, excluded: List<Double> = emptyList()) =
        RangeScanParams(start, end, step, ModulationMode.AM, excluded)

    private fun frequencies(source: ScanSource) = (0 until source.size).map { source.targetAt(it).frequencyKhz }

    @Test
    fun includesEndWhenMultipleOfStep() {
        val source = FrequencyRangeScanSource(params(26965.0, 27405.0, 10.0))
        assertEquals(45, source.size)
        assertEquals(26965.0, source.targetAt(0).frequencyKhz)
        assertEquals(27405.0, source.targetAt(44).frequencyKhz)
    }

    @Test
    fun lastStepNeverExceedsEnd() {
        val source = FrequencyRangeScanSource(params(7000.0, 7012.0, 5.0))
        assertEquals(listOf(7000.0, 7005.0, 7010.0), frequencies(source))
    }

    @Test
    fun noFloatingPointDriftOverManySteps() {
        val source = FrequencyRangeScanSource(params(0.1, 3000.0, 0.1))
        assertEquals(30000, source.size)
        assertEquals(3000.0, source.targetAt(source.size - 1).frequencyKhz)
        assertEquals(1234.5, source.targetAt(12344).frequencyKhz)
        frequencies(source).forEach { f -> assertEquals(f, roundToHz(f)) }
    }

    @Test
    fun excludedFrequenciesAreSkippedWithHalfStepTolerance() {
        val source = FrequencyRangeScanSource(params(27000.0, 27050.0, 10.0, excluded = listOf(27010.0, 27032.0)))
        assertEquals(listOf(27000.0, 27020.0, 27040.0, 27050.0), frequencies(source))
    }

    @Test
    fun singleStepRange() {
        val source = FrequencyRangeScanSource(params(5000.0, 5004.0, 10.0))
        assertEquals(listOf(5000.0), frequencies(source))
    }

    @Test
    fun targetCarriesModeAndLabel() {
        val target = FrequencyRangeScanSource(RangeScanParams(14200.0, 14210.0, 5.0, ModulationMode.USB)).targetAt(1)
        assertEquals(ScanTarget(14205.0, ModulationMode.USB, "14205.000 kHz"), target)
    }

    @Test
    fun validationRejectsInvalidRanges() {
        assertNull(validateRangeScan(params(26965.0, 27405.0, 10.0)))
        assertNotNull(validateRangeScan(params(27405.0, 26965.0, 10.0)), "inizio maggiore della fine")
        assertNotNull(validateRangeScan(params(27000.0, 27000.0, 10.0)), "inizio uguale alla fine")
        assertNotNull(validateRangeScan(params(-1.0, 100.0, 10.0)), "sotto 0 kHz")
        assertNotNull(validateRangeScan(params(29000.0, 30001.0, 10.0)), "oltre 30 MHz")
        assertNotNull(validateRangeScan(params(1000.0, 2000.0, 0.0)), "passo nullo")
        assertNotNull(validateRangeScan(params(1000.0, 2000.0, -5.0)), "passo negativo")
        assertNotNull(validateRangeScan(params(1000.0, 2000.0, 0.05)), "passo troppo piccolo")
        assertNotNull(validateRangeScan(params(1000.0, 2000.0, 2000.0)), "passo troppo grande")
        assertNotNull(validateRangeScan(params(1000.0, 1010.0, 10.0, excluded = listOf(1000.0, 1010.0))), "tutto escluso")
        assertNotNull(validateRangeScan(params(Double.NaN, 1010.0, 10.0)), "NaN")
    }
}

class ScanHitLogTests {

    private val target = ScanTarget(27125.0, ModulationMode.AM, "27125.000 kHz")

    @Test
    fun repeatedSignalUpdatesExistingEntry() {
        val log = ScanHitLog()
        log.record(target, -90.0, 1_000)
        log.record(target.copy(frequencyKhz = 27135.0), -95.0, 2_000)
        val hits = log.record(target, -80.0, 3_000)

        assertEquals(2, hits.size)
        assertEquals(27125.0, hits[0].frequencyKhz, "la voce aggiornata torna in cima")
        assertEquals(2, hits[0].count)
        assertEquals(1_000, hits[0].firstSeenMillis)
        assertEquals(3_000, hits[0].lastSeenMillis)
        assertEquals(-80.0, hits[0].peakRssiDbm)
    }

    @Test
    fun removeAndClear() {
        val log = ScanHitLog()
        log.record(target, -90.0, 1_000)
        assertTrue(log.remove(27125.0).isEmpty())
        log.record(target, -90.0, 1_000)
        assertTrue(log.clear().isEmpty())
    }

    @Test
    fun keepsOnlyMostRecentEntries() {
        val log = ScanHitLog(maxEntries = 3)
        var hits = emptyList<ScanHit>()
        repeat(5) { i -> hits = log.record(target.copy(frequencyKhz = 27000.0 + i), -90.0, i.toLong()) }
        assertEquals(listOf(27004.0, 27003.0, 27002.0), hits.map { it.frequencyKhz })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RangeScanTests {

    private class Harness(scope: TestScope, params: RangeScanParams, autoResume: Boolean = true) {
        val params = MutableStateFlow(params)
        val gate = MutableStateFlow(false)
        val tuned = mutableListOf<Double>()
        val found = mutableListOf<Pair<ScanKind, Double>>()
        val stoppedOn = mutableListOf<Double>()
        val scanner = ScanCoordinator(
            scope = scope.backgroundScope,
            signalGateOpen = gate,
            tuneTo = { tuned += it.frequencyKhz },
            resetSignalDetection = { gate.value = false },
            onSignalFound = { kind, target -> found += kind to target.frequencyKhz },
            onStoppedOnSignal = { _, target -> stoppedOn += target.frequencyKhz }
        )
        val plan = ScanPlan(
            kind = ScanKind.RANGE,
            source = { FrequencyRangeScanSource(this.params.value) },
            dwellMillis = { RANGE_DWELL_MS },
            autoResume = { autoResume }
        )
    }

    private fun TestScope.advanceStep(ms: Long = RANGE_STEP_MS) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private val cb = RangeScanParams(27000.0, 27020.0, 10.0, ModulationMode.AM)

    @Test
    fun walksTheRangeAndRestartsFromTheBeginning() = runTest {
        val h = Harness(this, cb)
        assertTrue(h.scanner.start(h.plan))
        runCurrent()
        assertEquals(ScanKind.RANGE, h.scanner.activeKind)
        val first = h.scanner.state.value
        assertIs<ScanState.Scanning>(first)
        assertEquals(1, first.position)
        assertEquals(3, first.total)
        repeat(3) { advanceStep() }
        assertEquals(listOf(27000.0, 27010.0, 27020.0, 27000.0), h.tuned)
        h.scanner.stop()
    }

    @Test
    fun staysOnSignalRecordsHitAndResumes() = runTest {
        val h = Harness(this, cb)
        h.scanner.start(h.plan)
        runCurrent()
        advanceStep()
        advanceStep(SCAN_SETTLE_MS + 200)
        h.gate.value = true
        runCurrent()
        assertIs<ScanState.SignalFound>(h.scanner.state.value)
        assertEquals(listOf(ScanKind.RANGE to 27010.0), h.found)

        advanceStep(30_000)
        assertEquals(listOf(27000.0, 27010.0), h.tuned)

        h.gate.value = false
        runCurrent()
        assertEquals(listOf(27000.0, 27010.0, 27020.0), h.tuned)
        h.scanner.stop()
    }

    @Test
    fun stopsOnSignalWithoutAutoResume() = runTest {
        val h = Harness(this, cb, autoResume = false)
        h.scanner.start(h.plan)
        runCurrent()
        advanceStep(SCAN_SETTLE_MS + 100)
        h.gate.value = true
        runCurrent()
        assertEquals(listOf(27000.0), h.stoppedOn)
        assertFalse(h.scanner.isActive)
        assertEquals(ScanState.Idle, h.scanner.state.value)
    }

    @Test
    fun signalDuringSettleTimeIsIgnored() = runTest {
        val h = Harness(this, cb)
        h.scanner.start(h.plan)
        runCurrent()
        h.gate.value = true
        advanceStep()
        assertEquals(listOf(27000.0, 27010.0), h.tuned)
        assertTrue(h.found.isEmpty())
        h.scanner.stop()
    }

    @Test
    fun stopPreventsFurtherTuning() = runTest {
        val h = Harness(this, cb)
        h.scanner.start(h.plan)
        runCurrent()
        h.scanner.stop()
        advanceStep(30_000)
        assertEquals(listOf(27000.0), h.tuned)
        assertEquals(null, h.scanner.activeKind)
    }

    @Test
    fun parameterChangesApplyFromNextStep() = runTest {
        val h = Harness(this, cb)
        h.scanner.start(h.plan)
        runCurrent()
        h.params.value = cb.copy(excludedKhz = listOf(27010.0))
        advanceStep()
        assertEquals(listOf(27000.0, 27020.0), h.tuned)
        h.scanner.stop()
    }

    @Test
    fun onlyOneScanAtATime() = runTest {
        val h = Harness(this, cb)
        val channels = ScanPlan(
            kind = ScanKind.CHANNELS,
            source = { ListScanSource(listOf(ScanTarget(7100.0, ModulationMode.LSB, "40 m"))) },
            dwellMillis = { RANGE_DWELL_MS },
            autoResume = { true }
        )
        assertTrue(h.scanner.start(channels))
        assertFalse(h.scanner.start(h.plan), "una seconda scansione non parte finché la prima è attiva")
        h.scanner.stop()
        assertTrue(h.scanner.start(h.plan))
        runCurrent()
        assertEquals(ScanKind.RANGE, h.scanner.activeKind)
        h.scanner.stop()
    }
}

class RangeScanSettingsTests {

    @Test
    fun defaultsAreTheCbBand() = withSettingsDir { dir ->
        val s = AppSettings(dir).settings.value
        assertEquals(RangeScanParams(26965.0, 27405.0, 10.0, ModulationMode.AM), s.rangeScanParams)
        assertEquals(RangeScanLimits.DEFAULT_DWELL_MS, s.rangeScanDwellMs)
        assertTrue(s.rangeScanAutoResume)
    }

    @Test
    fun oldSettingsFileGetsDefaults() = withSettingsDir { dir ->
        File(dir, "settings.json").writeText("""{ "scanDwellSeconds": 5, "setupCompleted": true }""")
        val s = AppSettings(dir).settings.value
        assertEquals(5, s.scanDwellSeconds)
        assertEquals(26965.0, s.rangeScanStartKhz)
        assertTrue(s.rangeScanExcludedKhz.isEmpty())
    }

    @Test
    fun validParametersArePersistedAndInvalidRejected() = withSettingsDir { dir ->
        val settings = AppSettings(dir)
        assertNull(settings.setRangeScanParameters(7000.0, 7200.0, 5.0, ModulationMode.LSB))
        assertNotNull(settings.setRangeScanParameters(7200.0, 7000.0, 5.0, ModulationMode.LSB))
        settings.setRangeScanDwellMs(50)
        settings.setRangeScanAutoResume(false)
        settings.addRangeScanExclusion(7100.0)
        settings.addRangeScanExclusion(7100.0)
        settings.addRangeScanExclusion(7050.0)

        val reloaded = AppSettings(dir).settings.value
        assertEquals(RangeScanParams(7000.0, 7200.0, 5.0, ModulationMode.LSB, listOf(7050.0, 7100.0)), reloaded.rangeScanParams)
        assertEquals(RangeScanLimits.MIN_DWELL_MS, reloaded.rangeScanDwellMs)
        assertFalse(reloaded.rangeScanAutoResume)

        settings.removeRangeScanExclusion(7050.0)
        assertEquals(listOf(7100.0), AppSettings(dir).settings.value.rangeScanExcludedKhz)
        settings.clearRangeScanExclusions()
        assertTrue(AppSettings(dir).settings.value.rangeScanExcludedKhz.isEmpty())
    }
}
