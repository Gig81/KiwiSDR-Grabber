package com.grabber.audio.analysis

import com.grabber.model.ModulationMode
import com.grabber.model.digital.DigitalMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Modo suggerito dal riconoscimento automatico. [mode] è null per i segnali riconosciuti ma senza
 * decoder (es. PSK31): in quel caso c'è solo l'etichetta.
 */
data class ModeSuggestion(
    val mode: DigitalMode?,
    val label: String,
    val confidence: Double,
    /** Parametri stimati (es. "shift 450 Hz"). */
    val detail: String = "",
    /** Shift FSK stimato, per impostare il decoder RTTY. */
    val fskShiftHz: Int? = null,
    /** Centro audio stimato del segnale. */
    val audioCenterHz: Double? = null,
    /** Vero se per riconoscere meglio serve una banda passante più larga (es. in CW). */
    val widenPassband: Boolean = false
) {
    val percent: Int get() = (confidence * 100).roundToInt()
}

/**
 * Riconoscimento euristico del modo (§6 del piano), senza apprendimento automatico: spettro medio e
 * picchi, coppie di toni FSK, inviluppo on/off di un tono (CW), occupazione di banda (FAX, voce) e
 * contesto di frequenza dal catalogo. Il risultato cambia solo se lo stesso candidato vince per due
 * finestre consecutive (isteresi).
 */
class ModeClassifier(private val windowSeconds: Double = 3.0) {
    private val analyzer = AudioSpectrumAnalyzer(size = 4096, smoothing = 0.15)
    private var sampleRate = 12000
    private var samplesInWindow = 0
    private var context = Context(ModulationMode.USB, 0.0, 300, 2700)

    // Inviluppo a blocchi di 10 ms del tono più forte (per il CW).
    private var envelopeFrequency = 0.0
    private val envelope = ArrayList<Double>()
    private var goertzelS1 = 0.0
    private var goertzelS2 = 0.0
    private var goertzelCount = 0

    private var candidate: ModeSuggestion? = null
    private var candidateWins = 0
    private var misses = 0

    var suggestion: ModeSuggestion? = null
        private set

    /** Picchi dell'ultima finestra, per diagnostica e test. */
    internal var debugInfo: String = ""
        private set

    /** Sintonia corrente: modo, frequenza mostrata e banda passante (Hz audio). */
    data class Context(val modulation: ModulationMode, val frequencyKhz: Double, val lowHz: Int, val highHz: Int)

    fun setContext(newContext: Context) {
        if (newContext != context) {
            context = newContext
            reset()
        }
    }

    fun reset() {
        analyzer.reset()
        samplesInWindow = 0
        envelope.clear()
        candidate = null
        candidateWins = 0
        misses = 0
        suggestion = null
    }

    /** Elabora campioni audio; restituisce true quando la valutazione di una finestra ha aggiornato il suggerimento. */
    fun process(samples: FloatArray, count: Int, rate: Int): Boolean {
        if (rate != sampleRate) {
            sampleRate = rate
            reset()
        }
        analyzer.push(samples, count, rate)
        trackEnvelope(samples, count)
        samplesInWindow += count
        if (samplesInWindow < windowSeconds * sampleRate) return false
        samplesInWindow = 0
        val result = evaluate()
        envelope.clear()
        return applyHysteresis(result)
    }

    private fun applyHysteresis(result: ModeSuggestion?): Boolean {
        val previous = suggestion
        if (result == null) {
            if (++misses >= 2) suggestion = null
            candidate = null
            candidateWins = 0
        } else {
            misses = 0
            if (candidate?.label == result.label) candidateWins++ else {
                candidate = result
                candidateWins = 1
            }
            if (candidateWins >= 2 || suggestion?.label == result.label) suggestion = result
        }
        return previous != suggestion
    }

    private fun trackEnvelope(samples: FloatArray, count: Int) {
        if (envelopeFrequency <= 0.0) return
        val block = sampleRate / 100
        val coefficient = 2 * cos(2 * PI * envelopeFrequency / sampleRate)
        for (i in 0 until count) {
            val s0 = samples[i] + coefficient * goertzelS1 - goertzelS2
            goertzelS2 = goertzelS1
            goertzelS1 = s0
            if (++goertzelCount >= block) {
                envelope += goertzelS1 * goertzelS1 + goertzelS2 * goertzelS2 - coefficient * goertzelS1 * goertzelS2
                goertzelS1 = 0.0
                goertzelS2 = 0.0
                goertzelCount = 0
            }
        }
    }

