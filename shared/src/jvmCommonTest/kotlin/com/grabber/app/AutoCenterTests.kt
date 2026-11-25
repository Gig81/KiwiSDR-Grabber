package com.grabber.app

import com.grabber.audio.analysis.AudioSpectrumAnalyzer
import com.grabber.audio.analysis.SignalCenterEstimator
import com.grabber.model.AudioFrame
import com.grabber.model.ModulationMode
import com.grabber.model.Passband
import com.grabber.model.digital.DigitalMode
import com.grabber.waterfall.WaterfallFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoCenterTests {
    private val rate = 12000

    private fun tones(vararg components: Pair<Double, Double>, seconds: Double = 1.0, noise: Double = 0.02): FloatArray {
        val random = Random(7)
        return FloatArray((rate * seconds).toInt()) { i ->
            var v = 0.0
            for ((hz, amp) in components) v += amp * sin(2 * PI * hz * i / rate)
            (v + (random.nextDouble() - 0.5) * 2 * noise).toFloat()
        }
    }

    private fun spectrumOf(samples: FloatArray): AudioSpectrumAnalyzer =
        AudioSpectrumAnalyzer(4096).also { it.push(samples, samples.size, rate) }

    @Test
    fun peakNearestToTheReferenceIsChosenNotTheStrongest() {
        val analyzer = spectrumOf(tones(560.0 to 0.1, 900.0 to 0.5))
        val peak = assertNotNull(SignalCenterEstimator.nearestPeak(analyzer.power(), 0.0, analyzer.binHz, 500.0, 500.0))
        assertTrue(abs(peak.frequencyHz - 560.0) < 2.0, "picco a ${peak.frequencyHz}")
    }

    @Test
    fun noiseAloneHasNoPeak() {
        val analyzer = spectrumOf(tones(noise = 0.3))
        assertNull(SignalCenterEstimator.nearestPeak(analyzer.power(), 0.0, analyzer.binHz, 500.0, 200.0, minSnrDb = 15.0))
    }

    @Test
    fun fskPairCenterIsFound() {
        val analyzer = spectrumOf(tones(455.0 to 0.2, 625.0 to 0.2, seconds = 2.0))
        val center = assertNotNull(SignalCenterEstimator.fskCenter(analyzer.power(), analyzer.binHz, 500.0, 170.0, 85.0))
        assertTrue(abs(center - 540.0) < 5.0, "centro $center")
    }

    private class Harness(var ctx: AfcContext, var frame: WaterfallFrame? = null) {
        val corrections = mutableListOf<Double>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val afc = AutoCenterCoordinator(
            scope = scope,
            isEnabled = { true },
            context = { ctx },
            latestWaterfall = { frame },
            applyCorrection = { corrections += it; ctx = ctx.copy(frequencyKhz = it) },
            settleMs = 10,
            measureMs = 150
        )
    }

    private fun feed(afc: AutoCenterCoordinator, samples: FloatArray) {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i] * 32767).toInt().toShort().toInt()
            bytes[2 * i] = v.toByte()
            bytes[2 * i + 1] = (v shr 8).toByte()
        }
        var seq = 0L
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(bytes.size, offset + 1024)
            afc.onAudio(AudioFrame(seq++, -80.0, bytes.copyOfRange(offset, end), rate))
            offset = end
        }
    }

    private fun cw(frequencyKhz: Double) = AfcContext(ModulationMode.CW, frequencyKhz, Passband(300, 700), null, 1000, 170)

    @Test
    fun cwToneIsCenteredFromAudio() = runBlocking {
        val h = Harness(cw(7030.0))
        try {
            h.afc.onTuned(TuneSource.WATERFALL_CLICK)
            withTimeout(5_000) { while (!h.afc.isCapturing) delay(5) }
            feed(h.afc, tones(620.0 to 0.3, seconds = 1.2))
            withTimeout(5_000) { while (h.afc.lastResult.value == null) delay(5) }
            assertEquals(AutoCenterResult.Outcome.APPLIED, h.afc.lastResult.value?.outcome)
            // Tono a 620 Hz invece di 500: la frequenza sale di 120 Hz.
            assertTrue(abs(h.corrections.single() - 7030.120) < 0.003, "correzione ${h.corrections}")
        } finally {
            h.scope.cancel()
        }
    }

    @Test
    fun voiceModesAndWholeBandDecodersAreNotCorrected() = runBlocking {
        val h = Harness(AfcContext(ModulationMode.USB, 14200.0, Passband(300, 2700), null, 1000, 170))
        try {
            h.afc.onTuned(TuneSource.USER_TYPED)
            withTimeout(5_000) { while (h.afc.lastResult.value == null) delay(5) }
            assertEquals(AutoCenterResult.Outcome.NOT_APPLICABLE, h.afc.lastResult.value?.outcome)
            h.ctx = h.ctx.copy(activeDecoder = DigitalMode.FT8)
            assertNull(h.afc.planFor(h.ctx))
            assertTrue(h.corrections.isEmpty())
        } finally {
            h.scope.cancel()
        }
    }

    @Test
    fun onlyUserTuningsTriggerTheAfc() = runBlocking {
        val h = Harness(cw(7030.0))
        try {
            for (source in listOf(TuneSource.PRESET, TuneSource.STEP, TuneSource.SCAN, TuneSource.AFC, TuneSource.OTHER)) {
                h.afc.onTuned(source)
            }
            delay(300)
            assertNull(h.afc.lastResult.value)
            assertTrue(!h.afc.isCapturing)
        } finally {
            h.scope.cancel()
        }
    }

    @Test
    fun amCarrierIsCenteredFromTheWaterfall() = runBlocking {
        // Zoom 12: ~7 Hz per bin; portante 1,2 kHz sopra la frequenza digitata.
        val zoom = 12
        val span = 30000.0 / (1 shl zoom)
        val start = 6000.0 - span / 2
        val bins = ByteArray(1024) { (255 - 120).toByte() }
        val carrierBin = ((6001.2 - start) / span * 1023).toInt()
        bins[carrierBin] = (255 - 60).toByte()
        val frame = WaterfallFrame(1, bins, zoom, 6000.0, start, span, start + span)
        val h = Harness(AfcContext(ModulationMode.AM, 6000.0, Passband(-4000, 4000), null, 1000, 170), frame)
        try {
            h.afc.onTuned(TuneSource.USER_TYPED)
            withTimeout(5_000) { while (h.afc.lastResult.value == null) delay(5) }
            assertEquals(AutoCenterResult.Outcome.APPLIED, h.afc.lastResult.value?.outcome)
            assertTrue(abs(h.corrections.single() - 6001.2) < 0.015, "correzione ${h.corrections}")
            assertTrue(h.afc.lastResult.value?.fromWaterfall == true)
        } finally {
            h.scope.cancel()
        }
    }

    @Test
    fun newTuningCancelsAMeasurementInProgress() = runBlocking {
        val h = Harness(cw(7030.0))
        try {
            h.afc.onTuned(TuneSource.USER_TYPED)
            withTimeout(5_000) { while (!h.afc.isCapturing) delay(5) }
            h.afc.onTuned(TuneSource.STEP)
            assertTrue(!h.afc.isCapturing)
            delay(300)
            assertNull(h.afc.lastResult.value)
        } finally {
            h.scope.cancel()
        }
    }
}
