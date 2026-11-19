package com.grabber.audio

import com.grabber.app.RangeScanParams
import com.grabber.app.RangeScanSession
import com.grabber.app.ScanTarget
import com.grabber.audio.dsp.AudioDspConfig
import com.grabber.audio.dsp.DspPreset
import com.grabber.audio.dsp.DspTarget
import com.grabber.audio.squelch.SquelchController
import com.grabber.model.ModulationMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AudioDspControlTests {

    private class Harness(scope: TestScope, licensed: Boolean) {
        var licensed = licensed
        val saved = mutableListOf<AudioDspConfig>()
        val pipeline = AudioPipeline(SquelchController(vad = null), playbackSink = {}, recordSink = {})
        val control = AudioDspControl(
            scope = scope.backgroundScope,
            pipeline = pipeline,
            initialConfig = AudioDspConfig(targetName = DspTarget.LISTEN.name, highPassHz = 300),
            isLicensed = { this.licensed },
            persist = { saved += it },
            saveDebounceMillis = 300
        )
    }

    @Test
    fun withoutLicenseCommandsAreRefusedAndDspIsOff() = runTest {
        val h = Harness(this, licensed = false)
        assertFalse(h.control.setTarget(DspTarget.LISTEN_AND_RECORD))
        assertFalse(h.control.applyPreset(DspPreset.SSB_VOICE))
        assertEquals(DspTarget.LISTEN, h.control.config.value.target)
        assertEquals(DspTarget.OFF, h.pipeline.effectiveTarget)
        runCurrent()
        assertFalse(h.control.isActive.value)
        advanceTimeBy(1_000)
        assertTrue(h.saved.isEmpty())
    }

    @Test
    fun changesApplyImmediatelyAndAreSavedOnceAfterDebounce() = runTest {
        val h = Harness(this, licensed = true)
        assertEquals(DspTarget.LISTEN, h.pipeline.effectiveTarget)
        repeat(10) { h.control.setEqGain(3, it.toDouble()) }
        assertEquals(9.0, h.control.config.value.eqGainsDb[3])
        assertEquals(DspPreset.CUSTOM.name, h.control.config.value.presetName)
        advanceTimeBy(200)
        assertTrue(h.saved.isEmpty(), "nessun salvataggio prima del debounce")
        advanceTimeBy(200)
        runCurrent()
        assertEquals(1, h.saved.size)
        assertEquals(9.0, h.saved.single().eqGainsDb[3])
    }

    @Test
    fun flushSavesPendingChangeImmediately() = runTest {
        val h = Harness(this, licensed = true)
        h.control.setTarget(DspTarget.LISTEN_AND_RECORD)
        h.control.flush()
        assertEquals(DspTarget.LISTEN_AND_RECORD, h.saved.single().target)
        advanceTimeBy(1_000)
        assertEquals(1, h.saved.size, "il salvataggio differito non deve ripetersi")
    }

    @Test
    fun licenseChangeTurnsDspOnAndOffWithoutRestart() = runTest {
        val h = Harness(this, licensed = false)
        assertEquals(DspTarget.OFF, h.pipeline.effectiveTarget)
        h.licensed = true
        h.control.refreshLicense()
        runCurrent()
        assertEquals(DspTarget.LISTEN, h.pipeline.effectiveTarget)
        assertTrue(h.control.isActive.value)
        h.licensed = false
        h.control.refreshLicense()
        runCurrent()
        assertEquals(DspTarget.OFF, h.pipeline.effectiveTarget)
        assertFalse(h.control.isActive.value)
    }

    @Test
    fun bypassIsTemporaryAndNotSaved() = runTest {
        val h = Harness(this, licensed = true)
        h.control.setBypass(true)
        assertEquals(DspTarget.OFF, h.pipeline.effectiveTarget)
        assertEquals(DspTarget.LISTEN, h.control.config.value.target)
        h.control.setBypass(false)
        assertEquals(DspTarget.LISTEN, h.pipeline.effectiveTarget)
        advanceTimeBy(1_000)
        assertTrue(h.saved.isEmpty())
    }
}

class RangeScanSessionTests {

    @Test
    fun sourceIsReusedUntilParametersChange() {
        var params = RangeScanParams(27000.0, 27020.0, 10.0, ModulationMode.AM)
        val session = RangeScanSession(params = { params }, dwellMillis = { 1000 }, autoResume = { true })
        val first = session.plan.source()
        assertTrue(first === session.plan.source())
        params = params.copy(excludedKhz = listOf(27010.0))
        val second = session.plan.source()
        assertFalse(first === second)
        assertEquals(2, second.size)
    }

    @Test
    fun hitsAreRecordedRemovedAndCleared() {
        val session = RangeScanSession(
            params = { RangeScanParams(27000.0, 27020.0, 10.0, ModulationMode.AM) },
            dwellMillis = { 1000 },
            autoResume = { true }
        )
        val target = ScanTarget(27010.0, ModulationMode.AM, "27010 kHz")
        session.recordHit(target, -90.0, 1_000)
        session.recordHit(target, -85.0, 2_000)
        assertEquals(2, session.hits.value.single().count)
        session.removeHit(27010.0)
        assertTrue(session.hits.value.isEmpty())
        session.recordHit(target, -90.0, 3_000)
        session.clearHits()
        assertTrue(session.hits.value.isEmpty())
        assertEquals(null, session.validationError())
    }
}
