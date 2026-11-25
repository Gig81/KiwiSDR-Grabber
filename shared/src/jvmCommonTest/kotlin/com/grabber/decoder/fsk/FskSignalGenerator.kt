package com.grabber.decoder.fsk

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Generatore di segnali FSK di prova (solo test): ITA2 asincrono e SITOR-B FEC, con rumore. */
class FskSignalGenerator(
    private val sampleRate: Int = 12000,
    private val random: Random = Random(1234)
) {
    private var phase = 0.0

    /** Toni a [centerHz] ± shift/2; [markHigh] mette il mark sul tono alto. */
    fun modulate(
        bits: List<Int>,
        baud: Double,
        centerHz: Double,
        shiftHz: Double,
        markHigh: Boolean = true,
        amplitude: Double = 0.3,
        noiseRms: Double = 0.0,
        leadInSeconds: Double = 0.5
    ): FloatArray {
        val samplesPerBit = sampleRate / baud
        val lead = (leadInSeconds * sampleRate).toInt()
        val total = lead + (bits.size * samplesPerBit).toInt() + lead
        val out = FloatArray(total)
        var bitPosition = 0.0
        for (i in 0 until total) {
            val bit = if (i < lead) 1 else bits.getOrElse(((i - lead) / samplesPerBit).toInt()) { 1 }
            val high = (bit == 1) == markHigh
            val freq = centerHz + if (high) shiftHz / 2 else -shiftHz / 2
            phase += 2 * PI * freq / sampleRate
            if (phase > 2 * PI) phase -= 2 * PI
            val noise = if (noiseRms > 0) random.nextGaussian() * noiseRms else 0.0
            out[i] = (amplitude * sin(phase) + noise).toFloat()
            bitPosition += 1.0
        }
        return out
    }

    private fun Random.nextGaussian(): Double {
        // Box-Muller
        val u1 = nextDouble().coerceAtLeast(1e-12)
        val u2 = nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2 * PI * u2)
    }

    companion object {
        /** Bit ITA2 asincroni (start 0, 5 dati dal meno significativo, stop 1 per [stopBits]). */
        fun ita2Bits(text: String, stopBits: Double): List<Int> {
            val letters = HashMap<Char, Int>()
            val figures = HashMap<Char, Int>()
            for (code in 0..31) {
                Ita2Decoder(unshiftOnSpace = false).also { it.decode(Ita2Decoder.LTRS) }.decode(code)?.let { letters.putIfAbsent(it, code) }
                Ita2Decoder(unshiftOnSpace = false).also { it.decode(Ita2Decoder.FIGS) }.decode(code)?.let { figures.putIfAbsent(it, code) }
            }
            val codes = ArrayList<Int>()
            var inFigures = false
            codes += Ita2Decoder.LTRS
            codes += Ita2Decoder.LTRS
            for (ch in text) {
                val letter = letters[ch]
                val figure = figures[ch]
                when {
                    letter != null && (!inFigures || figure == null || ch == ' ' || ch == '\n' || ch == '\r') -> {
                        if (inFigures && ch != ' ' && ch != '\n' && ch != '\r') { codes += Ita2Decoder.LTRS; inFigures = false }
                        codes += letter
                    }
                    figure != null -> {
                        if (!inFigures) { codes += Ita2Decoder.FIGS; inFigures = true }
                        codes += figure
                    }
                }
            }
            // Risultato in mezzi bit (da modulare a baud × 2), così lo stop da 1,5 bit è esatto.
            val stopHalves = (stopBits * 2).toInt()
            val halves = ArrayList<Int>()
            for (code in codes) {
                repeat(2) { halves += 0 }
                for (b in 0 until 5) repeat(2) { halves += (code shr b) and 1 }
                repeat(stopHalves) { halves += 1 }
            }
            return halves
        }

        /**
         * Sequenza di caratteri SITOR-B FEC: fasatura, poi ogni carattere in posizione RX e, cinque
         * posizioni dopo, in DX. [corrupt] altera alcune copie: (indice carattere, true = copia DX).
         */
        fun sitorBits(text: String, phasingPairs: Int = 12, corrupt: Set<Pair<Int, Boolean>> = emptySet()): List<Int> {
            val reverseLetters = HashMap<Char, Int>()
            val reverseFigures = HashMap<Char, Int>()
            for (code in 0 until 128) {
                if (!Ccir476.isValid(code)) continue
                Ccir476.charOf(code, false)?.let { reverseLetters.putIfAbsent(it, code) }
                Ccir476.charOf(code, true)?.let { reverseFigures.putIfAbsent(it, code) }
            }
            val chars = ArrayList<Int>()
            var figures = false
            chars += Ccir476.LETTERS
            for (ch in text) {
                val l = reverseLetters[ch]
                val f = reverseFigures[ch]
                if (l != null && (!figures || f == null || ch == ' ' || ch == '\n' || ch == '\r')) {
                    if (figures && ch != ' ' && ch != '\n' && ch != '\r') { chars += Ccir476.LETTERS; figures = false }
                    chars += l
                } else if (f != null) {
                    if (!figures) { chars += Ccir476.FIGURES; figures = true }
                    chars += f
                }
            }
            val slots = ArrayList<Int>()
            val n = chars.size
            for (k in 0 until n + phasingPairs + 3) {
                val rxIndex = k - phasingPairs
                val dxIndex = k - phasingPairs - 2
                var rx = if (rxIndex in 0 until n) chars[rxIndex] else Ccir476.REP
                var dx = if (dxIndex in 0 until n) chars[dxIndex] else Ccir476.ALPHA
                if (rxIndex in 0 until n && (rxIndex to false) in corrupt) rx = rx xor 0x01
                if (dxIndex in 0 until n && (dxIndex to true) in corrupt) dx = dx xor 0x01
                slots += rx
                slots += dx
            }
            val bits = ArrayList<Int>()
            for (code in slots) for (b in 0 until 7) bits += (code shr b) and 1
            return bits
        }
    }
}
