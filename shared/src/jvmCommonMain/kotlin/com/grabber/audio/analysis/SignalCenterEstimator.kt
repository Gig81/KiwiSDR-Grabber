package com.grabber.audio.analysis

import kotlin.math.abs
import kotlin.math.log10

/** Picco trovato in uno spettro: frequenza (interpolata), SNR sul rumore mediano e larghezza a -6 dB. */
data class SpectralPeak(val frequencyHz: Double, val snrDb: Double, val widthHz: Double)

/**
 * Ricerca di un segnale in uno spettro di potenza (lineare, bin equispaziati da [startHz] con passo
 * [binHz]): picchi sopra il rumore mediano, scelta del più vicino al riferimento (non del più forte),
 * interpolazione parabolica del massimo.
 */
object SignalCenterEstimator {

    /**
     * Picco più vicino a [referenceHz] entro ±[searchHz], con SNR ≥ [minSnrDb] e larghezza ≤
     * [maxWidthHz]; null se non c'è un segnale affidabile.
     */
    fun nearestPeak(
        power: DoubleArray,
        startHz: Double,
        binHz: Double,
        referenceHz: Double,
        searchHz: Double,
        minSnrDb: Double = 10.0,
        maxWidthHz: Double = Double.MAX_VALUE
    ): SpectralPeak? {
        if (power.size < 8) return null
        val noise = median(power).coerceAtLeast(1e-20)
        val first = (((referenceHz - searchHz) - startHz) / binHz).toInt().coerceIn(1, power.size - 2)
        val last = (((referenceHz + searchHz) - startHz) / binHz).toInt().coerceIn(1, power.size - 2)
        var best: SpectralPeak? = null
        for (k in first..last) {
            val p = power[k]
            if (p <= power[k - 1] || p < power[k + 1]) continue
            val snr = 10 * log10(p / noise)
            if (snr < minSnrDb) continue
            val width = widthAt(power, k, p / 4.0) * binHz
            if (width > maxWidthHz) continue
            val frequency = startHz + (k + parabolicOffset(power[k - 1], p, power[k + 1])) * binHz
            val candidate = SpectralPeak(frequency, snr, width)
            if (best == null || abs(frequency - referenceHz) < abs(best.frequencyHz - referenceHz)) best = candidate
        }
        return best
    }

    /**
     * Coppia di toni distanti [shiftHz] (FSK) il cui centro è più vicino a [referenceHz]: restituisce il
     * centro stimato o null se i due toni non emergono dal rumore di almeno [minSnrDb].
     */
    fun fskCenter(
        power: DoubleArray,
        binHz: Double,
        referenceHz: Double,
        shiftHz: Double,
        searchHz: Double,
        minSnrDb: Double = 8.0
    ): Double? {
        val noise = median(power).coerceAtLeast(1e-20)
        val shiftBins = shiftHz / binHz
        var bestCenter: Double? = null
        var bestScore = 0.0
        val firstCenter = ((referenceHz - searchHz) / binHz).toInt()
        val lastCenter = ((referenceHz + searchHz) / binHz).toInt()
        for (c in firstCenter..lastCenter) {
            val lo = (c - shiftBins / 2).toInt()
            val hi = (c + shiftBins / 2).toInt()
            if (lo < 1 || hi >= power.size - 1) continue
            val score = minOf(localMax(power, lo), localMax(power, hi))
            if (score > bestScore) {
                bestScore = score
                bestCenter = c * binHz
            }
        }
        if (bestCenter == null || 10 * log10(bestScore / noise) < minSnrDb) return null
        return bestCenter
    }

    /** Baricentro dell'energia tra [lowHz] e [highHz] (segnali larghi: FAX, NFM). */
    fun centroid(power: DoubleArray, startHz: Double, binHz: Double, lowHz: Double, highHz: Double): Double? {
        val first = ((lowHz - startHz) / binHz).toInt().coerceIn(0, power.size - 1)
        val last = ((highHz - startHz) / binHz).toInt().coerceIn(0, power.size - 1)
        val noise = median(power)
        var weighted = 0.0
        var total = 0.0
        for (k in first..last) {
            val p = (power[k] - noise).coerceAtLeast(0.0)
            weighted += p * (startHz + k * binHz)
            total += p
        }
        if (total <= 0.0 || 10 * log10((total / (last - first + 1)) / noise.coerceAtLeast(1e-20) + 1) < 6.0) return null
        return weighted / total
    }

    private fun localMax(power: DoubleArray, k: Int): Double = maxOf(power[k - 1], power[k], power[k + 1])

    private fun widthAt(power: DoubleArray, k: Int, level: Double): Int {
        var left = k
        while (left > 0 && power[left] > level) left--
        var right = k
        while (right < power.size - 1 && power[right] > level) right++
        return right - left
    }

    private fun parabolicOffset(a: Double, b: Double, c: Double): Double {
        val la = log10(a + 1e-20)
        val lb = log10(b + 1e-20)
        val lc = log10(c + 1e-20)
        val denominator = la - 2 * lb + lc
        return if (denominator == 0.0) 0.0 else (0.5 * (la - lc) / denominator).coerceIn(-0.5, 0.5)
    }

    internal fun median(values: DoubleArray): Double {
        val sorted = values.sortedArray()
        return sorted[sorted.size / 2]
    }
}