    /** Valutazione di una finestra: restituisce il candidato migliore o null (rumore, voce, nulla di noto). */
    internal fun evaluate(): ModeSuggestion? {
        val power = analyzer.power()
        val binHz = analyzer.binHz
        val low = minOf(abs(context.lowHz), abs(context.highHz)).coerceAtLeast(50)
        val high = maxOf(abs(context.lowHz), abs(context.highHz)).coerceAtMost((sampleRate / 2) - 50)
        val first = (low / binHz).toInt().coerceAtLeast(1)
        val last = (high / binHz).toInt().coerceAtMost(power.size - 2)
        if (last - first < 8) return null
        val band = power.copyOfRange(first, last + 1)
        // Rumore: 20° percentile (in bande strette il segnale occupa gran parte dei bin, la mediana lo sovrastima).
        val noise = band.sortedArray()[band.size / 5].coerceAtLeast(1e-20)
        val total = band.sum()
        val peaks = findPeaks(power, first, last, noise, binHz)
        val strongest = peaks.maxByOrNull { it.snrDb }
        debugInfo = peaks.joinToString { "${it.frequencyHz.roundToInt()}Hz/${it.snrDb.roundToInt()}dB/w${it.widthHz.roundToInt()}" }
        envelopeFrequency = strongest?.frequencyHz ?: 0.0

        val freq = context.frequencyKhz
        val frequencyHint = frequencyContext(freq)

        // FT8/FT4/WSPR: molti toni stretti deboli sulle frequenze note (prima delle coppie FSK).
        if (frequencyHint in setOf(DigitalMode.FT8, DigitalMode.FT4, DigitalMode.WSPR) && peaks.size >= 2) {
            val mode = frequencyHint!!
            return ModeSuggestion(mode, mode.displayName, 0.75, "frequenza ${mode.displayName} e ${peaks.size} segnali")
        }

        // NAVTEX / RTTY / DSC: due toni dominanti a uno shift noto.
        fskPair(peaks)?.let { (center, shift) ->
            val around = maxOf(30.0, shift * 0.25)
            val pairShare = (shareBetween(power, binHz, center - shift / 2 - around, center - shift / 2 + around) +
                shareBetween(power, binHz, center + shift / 2 - around, center + shift / 2 + around)) / total
            if (pairShare < MIN_FSK_SHARE) return@let
            var confidence = 0.55 + (pairShare.coerceIn(0.0, 0.5))
            val mode = when {
                frequencyHint == DigitalMode.NAVTEX && abs(shift - 170) < 20 -> DigitalMode.NAVTEX.also { confidence += 0.15 }
                frequencyHint == DigitalMode.DSC && abs(shift - 170) < 20 -> DigitalMode.DSC.also { confidence += 0.15 }
                else -> DigitalMode.RTTY
            }
            return ModeSuggestion(
                mode = mode,
                label = mode.displayName,
                confidence = confidence.coerceAtMost(0.98),
                detail = "shift ${shift.roundToInt()} Hz",
                fskShiftHz = shift.roundToInt(),
                audioCenterHz = center
            )
        }

        // CW: un tono stretto dominante che si accende e si spegne.
        val toneShare = strongest?.let { shareBetween(power, binHz, it.frequencyHz - 50, it.frequencyHz + 50) / total } ?: 0.0
        if (strongest != null && strongest.snrDb > 12 && strongest.widthHz < 120 && toneShare > MIN_CW_SHARE) {
            val keying = keyingRate()
            if (keying != null && keying in 1.0..40.0) {
                val confidence = (0.6 + strongest.power / total * 0.6).coerceAtMost(0.95)
                return ModeSuggestion(DigitalMode.CW, "CW", confidence, "tono ${strongest.frequencyHz.roundToInt()} Hz", audioCenterHz = strongest.frequencyHz)
            }
        }

        // FAX: energia concentrata tra 1500 e 2300 Hz (in USB a frequenza pubblicata − 1,9 kHz).
        val faxShare = shareBetween(power, binHz, 1450.0, 2350.0) / total
        val voiceShare = shareBetween(power, binHz, 300.0, 1400.0) / total
        if (context.modulation == ModulationMode.USB && faxShare > 0.75 && voiceShare < 0.15 && 10 * log10(total / band.size / noise) > 3) {
            val confidence = (0.6 + (faxShare - 0.75) + if (frequencyHint == DigitalMode.FAX) 0.2 else 0.0).coerceAtMost(0.95)
            return ModeSuggestion(DigitalMode.FAX, "Fax meteo", confidence, "energia 1500–2300 Hz")
        }

        // Portante singola stabile e stretta: PSK31 se larga ~30–80 Hz, altrimenti nessun suggerimento.
        if (strongest != null && strongest.snrDb > 15 && strongest.widthHz in 25.0..90.0 && peaks.size <= 2) {
            val keying = keyingRate()
            if (keying == null || keying < 1.0) {
                return ModeSuggestion(null, "PSK31 (nessun decoder)", 0.5, "portante modulata ~${strongest.widthHz.roundToInt()} Hz")
            }
        }

        return null
    }

    internal data class Peak(val frequencyHz: Double, val snrDb: Double, val widthHz: Double, val power: Double)

