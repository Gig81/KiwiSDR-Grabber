package com.grabber.decoder.fsk

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Demodulatore FSK non coerente: per ciascun tono (mark e space) il segnale viene portato in banda
 * base e integrato su una finestra mobile lunga un bit (filtro adattato). La differenza normalizzata
 * delle energie dà un valore "morbido" in -1..1 (positivo = mark).
 *
 * Il passaggio per lo zero del valore cade a metà finestra dopo una transizione di bit: chi decide i
 * bit campiona alla fine di ogni bit (vedi [AsyncBitDecoder] e [SyncBitClock]).
 *
 * Include un AFC lento che sposta entrambi i toni (entro ±[afcRangeHz]) verso il massimo di energia,
 * così piccoli errori di sintonia non degradano la decodifica.
 */
class FskDemodulator(
    private val sampleRate: Int,
    centerHz: Double,
    shiftHz: Double,
    baud: Double,
    /** Mark sul tono più basso invece che su quello più alto. */
    private val markIsLower: Boolean,
    private val afcRangeHz: Double = shiftHz / 2.0
) {
    /** Campioni per bit (lunghezza della finestra del filtro adattato). */
    val samplesPerBit: Int = (sampleRate / baud).roundToInt().coerceAtLeast(4)

    private val nominalCenter = centerHz
    private val halfShift = shiftHz / 2.0
    var centerHz: Double = centerHz
        private set

    private val hi = ToneIntegrator(samplesPerBit)
    private val lo = ToneIntegrator(samplesPerBit)
    // Integratori AFC: energia mezzo shift sopra e sotto ciascun tono, per stimare lo scostamento.
    private val hiUp = ToneIntegrator(samplesPerBit)
    private val hiDown = ToneIntegrator(samplesPerBit)
    private val loUp = ToneIntegrator(samplesPerBit)
    private val loDown = ToneIntegrator(samplesPerBit)

    private val power = DoubleArray(samplesPerBit)
    private var powerSum = 0.0
    private var powerIndex = 0
    private var levelAverage = 0.0
    private var afcError = 0.0
    private var sampleCount = 0L
    private val acquisitionBuffer = FloatArray(sampleRate)
    private var acquisitionIndex = 0

    /** Livello del segnale FSK rispetto alla potenza totale (0 = solo rumore, 1 = solo toni). */
    val level: Double get() = levelAverage

    /** Vero se c'è un segnale FSK riconoscibile. */
    val hasSignal: Boolean get() = levelAverage > SIGNAL_THRESHOLD

    init {
        retune()
    }

    private fun retune() {
        hi.setFrequency(centerHz + halfShift, sampleRate)
        lo.setFrequency(centerHz - halfShift, sampleRate)
        val probe = halfShift / 2.0
        hiUp.setFrequency(centerHz + halfShift + probe, sampleRate)
        hiDown.setFrequency(centerHz + halfShift - probe, sampleRate)
        loUp.setFrequency(centerHz - halfShift + probe, sampleRate)
        loDown.setFrequency(centerHz - halfShift - probe, sampleRate)
    }

    /** Elabora un campione e restituisce il valore morbido (positivo = mark). */
    fun process(sample: Float): Double {
        val x = sample.toDouble()
        val eHi = hi.push(x)
        val eLo = lo.push(x)
        val eHiUp = hiUp.push(x)
        val eHiDown = hiDown.push(x)
        val eLoUp = loUp.push(x)
        val eLoDown = loDown.push(x)

        acquisitionBuffer[acquisitionIndex++] = sample
        if (acquisitionIndex == acquisitionBuffer.size) {
            acquisitionIndex = 0
            if (!hasSignal) acquire()
        }

        val p = x * x
        powerSum += p - power[powerIndex]
        power[powerIndex] = p
        powerIndex = (powerIndex + 1) % samplesPerBit
        val meanPower = (powerSum / samplesPerBit).coerceAtLeast(1e-12)

        val n = samplesPerBit.toDouble()
        val toneFraction = ((maxOf(eHi, eLo) * 2.0) / (n * n) / meanPower).coerceIn(0.0, 1.0)
        levelAverage += (toneFraction - levelAverage) * LEVEL_SMOOTHING

        // AFC: con segnale presente, confronta l'energia sopra e sotto i toni e sposta il centro.
        if (levelAverage > AFC_TRACK_LEVEL) {
            val up = eHiUp + eLoUp
            val down = eHiDown + eLoDown
            val total = up + down + 1e-12
            afcError += ((up - down) / total - afcError) * AFC_SMOOTHING
        }
        if (++sampleCount % samplesPerBit.toLong() == 0L && levelAverage > AFC_TRACK_LEVEL && kotlin.math.abs(afcError) > 0.1) {
            val step = afcError * halfShift * AFC_STEP
            centerHz = (centerHz + step).coerceIn(nominalCenter - afcRangeHz, nominalCenter + afcRangeHz)
            retune()
        }

        val sum = eHi + eLo + 1e-12
        val soft = (eHi - eLo) / sum
        return if (markIsLower) -soft else soft
    }

    /**
     * Acquisizione: senza segnale agganciato cerca, nell'ultimo secondo di audio, la coppia di toni
     * distanti uno shift con più energia entro l'intervallo AFC, e vi sposta il centro.
     */
    private fun acquire() {
        val step = ACQUISITION_STEP_HZ
        val low = nominalCenter - afcRangeHz - halfShift
        val count = ((2 * afcRangeHz + 2 * halfShift) / step).toInt() + 1
        val energies = DoubleArray(count) { goertzel(acquisitionBuffer, low + it * step) }
        val shiftSteps = (2 * halfShift / step).roundToInt()
        var bestScore = 0.0
        var bestIndex = -1
        for (i in 0 until count - shiftSteps) {
            val score = minOf(energies[i], energies[i + shiftSteps])
            if (score > bestScore) { bestScore = score; bestIndex = i }
        }
        if (bestIndex < 0) return
        val median = energies.sorted()[count / 2]
        if (bestScore < median * ACQUISITION_RATIO) return
        val candidate = low + bestIndex * step + halfShift
        if (kotlin.math.abs(candidate - centerHz) >= step) {
            centerHz = candidate.coerceIn(nominalCenter - afcRangeHz, nominalCenter + afcRangeHz)
            retune()
        }
    }

    private fun goertzel(data: FloatArray, hz: Double): Double {
        val c = 2 * cos(2 * PI * hz / sampleRate)
        var s1 = 0.0
        var s2 = 0.0
        for (v in data) {
            val s0 = v + c * s1 - s2
            s2 = s1
            s1 = s0
        }
        return s1 * s1 + s2 * s2 - c * s1 * s2
    }

    fun reset() {
        hi.reset(); lo.reset(); hiUp.reset(); hiDown.reset(); loUp.reset(); loDown.reset()
        power.fill(0.0)
        powerSum = 0.0
        levelAverage = 0.0
        afcError = 0.0
        centerHz = nominalCenter
        retune()
    }

    /** Offset corrente del centro rispetto a quello nominale, in Hz (AFC interno). */
    val afcOffsetHz: Double get() = centerHz - nominalCenter

    private companion object {
        const val SIGNAL_THRESHOLD = 0.25
        const val LEVEL_SMOOTHING = 0.002
        const val AFC_SMOOTHING = 0.001
        const val AFC_STEP = 0.005
        const val AFC_TRACK_LEVEL = 0.4
        const val ACQUISITION_STEP_HZ = 5.0
        const val ACQUISITION_RATIO = 8.0
    }
}

