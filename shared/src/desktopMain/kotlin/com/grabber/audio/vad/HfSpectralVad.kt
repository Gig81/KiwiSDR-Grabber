package com.grabber.audio.vad

import kotlin.math.*

/**
 * Motore DSP di Voice Activity Detection ottimizzato specificamente per ricevitori radio HF e KiwiSDR (12 kHz).
 *
 * Implementa:
 * 1. Banco di filtri biquad Butterworth (passa-banda 300 - 3000 Hz) per isolare l'energia vocale dalle componenti
 *    di ronzio di rete (< 300 Hz) e dal fruscio/soffio ad alta frequenza (> 3000 Hz).
 * 2. Rivelatore di scariche impulsive QRN (Crest Factor e Peak-to-RMS) per scartare fulmini e clic industriali.
 * 3. Analisi di armonicità e autocorrelazione per rilevare il passo fondamentale delle corde vocali umane (85 - 300 Hz).
 * 4. Zero-Crossing Rate per separare il rumore gaussiano dalle formanti del parlato.
 * 5. Timer di hangover per mantenere il gate stabile ed evitare spezzettamenti sillabici.
 */
class HfSpectralVad(
    override val sampleRate: Int = 12000,
    override var sensitivityThreshold: Float = 0.45f,
    override var hangoverMs: Int = 250
) : VoiceActivityDetector {

    // Filtri IIR Biquad del 2° ordine Butterworth: passa-alto a 300 Hz e passa-basso a 3000 Hz
    private val hpFilter = BiquadFilter.createHighPass(sampleRate.toFloat(), 300f, 0.7071f)
    private val lpFilter = BiquadFilter.createLowPass(sampleRate.toFloat(), 3000f, 0.7071f)

    // Stima adattiva del rumore di fondo (leaky integrator asimmetrico)
    private var noiseFloorRms: Float = 0.002f // ~ -54 dBFS

    // Tempo di hangover rimanente in millisecondi
    private var hangoverRemainingMs: Int = 0

    // Range di lag per autocorrelazione (85 Hz - 300 Hz a 12 kHz)
    private val minLag: Int = (sampleRate / 300.0).roundToInt().coerceAtLeast(1)
    private val maxLag: Int = (sampleRate / 85.0).roundToInt().coerceAtMost(sampleRate / 4)

    override fun process(pcmBytes: ByteArray): VadResult {
        val sampleCount = pcmBytes.size / 2
        if (sampleCount < 32) {
            return VadResult(
                isVoice = false,
                confidence = 0f,
                voiceBandRatio = 0f,
                crestFactor = 1f,
                isImpulseNoise = false,
                pitchHarmonicity = 0f,
                rmsDb = -90f,
                zeroCrossingRate = 0f
            )
        }

        // 1. Estrazione campioni PCM 16-bit little-endian normalizzati in [-1.0f, 1.0f]
        val samples = FloatArray(sampleCount)
        var sumSq = 0.0
        var peak = 0.0f
        var zeroCrossings = 0

        var prevSample = 0.0f
        for (i in 0 until sampleCount) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val raw = (high shl 8) or low
            val s = raw.toShort().toFloat() / 32768.0f
            samples[i] = s

            val absVal = abs(s)
            if (absVal > peak) peak = absVal
            sumSq += (s * s)

            if (i > 0 && ((s >= 0f && prevSample < 0f) || (s < 0f && prevSample >= 0f))) {
                zeroCrossings++
            }
            prevSample = s
        }

        val totalRms = sqrt(sumSq / sampleCount).toFloat()
        val rmsDb = if (totalRms > 1e-6f) 20f * log10(totalRms) else -90f
        val zcr = zeroCrossings.toFloat() / sampleCount

        // 2. Calcolo del Crest Factor (Picco / RMS) per rivelazione QRN
        val crestFactor = if (totalRms > 1e-6f) peak / totalRms else 1.0f

        // Scarica atmosferica (QRN): picco acuto isolato e Crest Factor molto elevato (> 5.5)
        // con ampiezza considerevole (> 0.04f = -28 dBFS)
        val isImpulseQrn = (crestFactor > 5.5f && peak > 0.04f) || (crestFactor > 7.0f)

        // 3. Filtraggio passa-banda vocale (300 - 3000 Hz)
        var voiceBandSumSq = 0.0
        for (i in 0 until sampleCount) {
            val hpOut = hpFilter.process(samples[i])
            val bandOut = lpFilter.process(hpOut)
            voiceBandSumSq += (bandOut * bandOut)
        }
        val voiceBandRms = sqrt(voiceBandSumSq / sampleCount).toFloat()
        val voiceBandRatio = if (totalRms > 1e-6f) {
            (voiceBandRms / totalRms).coerceIn(0.0f, 1.0f)
        } else {
            0.0f
        }

        // 4. Analisi di autocorrelazione per armonicità fondamentale (pitch F0 tra 85 e 300 Hz)
        var maxAutocorr = 0.0f
        if (totalRms > 0.003f && sampleCount > maxLag + 40) {
            val corrWindow = min(sampleCount - maxLag, 256)
            var baseEnergy = 0.0
            for (n in 0 until corrWindow) {
                baseEnergy += samples[n] * samples[n]
            }

            if (baseEnergy > 1e-6) {
                // Cerchiamo il picco nel range lag F0
                for (lag in minLag..maxLag step 2) {
                    var cross = 0.0
                    var lagEnergy = 0.0
                    for (n in 0 until corrWindow) {
                        val s0 = samples[n]
                        val sLag = samples[n + lag]
                        cross += s0 * sLag
                        lagEnergy += sLag * sLag
                    }
                    val denom = sqrt(baseEnergy * lagEnergy)
                    if (denom > 1e-6) {
                        val r = (cross / denom).toFloat()
                        if (r > maxAutocorr) {
                            maxAutocorr = r
                        }
                    }
                }
            }
        }

        // 5. Aggiornamento stima adattiva del rumore di fondo
        if (!isImpulseQrn && totalRms > 1e-6f) {
            if (totalRms < noiseFloorRms) {
                // Discesa rapida verso il minimo di rumore
                noiseFloorRms = noiseFloorRms * 0.90f + totalRms * 0.10f
            } else {
                // Risalita molto lenta (non segue la voce parlata)
                noiseFloorRms = noiseFloorRms * 0.995f + totalRms * 0.005f
            }
        }
        noiseFloorRms = noiseFloorRms.coerceIn(0.0005f, 0.1f)

        // Rapporto segnale/rumore locale
        val snrLinear = totalRms / noiseFloorRms

        // 6. Calcolo del punteggio di confidenza vocale (0.0f - 1.0f)
        val rawConfidence: Float

        if (totalRms < 0.002f || snrLinear < 1.15f) {
            // Silenzio radio o segnale troppo debole
            rawConfidence = 0.0f
        } else if (isImpulseQrn) {
            // Scarica impulsiva QRN / fulmine: scartata
            rawConfidence = 0.05f
        } else if (voiceBandRatio < 0.50f) {
            // Se meno del 50% dell'energia è in 300-3000 Hz, è rumore bianco o ronzio a bassa frequenza
            rawConfidence = 0.05f * (voiceBandRatio / 0.50f)
        } else {
            // Banda vocale: voce umana tipicamente > 0.65; rumore bianco ~ 0.45
            val bandScore = ((voiceBandRatio - 0.50f) / 0.35f).coerceIn(0.0f, 1.0f)

            // Armonicità (pitch): voce vocalizzata > 0.30; rumore bianco < 0.18
            val harmonicityScore = ((maxAutocorr - 0.20f) / 0.40f).coerceIn(0.0f, 1.0f)

            // Penalità per segnali con altissimo Zero-Crossing Rate (rumore fruscio a frequenze alte)
            val zcrFactor = if (zcr > 0.35f) (1.0f - ((zcr - 0.35f) / 0.25f)).coerceIn(0.1f, 1.0f) else 1.0f

            // Combinazione: richiede sia presenza in banda vocale che armonicità o forma d'onda vocale
            val coreScore = (0.55f * bandScore + 0.45f * harmonicityScore) * zcrFactor

            // Gate SNR: se il segnale non emerge dal rumore di fondo, attenua
            val snrFactor = ((snrLinear - 1.2f) / 2.0f).coerceIn(0.2f, 1.0f)

            rawConfidence = (coreScore * snrFactor).coerceIn(0.0f, 1.0f)
        }

        // 7. Macchina a stati di Hangover temporale per evitare tagli tra sillabe
        val frameDurationMs = (sampleCount * 1000) / sampleRate
        val isImmediateVoice = rawConfidence >= sensitivityThreshold

        val isVoiceFinal: Boolean
        if (isImmediateVoice) {
            hangoverRemainingMs = hangoverMs
            isVoiceFinal = true
        } else {
            if (hangoverRemainingMs > 0 && totalRms > 0.0015f && !isImpulseQrn) {
                // Ancora in periodo di hangover: manteniamo il gate aperto per non troncare la frase
                hangoverRemainingMs = (hangoverRemainingMs - frameDurationMs).coerceAtLeast(0)
                isVoiceFinal = true
            } else {
                hangoverRemainingMs = 0
                isVoiceFinal = false
            }
        }

        return VadResult(
            isVoice = isVoiceFinal,
            confidence = rawConfidence,
            voiceBandRatio = voiceBandRatio,
            crestFactor = crestFactor,
            isImpulseNoise = isImpulseQrn,
            pitchHarmonicity = maxAutocorr,
            rmsDb = rmsDb,
            zeroCrossingRate = zcr
        )
    }

    override fun reset() {
        hpFilter.reset()
        lpFilter.reset()
        noiseFloorRms = 0.002f
        hangoverRemainingMs = 0
    }
}

