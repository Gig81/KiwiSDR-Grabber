package com.grabber.decoder.fsk

import com.grabber.decoder.DecoderSink
import com.grabber.model.digital.DecodedItem
import com.grabber.model.digital.DecoderMetrics
import com.grabber.model.digital.DigitalModeParams
import com.grabber.model.digital.DscCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** DSC su segnali sintetici costruiti secondo ITU-R M.493 (fasatura, posizioni DX/RX, ECC). */
class DscDecoderTests {
    private val rate = 12000

    /** Sequenza di simboli di una chiamata individuale con i campi indicati. */
    private fun callSymbols(
        format: Int,
        to: List<Int>,
        category: Int,
        from: List<Int>,
        cmd1: Int,
        cmd2: Int,
        freq1: List<Int>,
        freq2: List<Int>,
        corruptDx: Set<Int> = emptySet()
    ): List<Int> {
        val fields = to + category + from + cmd1 + cmd2 + freq1 + freq2 + DscSymbols.EOS
        val ecc = fields.fold(format) { acc, v -> acc xor v }
        val dx = HashMap<Int, Int>()
        dx[12] = format
        dx[14] = format
        (fields + ecc + DscSymbols.EOS + DscSymbols.EOS).forEachIndexed { k, v -> dx[16 + 2 * k] = v }
        val positions = IntArray(62)
        for (p in 0 until 62) {
            positions[p] = when {
                p < 12 -> if (p % 2 == 0) DscSymbols.DX else DscSymbols.RX7 - p / 2
                p == 13 -> 105
                p == 15 -> 104
                p % 2 == 0 -> dx.getValue(p)
                else -> dx.getValue(p - 5)
            }
        }
        return positions.mapIndexed { p, v ->
            val symbol = DscSymbols.encode(v)
            if (p in corruptDx) symbol xor 0x01 else symbol
        }
    }

    private fun bits(symbols: List<Int>): List<Int> {
        val out = ArrayList<Int>()
        repeat(200) { out += it % 2 } // punteggiatura per il clock
        for (s in symbols) for (b in 0 until 10) out += (s shr b) and 1
        repeat(40) { out += it % 2 }
        return out
    }

    private fun decode(symbols: List<Int>): List<DscCall> {
        val signal = FskSignalGenerator(rate).modulate(bits(symbols), 100.0, 500.0, 170.0, markHigh = false, leadInSeconds = 1.2)
        val calls = ArrayList<DscCall>()
        val sink = object : DecoderSink {
            override fun add(item: DecodedItem) { if (item is DscCall) calls += item }
            override fun appendText(text: String) = Unit
            override fun status(text: String) = Unit
            override fun skimmer(audioHz: Int, frequencyKhz: Double, wpm: Int, char: Char) = Unit
            override fun metrics(update: (DecoderMetrics) -> DecoderMetrics) = Unit
            override fun newImage(width: Int, title: String) = Unit
            override fun imageRow(pixels: IntArray) = Unit
            override fun imageCompleted() = Unit
        }
        val decoder = DscDecoder(DigitalModeParams())
        var i = 0
        while (i < signal.size) {
            val n = minOf(512, signal.size - i)
            decoder.process(signal.copyOfRange(i, i + n), n, rate, sink)
            i += n
        }
        return calls
    }

    private val shipTo = listOf(24, 71, 23, 45, 60)      // MMSI 247123456
    private val shipFrom = listOf(23, 51, 23, 45, 70)    // MMSI 235123457

    @Test
    fun symbolCheckBitsCountZeros() {
        assertTrue(DscSymbols.isValid(DscSymbols.encode(125)))
        assertTrue(!DscSymbols.isValid(DscSymbols.encode(125) xor 0x01))
        assertEquals(127, DscSymbols.value(DscSymbols.encode(127)))
    }

    @Test
    fun individualCallIsDecoded() {
        val calls = decode(callSymbols(120, shipTo, 100, shipFrom, 100, 126, listOf(0, 41, 25), listOf(0, 41, 25)))
        val call = calls.single()
        assertEquals("Stazione individuale", call.format)
        assertEquals("Ordinaria", call.category)
        assertEquals("235123457", call.selfId)
        assertEquals("247123456", call.address)
        assertTrue("fonia F3E/G3E" in call.details, call.details)
        assertTrue("412,5 kHz" in call.details, call.details)
        assertTrue("ECC" !in call.details, call.details)
    }

    @Test
    fun corruptedDxSymbolsAreRecoveredFromTheRxCopy() {
        val calls = decode(callSymbols(120, shipTo, 108, shipFrom, 109, 126, listOf(12, 34, 56), listOf(12, 34, 56), corruptDx = setOf(18, 26, 30)))
        val call = calls.single()
        assertEquals("Sicurezza", call.category)
        assertEquals("247123456", call.address)
        assertEquals("235123457", call.selfId)
        assertTrue(call.errorPercent > 0)
    }

    @Test
    fun frequencyTextUsesHundredsOfHertz() {
        assertEquals("2182,0 kHz", DscDecoder.frequencyText("021820"))
        assertEquals(null, DscDecoder.frequencyText("900000"))
    }
}
