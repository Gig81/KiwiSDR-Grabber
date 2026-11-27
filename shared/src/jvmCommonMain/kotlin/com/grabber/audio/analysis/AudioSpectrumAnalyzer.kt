package com.grabber.audio.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/**
 * Spettro medio dell'audio: FFT radix-2 a [size] punti con finestra di Hann, blocchi sovrapposti al 50 %
 * e media (aritmetica finché si accumula, poi esponenziale con peso [smoothing]).
 * Pensato per essere attivo solo quando serve (AFC, riconoscimento del modo).
 */
class AudioSpectrumAnalyzer(
    val size: Int = 4096,
    private val smoothing: Double = 0.2
) {
    init {
        require(size >= 64 && size and (size - 1) == 0) { "La dimensione della FFT deve essere una potenza di 2" }
    }

    private val window = DoubleArray(size) { 0.5 - 0.5 * cos(2 * PI * it / (size - 1)) }
    private val input = FloatArray(size)
    private var filled = 0
    private val re = DoubleArray(size)
    private val im = DoubleArray(size)
    private val averaged = DoubleArray(size / 2)
    private val cosTable = DoubleArray(size / 2) { cos(2 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { -sin(2 * PI * it / size) }

    /** Numero di blocchi FFT accumulati dall'ultimo [reset]. */
    var blocks: Int = 0
        private set

    var sampleRate: Int = 12000
        private set

    fun reset() {
        filled = 0
        blocks = 0
        averaged.fill(0.0)
    }

    fun push(samples: FloatArray, count: Int, sampleRate: Int) {
        if (sampleRate != this.sampleRate) {
            this.sampleRate = sampleRate
            reset()
        }
        for (i in 0 until count) {
            input[filled++] = samples[i]
            if (filled == size) {
                transform()
                // Sovrapposizione del 50 %: la seconda metà diventa l'inizio del blocco successivo.
                input.copyInto(input, 0, size / 2, size)
                filled = size / 2
            }
        }
    }

    /** Potenza media per bin (lineare), lunghezza size/2. */
    fun power(): DoubleArray = averaged.copyOf()

    /** Potenza media in dB per bin. */
    fun powerDb(): DoubleArray = DoubleArray(averaged.size) { 10 * log10(averaged[it] + 1e-20) }

    val binHz: Double get() = sampleRate.toDouble() / size

    private fun transform() {
        for (i in 0 until size) {
            re[i] = input[i] * window[i]
            im[i] = 0.0
        }
        fft(re, im)
        blocks++
        val weight = if (blocks <= (1 / smoothing).toInt()) 1.0 / blocks else smoothing
        for (k in averaged.indices) {
            val p = re[k] * re[k] + im[k] * im[k]
            averaged[k] += (p - averaged[k]) * weight
        }
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var length = 2
        while (length <= n) {
            val half = length / 2
            val step = n / length
            var start = 0
            while (start < n) {
                for (k in 0 until half) {
                    val c = cosTable[k * step]
                    val s = sinTable[k * step]
                    val a = start + k
                    val b = a + half
                    val tr = re[b] * c - im[b] * s
                    val ti = re[b] * s + im[b] * c
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
                start += length
            }
            length = length shl 1
        }
    }
}
