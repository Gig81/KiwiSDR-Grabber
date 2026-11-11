package com.grabber.audio.vad

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VadTests {

    private val sampleRate = 12000

    private fun generateSinePcm(freqHz: Double, durationMs: Int, amplitude: Double = 0.5): ByteArray {
        val sampleCount = (sampleRate * durationMs) / 1000
        val bytes = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val t = i.toDouble() / sampleRate
            val sampleVal = (sin(2.0 * PI * freqHz * t) * amplitude * 32767.0).toInt().coerceIn(-32768, 32767)
            bytes[i * 2] = (sampleVal and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sampleVal shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun generateSyntheticVoicePcm(pitchHz: Double, durationMs: Int, amplitude: Double = 0.4): ByteArray {
        val sampleCount = (sampleRate * durationMs) / 1000
        val bytes = ByteArray(sampleCount * 2)
        // Voce umana: fondamentale + armoniche delle formanti (300Hz - 2500Hz)
        for (i in 0 until sampleCount) {
            val t = i.toDouble() / sampleRate
            var sum = 0.0
            sum += 0.50 * sin(2.0 * PI * pitchHz * t)          // Fondamentale (es. 150 Hz)
            sum += 0.35 * sin(2.0 * PI * (pitchHz * 2) * t)    // 1a armonica (300 Hz)
            sum += 0.40 * sin(2.0 * PI * (pitchHz * 4) * t)    // 3a armonica (600 Hz - formante F1)
            sum += 0.30 * sin(2.0 * PI * (pitchHz * 8) * t)    // 7a armonica (1200 Hz - formante F2)
            sum += 0.20 * sin(2.0 * PI * (pitchHz * 16) * t)   // 15a armonica (2400 Hz - formante F3)
            val sampleVal = (sum * amplitude * 32767.0).toInt().coerceIn(-32768, 32767)
            bytes[i * 2] = (sampleVal and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sampleVal shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun generateWhiteNoisePcm(durationMs: Int, amplitude: Double = 0.2): ByteArray {
        val sampleCount = (sampleRate * durationMs) / 1000
        val bytes = ByteArray(sampleCount * 2)
        val rnd = Random(42)
        for (i in 0 until sampleCount) {
            val n = (rnd.nextDouble() * 2.0 - 1.0) * amplitude * 32767.0
            val sampleVal = n.toInt().coerceIn(-32768, 32767)
            bytes[i * 2] = (sampleVal and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sampleVal shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun generateQrnSpikePcm(durationMs: Int): ByteArray {
        // Blocco di rumore leggero con una scarica/spike improvviso di fulmine (Crest Factor estremo)
        val sampleCount = (sampleRate * durationMs) / 1000
        val bytes = ByteArray(sampleCount * 2)
        val rnd = Random(123)
        for (i in 0 until sampleCount) {
            var sampleVal = ((rnd.nextDouble() * 2.0 - 1.0) * 0.01 * 32767.0).toInt()
            // Spike singolo ad altissima energia
            if (i == sampleCount / 2) {
                sampleVal = 30000 // Scarica a fondo scala
            } else if (i == sampleCount / 2 + 1) {
                sampleVal = -22000
            }
            sampleVal = sampleVal.coerceIn(-32768, 32767)
            bytes[i * 2] = (sampleVal and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sampleVal shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    @Test
    fun testSilenceDetection() {
        val vad = HfSpectralVad(sampleRate = sampleRate)
        val silencePcm = ByteArray(sampleRate * 2 / 10) // 100 ms di silenzio
        val result = vad.process(silencePcm)

        assertFalse(result.isVoice, "Il silenzio non deve essere rilevato come voce")
        assertTrue(result.confidence < 0.1f, "La confidenza su silenzio deve essere prossima a zero")
        assertTrue(result.rmsDb < -60f, "Il livello RMS su silenzio deve essere < -60 dBFS")
    }

    @Test
    fun testSyntheticVoiceDetection() {
        val vad = HfSpectralVad(sampleRate = sampleRate, hangoverMs = 0)
        // Voce a 160 Hz (tipico timbro vocale)
        val voicePcm = generateSyntheticVoicePcm(pitchHz = 160.0, durationMs = 150)
        val result = vad.process(voicePcm)

        assertTrue(result.isVoice, "La voce sintetica deve essere rilevata come voce")
        assertTrue(result.confidence > 0.40f, "Confidenza vocale attesa elevata, ottenuta: ${result.confidence}")
        assertTrue(result.voiceBandRatio > 0.70f, "Rapporto banda vocale atteso > 70%, ottenuto: ${result.voiceBandRatio}")
        assertTrue(result.pitchHarmonicity > 0.30f, "Armonicità vocale attesa > 0.30, ottenuta: ${result.pitchHarmonicity}")
        assertFalse(result.isImpulseNoise, "La voce non deve essere scambiata per QRN")
    }

    @Test
    fun testWhiteNoiseDiscrimination() {
        val vad = HfSpectralVad(sampleRate = sampleRate, hangoverMs = 0)
        val noisePcm = generateWhiteNoisePcm(durationMs = 150, amplitude = 0.25)
        val result = vad.process(noisePcm)

        assertFalse(result.isVoice, "Il rumore bianco costante non deve essere rilevato come voce")
        assertTrue(result.confidence < 0.40f, "Confidenza su rumore bianco deve essere bassa, ottenuta: ${result.confidence}")
        assertTrue(result.zeroCrossingRate > 0.35f, "ZCR di rumore bianco deve essere elevato, ottenuto: ${result.zeroCrossingRate}")
    }

    @Test
    fun testQrnSpikeRejection() {
        val vad = HfSpectralVad(sampleRate = sampleRate, hangoverMs = 0)
        val qrnPcm = generateQrnSpikePcm(durationMs = 100)
        val result = vad.process(qrnPcm)

        assertTrue(result.isImpulseNoise, "La scarica atmosferica deve essere rilevata come disturbo impulsivo (QRN)")
        assertTrue(result.crestFactor > 5.5f, "Crest Factor atteso > 5.5 per QRN, ottenuto: ${result.crestFactor}")
        assertFalse(result.isVoice, "La scarica QRN deve essere scartata dal voice-gate")
    }

    @Test
    fun testLowFrequencyMainsHumRejection() {
        val vad = HfSpectralVad(sampleRate = sampleRate, hangoverMs = 0)
        // Ronzio di rete 50 Hz (fuori dalla banda vocale 300 - 3000 Hz)
        val humPcm = generateSinePcm(freqHz = 50.0, durationMs = 150, amplitude = 0.3)
        val result = vad.process(humPcm)

        assertFalse(result.isVoice, "Il ronzio di rete a 50 Hz non deve aprire il voice-gate")
        assertTrue(result.voiceBandRatio < 0.30f, "Il rapporto banda vocale per 50 Hz deve essere < 0.30, ottenuto: ${result.voiceBandRatio}")
    }

    @Test
    fun testHangoverMaintainsVoiceState() {
        val vad = HfSpectralVad(sampleRate = sampleRate, hangoverMs = 200)

        // 1. Inviamo un blocco di voce per innescare il VAD
        val voicePcm = generateSyntheticVoicePcm(pitchHz = 180.0, durationMs = 100)
        val resVoice = vad.process(voicePcm)
        assertTrue(resVoice.isVoice, "Gate deve essere aperto sulla voce")

        // 2. Inviamo un blocco di basso rumore (breve pausa sillabica di 50 ms)
        val noisePause = generateWhiteNoisePcm(durationMs = 50, amplitude = 0.01)
        val resPause = vad.process(noisePause)
        assertTrue(resPause.isVoice, "Il gate deve rimanere aperto grazie all'hangover durante pause sillabiche")

        // 3. Inviamo ulteriori blocchi per oltre 250 ms di silenzio
        val longSilence = ByteArray((sampleRate * 300) / 1000 * 2)
        val resSilence = vad.process(longSilence)
        assertFalse(resSilence.isVoice, "Dopo la scadenza dell'hangover il gate deve chiudersi")
    }
}
