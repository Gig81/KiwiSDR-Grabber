package com.grabber.audio.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val RATE = 12000

/** PCM 16 bit LE mono di una sinusoide (ampiezza in scala ±1). */
internal fun sinePcm(frequencyHz: Double, amplitude: Double, samples: Int, sampleRate: Int = RATE): ByteArray {
    val out = ByteArray(samples * 2)
    for (i in 0 until samples) {
        val v = (amplitude * 32767 * sin(2 * PI * frequencyHz * i / sampleRate)).toInt()
        out[2 * i] = (v and 0xFF).toByte()
        out[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
    }
    return out
}

internal fun pcmToSamples(pcm: ByteArray): IntArray = IntArray(pcm.size / 2) { i ->
    ((pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 0xFF))
}

/** RMS dei campioni da [from] in poi (per ignorare il transitorio iniziale dei filtri). */
internal fun rms(pcm: ByteArray, from: Int = 0): Double {
    val s = pcmToSamples(pcm)
    var sum = 0.0
    for (i in from until s.size) sum += s[i].toDouble() * s[i]
    return sqrt(sum / (s.size - from))
}

private fun db(ratio: Double) = 20 * log10(ratio)

class BiquadDesignTests {

    @Test
    fun peakingHasGainAtCenterAndUnityFarAway() {
        val c = BiquadDesign.peaking(RATE, 1000.0, 1.2, 6.0)
        assertEquals(6.0, BiquadDesign.magnitudeDb(c, 1000.0, RATE), 0.1)
        assertEquals(0.0, BiquadDesign.magnitudeDb(c, 30.0, RATE), 0.3)
    }

    @Test
    fun highAndLowPassAreMinus3DbAtCutoff() {
        assertEquals(-3.01, BiquadDesign.magnitudeDb(BiquadDesign.highPass(RATE, 300.0), 300.0, RATE), 0.1)
        assertEquals(-3.01, BiquadDesign.magnitudeDb(BiquadDesign.lowPass(RATE, 2800.0), 2800.0, RATE), 0.1)
        assertTrue(BiquadDesign.magnitudeDb(BiquadDesign.highPass(RATE, 300.0), 50.0, RATE) < -25)
        assertTrue(BiquadDesign.magnitudeDb(BiquadDesign.lowPass(RATE, 1000.0), 5000.0, RATE) < -20)
    }

    @Test
    fun notchAttenuatesCenterByAtLeast30Db() {
        val c = BiquadDesign.notch(RATE, 1000.0, 10.0)
        assertTrue(BiquadDesign.magnitudeDb(c, 1000.0, RATE) < -30)
        assertEquals(0.0, BiquadDesign.magnitudeDb(c, 2000.0, RATE), 0.5)
    }

    @Test
    fun shelvesBoostTheirSide() {
        assertEquals(6.0, BiquadDesign.magnitudeDb(BiquadDesign.lowShelf(RATE, 100.0, 6.0), 20.0, RATE), 0.3)
        assertEquals(6.0, BiquadDesign.magnitudeDb(BiquadDesign.highShelf(RATE, 4000.0, 6.0), 5900.0, RATE), 0.6)
    }
}

class AudioDspChainTests {

    private fun chain(config: AudioDspConfig) = AudioDspChain().apply { configure(config) }

    private fun eq(band: Int, gain: Double) = AudioDspConfig(
        targetName = DspTarget.LISTEN.name,
        eqGainsDb = List(DspLimits.EQ_BANDS_HZ.size) { if (it == band) gain else 0.0 }
    )

    @Test
    fun neutralConfigurationReturnsTheSameBuffer() {
        val pcm = sinePcm(1000.0, 0.5, 1024)
        assertSame(pcm, chain(AudioDspConfig(targetName = DspTarget.LISTEN.name)).process(pcm, RATE))
    }

    @Test
    fun equalizerBandDoublesAmplitudeAt6Db() {
        val band = DspLimits.EQ_BANDS_HZ.indexOf(1000)
        val input = sinePcm(1000.0, 0.25, RATE)
        val output = chain(eq(band, 6.0)).process(input, RATE)
        val ratio = rms(output, from = 2000) / rms(input, from = 2000)
        assertEquals(1.995, ratio, 0.1)
    }

    @Test
    fun stateIsKeptAcrossBlocks() {
        val config = eq(DspLimits.EQ_BANDS_HZ.indexOf(500), 9.0).copy(highPassHz = 200, lowPassHz = 3000, noiseReductionPercent = 40)
        val input = sinePcm(700.0, 0.3, 6000)
        val whole = chain(config).process(input, RATE)
        val split = chain(config)
        val pieces = input.toList().chunked(512).map { split.process(it.toByteArray(), RATE) }
        assertContentEquals(whole, pieces.reduce { acc, bytes -> acc + bytes })
    }

    @Test
    fun notchRemovesTone() {
        val input = sinePcm(1000.0, 0.5, RATE)
        val output = chain(AudioDspConfig(targetName = DspTarget.LISTEN.name, notchHz = 1000, notchQ = 10.0)).process(input, RATE)
        assertTrue(db(rms(output, from = 6000) / rms(input, from = 6000)) < -30)
    }

    @Test
    fun limiterPreventsClippingAndWrapAround() {
        val input = sinePcm(1000.0, 0.99, RATE)
        val config = AudioDspConfig(targetName = DspTarget.LISTEN.name, eqGainsDb = List(DspLimits.EQ_BANDS_HZ.size) { 12.0 })
        val samples = pcmToSamples(chain(config).process(input, RATE))
        assertTrue(samples.all { it in -32767..32767 })
        // Un wrap-around (overflow a 16 bit) invertirebbe il segno sui picchi, aggiungendo attraversamenti dello zero.
        fun zeroCrossings(s: IntArray) = (1 until s.size).count { (s[it] >= 0) != (s[it - 1] >= 0) }
        val expected = zeroCrossings(pcmToSamples(input))
        assertTrue(abs(zeroCrossings(samples) - expected) <= 4, "attraversamenti ${zeroCrossings(samples)} invece di $expected")
        assertTrue(samples.maxOrNull()!! > 25000, "il limitatore non deve schiacciare il segnale")
    }

    @Test
    fun silenceStaysSilent() {
        val config = eq(3, 12.0).copy(highPassHz = 100, lowPassHz = 3000, notchHz = 800, noiseReductionPercent = 100)
        val out = pcmToSamples(chain(config).process(ByteArray(4000), RATE))
        assertTrue(out.all { it == 0 })
    }

    @Test
    fun sampleRateChangeRecomputesCoefficients() {
        val dsp = chain(AudioDspConfig(targetName = DspTarget.LISTEN.name, notchHz = 1000, notchQ = 10.0))
        dsp.process(sinePcm(1000.0, 0.5, 2000), RATE)
        val wideRate = 20250
        val input = sinePcm(1000.0, 0.5, wideRate, wideRate)
        val output = dsp.process(input, wideRate)
        assertTrue(db(rms(output, from = 10000) / rms(input, from = 10000)) < -30)
    }

    @Test
    fun bandsAboveNyquistAreIgnored() {
        val top = DspLimits.EQ_BANDS_HZ.lastIndex // 4000 Hz, oltre 0.45 × 8000 Hz
        val input = sinePcm(1000.0, 0.3, 4000, 8000)
        val output = chain(eq(top, 12.0)).process(input, 8000)
        assertEquals(rms(input), rms(output), rms(input) * 0.01)
    }

    @Test
    fun concurrentConfigureDoesNotBreakProcessing() {
        val dsp = chain(eq(2, 6.0))
        val worker = Thread {
            repeat(2000) { i -> dsp.configure(eq(i % 7, (i % 25 - 12).toDouble()).copy(highPassHz = 100 + i % 300)) }
        }
        worker.start()
        val input = sinePcm(440.0, 0.4, 512)
        repeat(2000) { dsp.process(input, RATE) }
        worker.join()
    }

    @Test
    fun fullChainIsMuchFasterThanRealTime() {
        val config = AudioDspConfig(
            targetName = DspTarget.LISTEN.name, highPassHz = 300, lowPassHz = 2800, notchHz = 1500,
            noiseReductionPercent = 60, eqGainsDb = listOf(3.0, -2.0, 1.0, 2.0, 4.0, -3.0, 2.0)
        )
        val dsp = chain(config)
        val block = sinePcm(700.0, 0.4, 1024) // ~85 ms a 12 kHz
        repeat(50) { dsp.process(block, RATE) } // riscaldamento JIT
        val blocks = 60 * RATE / 1024
        val start = System.nanoTime()
        repeat(blocks) { dsp.process(block, RATE) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        // 60 s di audio: margine ampio (> 12× il tempo reale) per non dipendere dalla macchina di CI.
        assertTrue(elapsedMs < 5_000, "60 s di audio elaborati in $elapsedMs ms")
        println("AudioDspChain: 60 s di audio elaborati in $elapsedMs ms")
    }

    @Test
    fun normalizationClampsValues() {
        val n = AudioDspConfig(
            targetName = "BOH", highPassHz = 5, lowPassHz = 99999, notchHz = -3, notchQ = Double.NaN,
            noiseReductionPercent = 300, eqGainsDb = listOf(40.0, Double.NaN), presetName = "BOH"
        ).normalized()
        assertEquals(DspTarget.OFF, n.target)
        assertEquals(DspLimits.HIGH_PASS_HZ.first, n.highPassHz)
        assertEquals(DspLimits.LOW_PASS_HZ.last, n.lowPassHz)
        assertEquals(0, n.notchHz)
        assertEquals(DspLimits.NOTCH_Q_DEFAULT, n.notchQ)
        assertEquals(100, n.noiseReductionPercent)
        assertEquals(listOf(12.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0), n.eqGainsDb)
        assertEquals(DspPreset.CUSTOM.name, n.presetName)
    }

    @Test
    fun presetKeepsTarget() {
        val config = AudioDspConfig(targetName = DspTarget.LISTEN_AND_RECORD.name).withPreset(DspPreset.SSB_VOICE)
        assertEquals(DspTarget.LISTEN_AND_RECORD, config.target)
        assertEquals(300, config.highPassHz)
        assertEquals(DspPreset.SSB_VOICE.name, config.presetName)
        assertTrue(AudioDspConfig().withPreset(DspPreset.NEUTRAL).isNeutral)
    }
}

class LmsNoiseReducerTests {

    @Test
    fun improvesSignalToNoiseRatioOfToneInWhiteNoise() {
        val random = Random(42)
        val n = RATE * 4
        val clean = DoubleArray(n) { 0.3 * sin(2 * PI * 800.0 * it / RATE) }
        val noisy = DoubleArray(n) { clean[it] + (random.nextDouble() - 0.5) * 0.4 }
        val reducer = LmsNoiseReducer().apply { level = 1.0 }
        val out = DoubleArray(n) { reducer.process(noisy[it]) }

        fun noisePower(signal: DoubleArray): Double {
            var sum = 0.0
            for (i in n / 2 until n) sum += (signal[i] - clean[i]).let { it * it }
            return sum / (n / 2)
        }
        val improvementDb = 10 * log10(noisePower(noisy) / noisePower(out))
        assertTrue(improvementDb > 3.0, "miglioramento SNR $improvementDb dB")
    }

    @Test
    fun zeroLevelIsBypass() {
        val reducer = LmsNoiseReducer()
        val random = Random(1)
        repeat(1000) {
            val x = random.nextDouble() - 0.5
            assertEquals(x, reducer.process(x), 0.0)
        }
    }
}
