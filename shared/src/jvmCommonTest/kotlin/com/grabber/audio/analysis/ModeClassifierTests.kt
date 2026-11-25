package com.grabber.audio.analysis

import com.grabber.decoder.fsk.FskSignalGenerator
import com.grabber.model.ModulationMode
import com.grabber.model.digital.DigitalMode
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModeClassifierTests {
    private val rate = 12000

    private var lastDebug = ""

    private fun classify(signal: FloatArray, context: ModeClassifier.Context): ModeSuggestion? {
        val classifier = ModeClassifier()
        classifier.setContext(context)
        var i = 0
        while (i < signal.size) {
            val n = minOf(1024, signal.size - i)
            classifier.process(signal.copyOfRange(i, i + n), n, rate)
            i += n
        }
        lastDebug = classifier.debugInfo
        return classifier.suggestion
    }

    private fun noise(seconds: Double, rms: Double, seed: Int = 3): FloatArray {
        val random = Random(seed)
        return FloatArray((seconds * rate).toInt()) { ((random.nextDouble() - 0.5) * 2 * rms * 1.7).toFloat() }
    }

    private fun mix(a: FloatArray, b: FloatArray) = FloatArray(minOf(a.size, b.size)) { a[it] + b[it] }

    private fun randomBits(count: Int, seed: Int = 9) = Random(seed).let { r -> List(count) { r.nextInt(2) } }

    @Test
    fun navtexIsRecognizedFromTonesAndFrequency() {
        val fsk = FskSignalGenerator(rate).modulate(randomBits(1200), 100.0, 500.0, 170.0, leadInSeconds = 0.0)
        val result = classify(mix(fsk, noise(12.0, 0.05)), ModeClassifier.Context(ModulationMode.CW, 518.0, 365, 635))
        assertEquals(DigitalMode.NAVTEX, result?.mode, "risultato $result picchi $lastDebug")
        assertTrue(result!!.confidence >= 0.9)
    }

    @Test
    fun weatherRttyShiftIsEstimated() {
        val fsk = FskSignalGenerator(rate).modulate(randomBits(600), 50.0, 1000.0, 450.0, leadInSeconds = 0.0)
        val result = classify(mix(fsk, noise(12.0, 0.05)), ModeClassifier.Context(ModulationMode.USB, 10099.8, 300, 2700))
        assertEquals(DigitalMode.RTTY, result?.mode, "risultato $result picchi $lastDebug")
        assertEquals(450, result?.fskShiftHz)
    }

    @Test
    fun keyedToneIsCw() {
        // "CQ" ripetuto: punti e linee a ~20 WPM (punto 60 ms).
        val pattern = "1110101110100011101011101000000"
        val unit = (0.06 * rate).toInt()
        val signal = FloatArray(12 * rate)
        var t = 0
        while (t < signal.size) {
            for (c in pattern) {
                for (k in 0 until unit) {
                    if (t >= signal.size) break
                    signal[t] = if (c == '1') (0.3 * sin(2 * PI * 600 * t / rate)).toFloat() else 0f
                    t++
                }
            }
        }
        val result = classify(mix(signal, noise(12.0, 0.02)), ModeClassifier.Context(ModulationMode.USB, 7025.0, 300, 2700))
        assertEquals(DigitalMode.CW, result?.mode, "risultato $result picchi $lastDebug")
    }

    @Test
    fun faxBandIsRecognized() {
        // Segnale FM tra 1500 (nero) e 2300 Hz (bianco) che varia lungo la riga.
        val random = Random(5)
        var phase = 0.0
        val signal = FloatArray(12 * rate) { i ->
            val level = (sin(2 * PI * i / 500.0) + random.nextDouble() * 0.5).coerceIn(-1.0, 1.0)
            val freq = 1900 + 400 * level
            phase += 2 * PI * freq / rate
            (0.3 * sin(phase)).toFloat()
        }
        val result = classify(mix(signal, noise(12.0, 0.02)), ModeClassifier.Context(ModulationMode.USB, 7878.1, 1300, 2500))
        assertEquals(DigitalMode.FAX, result?.mode, "risultato $result picchi $lastDebug")
    }

    @Test
    fun noiseAndVoiceLikeSignalsGiveNoSuggestion() {
        assertNull(classify(noise(12.0, 0.3), ModeClassifier.Context(ModulationMode.USB, 14200.0, 300, 2700)))
        // "Voce": somma di armoniche con fondamentale variabile e ampiezza sillabica.
        var phase = 0.0
        val voice = FloatArray(12 * rate) { i ->
            val f0 = 140 + 40 * sin(2 * PI * i / (rate * 0.7))
            phase += 2 * PI * f0 / rate
            val syllable = 0.5 + 0.5 * sin(2 * PI * i / (rate * 0.25))
            var v = 0.0
            for (h in 1..15) v += sin(h * phase) / h
            (0.1 * syllable * v).toFloat()
        }
        assertNull(classify(mix(voice, noise(12.0, 0.02)), ModeClassifier.Context(ModulationMode.USB, 14200.0, 300, 2700)))
    }

    @Test
    fun frequencyContextKnowsTheMainAllocations() {
        assertEquals(DigitalMode.NAVTEX, ModeClassifier.frequencyContext(518.0))
        assertEquals(DigitalMode.FT8, ModeClassifier.frequencyContext(14075.2))
        assertEquals(DigitalMode.FAX, ModeClassifier.frequencyContext(7878.1))
        assertNull(ModeClassifier.frequencyContext(9500.0))
    }
}