/**
 * Energia di un tono su una finestra mobile di [length] campioni: oscillatore locale, prodotto
 * I/Q e somme scorrevoli (costo costante per campione).
 */
internal class ToneIntegrator(private val length: Int) {
    private val bufI = DoubleArray(length)
    private val bufQ = DoubleArray(length)
    private var sumI = 0.0
    private var sumQ = 0.0
    private var index = 0
    private var cosStep = 1.0
    private var sinStep = 0.0
    private var oscCos = 1.0
    private var oscSin = 0.0
    private var renormalize = 0
    private var wraps = 0

    fun setFrequency(hz: Double, sampleRate: Int) {
        val w = 2 * PI * hz / sampleRate
        cosStep = cos(w)
        sinStep = sin(w)
    }

    fun push(x: Double): Double {
        val i = x * oscCos
        val q = x * oscSin
        sumI += i - bufI[index]
        sumQ += q - bufQ[index]
        bufI[index] = i
        bufQ[index] = q
        index = if (index + 1 == length) 0 else index + 1
        if (index == 0 && ++wraps % 256 == 0) {
            // Ricalcolo esatto delle somme: evita l'accumulo degli errori di arrotondamento.
            sumI = bufI.sum()
            sumQ = bufQ.sum()
        }
        // Rotazione dell'oscillatore (ricorrenza), rinormalizzata periodicamente per evitare deriva.
        val c = oscCos * cosStep - oscSin * sinStep
        val s = oscSin * cosStep + oscCos * sinStep
        oscCos = c
        oscSin = s
        if (++renormalize >= 1024) {
            renormalize = 0
            val norm = sqrt(oscCos * oscCos + oscSin * oscSin)
            oscCos /= norm
            oscSin /= norm
        }
        return sumI * sumI + sumQ * sumQ
    }

    fun reset() {
        bufI.fill(0.0)
        bufQ.fill(0.0)
        sumI = 0.0
        sumQ = 0.0
        index = 0
    }
}