/**
 * Filtro IIR Biquad del secondo ordine con forma diretta Direct Form II.
 */
class BiquadFilter private constructor(
    private val b0: Float,
    private val b1: Float,
    private val b2: Float,
    private val a1: Float,
    private val a2: Float
) {
    private var z1: Float = 0.0f
    private var z2: Float = 0.0f

    fun process(sample: Float): Float {
        val out = b0 * sample + z1
        z1 = b1 * sample - a1 * out + z2
        z2 = b2 * sample - a2 * out
        return out
    }

    fun reset() {
        z1 = 0.0f
        z2 = 0.0f
    }

    companion object {
        fun createHighPass(sampleRate: Float, cutoffHz: Float, q: Float = 0.7071f): BiquadFilter {
            val w0 = 2.0 * Math.PI * (cutoffHz / sampleRate)
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q)

            val a0 = 1.0 + alpha
            val b0 = (1.0 + cosW0) / 2.0
            val b1 = -(1.0 + cosW0)
            val b2 = (1.0 + cosW0) / 2.0
            val a1 = -2.0 * cosW0
            val a2 = 1.0 - alpha

            return BiquadFilter(
                b0 = (b0 / a0).toFloat(),
                b1 = (b1 / a0).toFloat(),
                b2 = (b2 / a0).toFloat(),
                a1 = (a1 / a0).toFloat(),
                a2 = (a2 / a0).toFloat()
            )
        }

        fun createLowPass(sampleRate: Float, cutoffHz: Float, q: Float = 0.7071f): BiquadFilter {
            val w0 = 2.0 * Math.PI * (cutoffHz / sampleRate)
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q)

            val a0 = 1.0 + alpha
            val b0 = (1.0 - cosW0) / 2.0
            val b1 = 1.0 - cosW0
            val b2 = (1.0 - cosW0) / 2.0
            val a1 = -2.0 * cosW0
            val a2 = 1.0 - alpha

            return BiquadFilter(
                b0 = (b0 / a0).toFloat(),
                b1 = (b1 / a0).toFloat(),
                b2 = (b2 / a0).toFloat(),
                a1 = (a1 / a0).toFloat(),
                a2 = (a2 / a0).toFloat()
            )
        }
    }
}
