package com.grabber.audio.dsp

import kotlin.math.abs
import kotlin.math.tanh

/**
 * Catena DSP per PCM 16 bit little-endian mono: passa-alto → passa-basso → notch → riduzione
 * rumore → equalizzatore → limitatore morbido.
 *
 * Una catena ha stato (i filtri ricordano i campioni precedenti) e va usata da un solo flusso
 * audio, elaborato in ordine. [configure] può essere chiamato da un altro thread: la nuova
 * configurazione viene applicata all'inizio del blocco successivo, senza azzerare lo stato dei
 * filtri (nessun clic muovendo i cursori). Con una configurazione neutra l'audio non viene toccato.
 */
class AudioDspChain {
    @Volatile private var pendingConfig: AudioDspConfig = AudioDspConfig()
    @Volatile private var resetRequested = false

    private var appliedConfig: AudioDspConfig? = null
    private var appliedSampleRate = 0

    private val highPass = Biquad()
    private val lowPass = Biquad()
    private val notch = Biquad()
    private val noiseReducer = LmsNoiseReducer()
    private val eqBands = List(DspLimits.EQ_BANDS_HZ.size) { Biquad() }
    private val eqActive = BooleanArray(DspLimits.EQ_BANDS_HZ.size)
    private var highPassActive = false
    private var lowPassActive = false
    private var notchActive = false
    private var noiseReductionActive = false
    private var limiterActive = false

    private var work = DoubleArray(0)

    fun configure(config: AudioDspConfig) {
        pendingConfig = config.normalized()
    }

    /** Azzera lo stato dei filtri al blocco successivo (cambio frequenza o di destinazione). */
    fun reset() {
        resetRequested = true
    }

    /**
     * Elabora un blocco PCM e restituisce un nuovo array; con configurazione neutra restituisce
     * lo stesso array ricevuto.
     */
    fun process(pcm: ByteArray, sampleRate: Int): ByteArray {
        val config = pendingConfig
        if (config.isNeutral || sampleRate <= 0) {
            appliedConfig = null
            return pcm
        }
        if (resetRequested) {
            resetRequested = false
            resetState()
        }
        if (config !== appliedConfig || sampleRate != appliedSampleRate) {
            if (sampleRate != appliedSampleRate || appliedConfig == null) resetState()
            applyCoefficients(config, sampleRate)
            appliedConfig = config
            appliedSampleRate = sampleRate
        }

        val samples = pcm.size / 2
        if (work.size < samples) work = DoubleArray(samples)
        for (i in 0 until samples) {
            val low = pcm[2 * i].toInt() and 0xFF
            val high = pcm[2 * i + 1].toInt()
            work[i] = ((high shl 8) or low) / 32768.0
        }

        for (i in 0 until samples) {
            var x = work[i]
            if (highPassActive) x = highPass.process(x)
            if (lowPassActive) x = lowPass.process(x)
            if (notchActive) x = notch.process(x)
            if (noiseReductionActive) x = noiseReducer.process(x)
            for (b in eqBands.indices) if (eqActive[b]) x = eqBands[b].process(x)
            if (limiterActive) x = softLimit(x)
            if (x.isNaN()) x = 0.0
            work[i] = x
        }

        val out = ByteArray(pcm.size)
        for (i in 0 until samples) {
            val value = (work[i] * 32767.0).toInt().coerceIn(-32768, 32767)
            out[2 * i] = (value and 0xFF).toByte()
            out[2 * i + 1] = ((value shr 8) and 0xFF).toByte()
        }
        if (pcm.size % 2 == 1) out[pcm.size - 1] = pcm[pcm.size - 1]
        return out
    }

    private fun applyCoefficients(config: AudioDspConfig, sampleRate: Int) {
        val nyquistLimit = sampleRate * 0.45

        highPassActive = config.highPassHz > 0 && config.highPassHz < nyquistLimit
        if (highPassActive) highPass.coefficients = BiquadDesign.highPass(sampleRate, config.highPassHz.toDouble())

        lowPassActive = config.lowPassHz > 0 && config.lowPassHz < nyquistLimit
        if (lowPassActive) lowPass.coefficients = BiquadDesign.lowPass(sampleRate, config.lowPassHz.toDouble())

        notchActive = config.notchHz > 0 && config.notchHz < nyquistLimit
        if (notchActive) notch.coefficients = BiquadDesign.notch(sampleRate, config.notchHz.toDouble(), config.notchQ)

        noiseReductionActive = config.noiseReductionPercent > 0
        noiseReducer.level = config.noiseReductionPercent / 100.0

        val lastBand = DspLimits.EQ_BANDS_HZ.lastIndex
        DspLimits.EQ_BANDS_HZ.forEachIndexed { index, frequency ->
            val gain = config.eqGainsDb[index]
            // Le bande oltre ~Nyquist non hanno senso con il sample rate del ricevitore: ignorate.
            eqActive[index] = gain != 0.0 && frequency < nyquistLimit
            if (eqActive[index]) {
                eqBands[index].coefficients = when (index) {
                    0 -> BiquadDesign.lowShelf(sampleRate, frequency.toDouble(), gain)
                    lastBand -> BiquadDesign.highShelf(sampleRate, frequency.toDouble(), gain)
                    else -> BiquadDesign.peaking(sampleRate, frequency.toDouble(), EQ_Q, gain)
                }
            }
        }
        limiterActive = config.eqGainsDb.any { it > 0.0 }
    }

    private fun resetState() {
        highPass.reset()
        lowPass.reset()
        notch.reset()
        noiseReducer.reset()
        eqBands.forEach { it.reset() }
    }

    companion object {
        /** Q delle bande peaking: circa un'ottava, adatto a bande distanziate di un'ottava. */
        const val EQ_Q = 1.2
        private const val LIMIT_THRESHOLD = 0.8

        /** Lineare sotto la soglia, poi compressione morbida verso ±1 (nessun clipping netto). */
        fun softLimit(x: Double): Double {
            val magnitude = abs(x)
            if (magnitude <= LIMIT_THRESHOLD) return x
            val headroom = 1.0 - LIMIT_THRESHOLD
            val limited = LIMIT_THRESHOLD + headroom * tanh((magnitude - LIMIT_THRESHOLD) / headroom)
            return if (x < 0) -limited else limited
        }
    }
}
