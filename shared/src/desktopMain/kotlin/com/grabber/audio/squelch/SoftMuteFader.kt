package com.grabber.audio.squelch

/**
 * Gestore di dissolvenza progressiva (Soft Mute / Anti-Pop) su campioni PCM 16-bit Little-Endian mono.
 * Evita clic e colpi acustici (discontinuità impulsive) all'apertura e chiusura del gate audio.
 */
class SoftMuteFader(
    private val sampleRate: Int = 12000,
    fadeDurationMs: Int = 10
) {
    // Guadagno corrente interpolato da 0.0f (silenzio totale) a 1.0f (passante pieno)
    private var currentGain: Float = 0.0f
    private var targetGain: Float = 0.0f
    private var fadeSamples: Int = calculateFadeSamples(fadeDurationMs)

    private fun calculateFadeSamples(ms: Int): Int {
        return (sampleRate * (ms / 1000.0)).toInt().coerceIn(12, sampleRate)
    }

    fun setFadeDurationMs(ms: Int) {
        fadeSamples = calculateFadeSamples(ms)
    }

    /**
     * Imposta il target del gate: true = OPEN (gain 1.0f), false = CLOSED (gain 0.0f).
     */
    fun setTargetOpen(open: Boolean) {
        targetGain = if (open) 1.0f else 0.0f
    }

    /**
     * Reimposta istantaneamente il guadagno (utile all'inizializzazione o reset).
     */
    fun reset(open: Boolean) {
        targetGain = if (open) 1.0f else 0.0f
        currentGain = targetGain
    }

    val isFullyClosed: Boolean
        get() = currentGain <= 0.001f && targetGain == 0.0f

    val isFullyOpen: Boolean
        get() = currentGain >= 0.999f && targetGain == 1.0f

    /**
     * Applica la dissolvenza in-place o copia su array di destinazione per campioni PCM 16-bit Little-Endian.
     * Se [inPlace] è false, restituisce un nuovo ByteArray elaborato.
     */
    fun process(pcmBytes: ByteArray, inPlace: Boolean = false): ByteArray {
        val out = if (inPlace) pcmBytes else ByteArray(pcmBytes.size)
        val sampleCount = pcmBytes.size / 2

        if (sampleCount == 0) return out

        val step = 1.0f / fadeSamples

        for (i in 0 until sampleCount) {
            // Aggiorna il guadagno verso targetGain con passo lineare continuo
            if (currentGain < targetGain) {
                currentGain = minOf(targetGain, currentGain + step)
                if (targetGain - currentGain < 0.0001f) currentGain = targetGain
            } else if (currentGain > targetGain) {
                currentGain = maxOf(targetGain, currentGain - step)
                if (currentGain - targetGain < 0.0001f) currentGain = targetGain
            }

            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toInt()

            val scaled = (sample * currentGain).toInt().coerceIn(-32768, 32767)

            out[i * 2] = (scaled and 0xFF).toByte()
            out[i * 2 + 1] = ((scaled shr 8) and 0xFF).toByte()
        }

        return out
    }
}
