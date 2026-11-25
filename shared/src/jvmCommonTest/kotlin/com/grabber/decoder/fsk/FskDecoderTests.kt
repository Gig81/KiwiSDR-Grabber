package com.grabber.decoder.fsk

import com.grabber.decoder.DecoderSink
import com.grabber.decoder.LocalDecoder
import com.grabber.model.digital.DecodedItem
import com.grabber.model.digital.DecoderMetrics
import com.grabber.model.digital.DigitalModeParams
import com.grabber.model.digital.FskFraming
import com.grabber.model.digital.FskParams
import com.grabber.model.digital.FskPreset
import com.grabber.model.digital.NavtexMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Decoder locali FSK su segnali sintetici: testo noto, rumore, offset di frequenza, errori FEC. */
class FskDecoderTests {
    private val rate = 12000
    private val text = "RYRYRY THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG 0123456789 CQ DE IK7ABC\n"

    private class CollectingSink : DecoderSink {
        val text = StringBuilder()
        val items = ArrayList<DecodedItem>()
        var metrics = DecoderMetrics()
        override fun add(item: DecodedItem) { items += item }
        override fun appendText(text: String) { this.text.append(text) }
        override fun status(text: String) = Unit
        override fun skimmer(audioHz: Int, frequencyKhz: Double, wpm: Int, char: Char) = Unit
        override fun metrics(update: (DecoderMetrics) -> DecoderMetrics) { metrics = update(metrics) }
        override fun newImage(width: Int, title: String) = Unit
        override fun imageRow(pixels: IntArray) = Unit
        override fun imageCompleted() = Unit
    }

    private fun run(decoder: LocalDecoder, signal: FloatArray): CollectingSink {
        val sink = CollectingSink()
        var i = 0
        while (i < signal.size) {
            val n = minOf(512, signal.size - i)
            decoder.process(signal.copyOfRange(i, i + n), n, rate, sink)
            i += n
        }
        return sink
    }

    private fun rtty(params: FskParams, offsetHz: Double = 0.0, noise: Double = 0.0, markHigh: Boolean = !params.inverted, repeat: Int = 2): String {
        val halves = FskSignalGenerator.ita2Bits(text.repeat(repeat), params.framing.stopBits)
        // Lead-in di 1,2 s: il primo secondo serve all'acquisizione dei toni.
        val signal = FskSignalGenerator(rate).modulate(halves, params.baud * 2, params.centerHz + offsetHz, params.shiftHz.toDouble(), markHigh, noiseRms = noise, leadInSeconds = 1.2)
        return run(RttyDecoder(params), signal).text.toString()
    }

    private fun similarity(expected: String, actual: String): Double {
        // Quota di parole attese presenti nell'uscita.
        val words = expected.trim().split(' ', '\n').filter { it.isNotBlank() }.distinct()
        val found = actual.split(' ', '\n').toSet()
        return words.count { it in found } / words.size.toDouble()
    }

    @Test
    fun hamRttyIsDecoded() {
        val output = rtty(FskParams.forPreset(FskPreset.HAM))
        assertTrue(text.trim() in output, "uscita: $output")
    }

    @Test
    fun weatherRttyWithInversionAnd450HzShiftIsDecoded() {
        val params = FskParams.forPreset(FskPreset.DWD).copy(inverted = true)
        val output = rtty(params)
        assertTrue(text.trim() in output, "uscita: $output")
    }

    @Test
    fun rttyWithOneStopBitIsDecoded() {
        val params = FskParams(FskPreset.CUSTOM, 170, 50.0, false, FskFraming.STOP_1, 1500)
        assertTrue(text.trim() in rtty(params))
    }

    @Test
    fun rttyAcquiresAFrequencyOffset() {
        val output = rtty(FskParams.forPreset(FskPreset.HAM), offsetHz = 60.0, repeat = 3)
        assertTrue(text.trim() in output, "uscita: $output")
    }

