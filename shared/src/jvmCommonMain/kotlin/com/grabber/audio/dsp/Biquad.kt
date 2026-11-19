package com.grabber.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Coefficienti normalizzati (a0 = 1) di un filtro biquad. */
data class BiquadCoefficients(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double
) {
    companion object {
        /** Filtro neutro: l'uscita è uguale all'ingresso. */
        val IDENTITY = BiquadCoefficients(1.0, 0.0, 0.0, 0.0, 0.0)
    }
}

/**
 * Progetto dei filtri secondo le formule di R. Bristow-Johnson, "Cookbook formulae for audio EQ
 * biquad filter coefficients" (pubblico dominio). Funzioni pure, testabili senza audio.
 */
object BiquadDesign {

    fun peaking(sampleRate: Int, frequencyHz: Double, q: Double, gainDb: Double): BiquadCoefficients {
        val a = 10.0.pow(gainDb / 40.0)
        val (w0, alpha) = omegaAlpha(sampleRate, frequencyHz, q)
        val cosW = cos(w0)
        return normalize(
            b0 = 1 + alpha * a, b1 = -2 * cosW, b2 = 1 - alpha * a,
            a0 = 1 + alpha / a, a1 = -2 * cosW, a2 = 1 - alpha / a
        )
    }

    fun lowShelf(sampleRate: Int, frequencyHz: Double, gainDb: Double, slope: Double = 1.0): BiquadCoefficients {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * frequencyHz / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / 2 * sqrt((a + 1 / a) * (1 / slope - 1) + 2)
        val twoSqrtAAlpha = 2 * sqrt(a) * alpha
        return normalize(
            b0 = a * ((a + 1) - (a - 1) * cosW + twoSqrtAAlpha),
            b1 = 2 * a * ((a - 1) - (a + 1) * cosW),
            b2 = a * ((a + 1) - (a - 1) * cosW - twoSqrtAAlpha),
            a0 = (a + 1) + (a - 1) * cosW + twoSqrtAAlpha,
            a1 = -2 * ((a - 1) + (a + 1) * cosW),
            a2 = (a + 1) + (a - 1) * cosW - twoSqrtAAlpha
        )
    }

    fun highShelf(sampleRate: Int, frequencyHz: Double, gainDb: Double, slope: Double = 1.0): BiquadCoefficients {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * frequencyHz / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / 2 * sqrt((a + 1 / a) * (1 / slope - 1) + 2)
        val twoSqrtAAlpha = 2 * sqrt(a) * alpha
        return normalize(
            b0 = a * ((a + 1) + (a - 1) * cosW + twoSqrtAAlpha),
            b1 = -2 * a * ((a - 1) + (a + 1) * cosW),
            b2 = a * ((a + 1) + (a - 1) * cosW - twoSqrtAAlpha),
            a0 = (a + 1) - (a - 1) * cosW + twoSqrtAAlpha,
            a1 = 2 * ((a - 1) - (a + 1) * cosW),
            a2 = (a + 1) - (a - 1) * cosW - twoSqrtAAlpha
        )
    }

    fun highPass(sampleRate: Int, frequencyHz: Double, q: Double = BUTTERWORTH_Q): BiquadCoefficients {
        val (w0, alpha) = omegaAlpha(sampleRate, frequencyHz, q)
        val cosW = cos(w0)
        return normalize(
            b0 = (1 + cosW) / 2, b1 = -(1 + cosW), b2 = (1 + cosW) / 2,
            a0 = 1 + alpha, a1 = -2 * cosW, a2 = 1 - alpha
        )
    }

    fun lowPass(sampleRate: Int, frequencyHz: Double, q: Double = BUTTERWORTH_Q): BiquadCoefficients {
        val (w0, alpha) = omegaAlpha(sampleRate, frequencyHz, q)
        val cosW = cos(w0)
        return normalize(
            b0 = (1 - cosW) / 2, b1 = 1 - cosW, b2 = (1 - cosW) / 2,
            a0 = 1 + alpha, a1 = -2 * cosW, a2 = 1 - alpha
        )
    }

    fun notch(sampleRate: Int, frequencyHz: Double, q: Double): BiquadCoefficients {
        val (w0, alpha) = omegaAlpha(sampleRate, frequencyHz, q)
        val cosW = cos(w0)
        return normalize(
            b0 = 1.0, b1 = -2 * cosW, b2 = 1.0,
            a0 = 1 + alpha, a1 = -2 * cosW, a2 = 1 - alpha
        )
    }

    /** Guadagno in dB del filtro alla frequenza indicata (risposta in frequenza analitica). */
    fun magnitudeDb(c: BiquadCoefficients, frequencyHz: Double, sampleRate: Int): Double {
        val w = 2 * PI * frequencyHz / sampleRate
        val cos1 = cos(w); val sin1 = sin(w)
        val cos2 = cos(2 * w); val sin2 = sin(2 * w)
        val numRe = c.b0 + c.b1 * cos1 + c.b2 * cos2
        val numIm = -(c.b1 * sin1 + c.b2 * sin2)
        val denRe = 1 + c.a1 * cos1 + c.a2 * cos2
        val denIm = -(c.a1 * sin1 + c.a2 * sin2)
        val magnitudeSquared = (numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm)
        return 10 * log10(magnitudeSquared.coerceAtLeast(1e-30))
    }

    const val BUTTERWORTH_Q = 0.7071067811865476

    private fun omegaAlpha(sampleRate: Int, frequencyHz: Double, q: Double): Pair<Double, Double> {
        val w0 = 2 * PI * frequencyHz / sampleRate
        return w0 to sin(w0) / (2 * q)
    }

    private fun normalize(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
        BiquadCoefficients(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
}

/**
 * Filtro biquad in forma trasposta diretta II. Lo stato è conservato tra un blocco e l'altro;
 * cambiare i coefficienti non azzera lo stato, così i cursori non producono clic.
 */
class Biquad(coefficients: BiquadCoefficients = BiquadCoefficients.IDENTITY) {
    var coefficients: BiquadCoefficients = coefficients
        set(value) {
            field = value
            b0 = value.b0; b1 = value.b1; b2 = value.b2; a1 = value.a1; a2 = value.a2
        }

    private var b0 = coefficients.b0
    private var b1 = coefficients.b1
    private var b2 = coefficients.b2
    private var a1 = coefficients.a1
    private var a2 = coefficients.a2
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        // Evita i valori denormalizzati (lentissimi) quando il segnale va a zero.
        if (z1 > -1e-20 && z1 < 1e-20) z1 = 0.0
        if (z2 > -1e-20 && z2 < 1e-20) z2 = 0.0
        return y
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }
}