    private fun findPeaks(power: DoubleArray, first: Int, last: Int, noise: Double, binHz: Double): List<Peak> {
        val peaks = ArrayList<Peak>()
        for (k in first..last) {
            val p = power[k]
            if (p <= power[k - 1] || p < power[k + 1]) continue
            val snr = 10 * log10(p / noise)
            if (snr < PEAK_MIN_SNR_DB) continue
            var left = k
            while (left > first && power[left] > p / 4) left--
            var right = k
            while (right < last && power[right] > p / 4) right++
            val width = (right - left) * binHz
            var energy = 0.0
            for (j in left..right) energy += power[j]
            peaks += Peak(k * binHz, snr, width, energy)
        }
        // Solo i picchi principali: un picco entro [CLUSTER_HZ] da uno più forte è un lobo dello stesso segnale.
        val kept = ArrayList<Peak>()
        for (peak in peaks.sortedByDescending { it.snrDb }) {
            if (kept.none { abs(it.frequencyHz - peak.frequencyHz) < CLUSTER_HZ }) kept += peak
            if (kept.size == 12) break
        }
        return kept
    }

    /** Coppia di toni con uno shift tipico e livelli simili: restituisce centro e shift. */
    private fun fskPair(peaks: List<Peak>): Pair<Double, Double>? {
        val strong = peaks.filter { it.snrDb > 12 }
        var best: Pair<Double, Double>? = null
        var bestScore = 0.0
        for (i in strong.indices) for (j in i + 1 until strong.size) {
            val a = strong[i]
            val b = strong[j]
            val shift = abs(a.frequencyHz - b.frequencyHz)
            val nominal = FSK_SHIFTS.firstOrNull { abs(it - shift) <= maxOf(12.0, it * 0.05) } ?: continue
            if (abs(a.snrDb - b.snrDb) > 8) continue
            val score = minOf(a.snrDb, b.snrDb)
            if (score > bestScore) {
                bestScore = score
                best = (a.frequencyHz + b.frequencyHz) / 2 to nominal
            }
        }
        val (center, shift) = best ?: return null
        // La coppia deve dominare: nessun altro picco paragonabile (esclude armoniche della voce e bande affollate).
        val tones = listOf(center - shift / 2, center + shift / 2)
        val pairSnr = strong.filter { p -> tones.any { abs(p.frequencyHz - it) < 25 } }.minOfOrNull { it.snrDb } ?: return null
        val rivals = strong.count { p -> tones.none { abs(p.frequencyHz - it) < 25 } && p.snrDb > pairSnr - 6 }
        return if (rivals == 0) best else null
    }

    /** Transizioni on/off al secondo dell'inviluppo del tono più forte, null se non c'è un inviluppo utile. */
    private fun keyingRate(): Double? {
        if (envelope.size < 50) return null
        val sorted = envelope.sorted()
        val lowLevel = sorted[sorted.size / 10]
        val highLevel = sorted[sorted.size * 9 / 10]
        if (highLevel < lowLevel * 8) return 0.0 // inviluppo costante: portante, non CW
        val threshold = kotlin.math.sqrt(lowLevel * highLevel)
        var transitions = 0
        var on = envelope[0] > threshold
        for (v in envelope) {
            val now = v > threshold
            if (now != on) {
                transitions++
                on = now
            }
        }
        return transitions / (envelope.size / 100.0)
    }

    private fun shareBetween(power: DoubleArray, binHz: Double, lowHz: Double, highHz: Double): Double {
        val a = (lowHz / binHz).toInt().coerceIn(0, power.size - 1)
        val b = (highHz / binHz).toInt().coerceIn(0, power.size - 1)
        var sum = 0.0
        for (k in a..b) sum += power[k]
        return sum
    }

    companion object {
        private const val PEAK_MIN_SNR_DB = 8.0
        private val FSK_SHIFTS = listOf(170.0, 200.0, 425.0, 450.0, 850.0)
        private const val MIN_FSK_SHARE = 0.35
        private const val MIN_CW_SHARE = 0.4
        private const val CLUSTER_HZ = 40.0

        /** Modo suggerito dalla sola frequenza (allocazioni note), null se nessuna. */
        fun frequencyContext(frequencyKhz: Double): DigitalMode? {
            fun near(target: Double, tolerance: Double) = abs(frequencyKhz - target) <= tolerance
            return when {
                listOf(518.0, 490.0, 4209.5).any { near(it, 0.3) } -> DigitalMode.NAVTEX
                listOf(2187.5, 4207.5, 6312.0, 8414.5, 12577.0, 16804.5).any { near(it, 0.3) } -> DigitalMode.DSC
                listOf(1840.0, 3573.0, 5357.0, 7074.0, 10136.0, 14074.0, 18100.0, 21074.0, 24915.0, 28074.0).any { near(it, 3.0) } -> DigitalMode.FT8
                listOf(3575.5, 7047.5, 10140.0, 14080.0, 18104.0, 21140.0, 24919.0, 28180.0).any { near(it, 3.0) } -> DigitalMode.FT4
                listOf(474.2, 1836.6, 3568.6, 3592.6, 5287.2, 7038.6, 10138.7, 14095.6, 18104.6, 21094.6, 24924.6, 28124.6).any { near(it, 1.0) } -> DigitalMode.WSPR
                listOf(3855.0, 7880.0, 13882.5, 2618.5, 4610.0, 8040.0, 11086.5, 6340.5, 9110.0, 8105.0).any { near(it, 2.5) } -> DigitalMode.FAX
                else -> null
            }
        }
    }
}