    @Test
    fun rttySurvivesModerateNoise() {
        // Ampiezza 0,3 e rumore 0,3 RMS: SNR circa -3 dB nella banda audio di 6 kHz, ~14 dB nella banda del segnale.
        val output = rtty(FskParams.forPreset(FskPreset.HAM), noise = 0.3, repeat = 3)
        assertTrue(similarity(text, output) >= 0.7, "uscita: $output")
    }

    @Test
    fun noiseAloneProducesNoText() {
        val noise = FskSignalGenerator(rate).modulate(emptyList(), 45.45, 1000.0, 170.0, amplitude = 0.0, noiseRms = 0.3, leadInSeconds = 5.0)
        val output = run(RttyDecoder(FskParams()), noise).text.toString()
        assertTrue(output.length < 10, "uscita dal solo rumore: '$output'")
    }

    private val navtex = "ZCZC GA43\nNAVAREA I 247/26\nENGLAND, EAST COAST.\nCHART GB 1408.\nNNNN\n"

    private fun navtexSignal(message: String, corrupt: Set<Pair<Int, Boolean>> = emptySet(), offset: Double = 0.0, noise: Double = 0.0): FloatArray {
        val bits = FskSignalGenerator.sitorBits(message, corrupt = corrupt)
        return FskSignalGenerator(rate).modulate(bits, 100.0, 500.0 + offset, 170.0, markHigh = true, noiseRms = noise, leadInSeconds = 1.2)
    }

    @Test
    fun navtexMessageIsAssembled() {
        val sink = run(NavtexDecoder(DigitalModeParams()), navtexSignal(navtex))
        val message = sink.items.filterIsInstance<NavtexMessage>().single()
        assertEquals("GA43", message.id)
        assertEquals('G', message.station)
        assertEquals('A', message.type)
        assertTrue("NAVAREA I 247/26" in message.body, message.body)
        assertTrue("CHART GB 1408." in message.body, message.body)
        assertEquals(0, message.errorPercent)
    }

    @Test
    fun fecRecoversCharactersCorruptedInOneCopy() {
        val corrupt = setOf(20 to true, 21 to false, 30 to true, 31 to true, 40 to false)
        val sink = run(NavtexDecoder(DigitalModeParams()), navtexSignal(navtex, corrupt))
        val message = sink.items.filterIsInstance<NavtexMessage>().single()
        assertTrue("ENGLAND, EAST COAST." in message.body, message.body)
        assertEquals(0, message.errorPercent)
    }

    @Test
    fun charactersLostInBothCopiesAreMarked() {
        val corrupt = setOf(25 to true, 25 to false)
        val sink = run(NavtexDecoder(DigitalModeParams()), navtexSignal(navtex, corrupt))
        val message = sink.items.filterIsInstance<NavtexMessage>().single()
        assertTrue(SitorBDecoder.ERROR_CHAR in message.body, message.body)
        assertTrue(message.errorPercent > 0)
    }

    @Test
    fun duplicateMessagesAreDiscarded() {
        val decoder = NavtexDecoder(DigitalModeParams(navtexDiscardDuplicates = true))
        val signal = navtexSignal(navtex + "\n" + navtex)
        val sink = run(decoder, signal)
        assertEquals(1, sink.items.filterIsInstance<NavtexMessage>().size)
    }

    @Test
    fun navtexToleratesOffsetAndNoise() {
        val sink = run(NavtexDecoder(DigitalModeParams()), navtexSignal(navtex, offset = 25.0, noise = 0.25))
        val message = sink.items.filterIsInstance<NavtexMessage>().firstOrNull()
        assertTrue(message != null && "GA43" == message.id, "messaggi: ${sink.items}")
    }

    @Test
    fun assemblerReportsIncompleteMessages() {
        val assembler = NavtexMessageAssembler()
        "ZCZC EA12 FIRST MESSAGE TEXT ".forEach { assembler.process(it) }
        var closed: NavtexMessageAssembler.RawMessage? = null
        "ZCZC EB01 ".forEach { c -> assembler.process(c)?.let { closed = it } }
        assertEquals("EA12", closed?.header)
        assertTrue(closed?.incomplete == true)
    }
}
