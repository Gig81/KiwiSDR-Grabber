package com.grabber.probe

import com.grabber.decoder.DecoderSink
import com.grabber.decoder.fsk.AsyncBitDecoder
import com.grabber.decoder.fsk.FskDemodulator
import com.grabber.decoder.fsk.Ita2Decoder
import com.grabber.decoder.fsk.SitorBDecoder
import com.grabber.decoder.fsk.SyncBitClock
import com.grabber.decoder.fsk.NavtexDecoder
import com.grabber.decoder.fsk.RttyDecoder
import com.grabber.model.digital.DecodedItem
import com.grabber.model.digital.DecoderMetrics
import com.grabber.model.digital.DigitalModeParams
import com.grabber.model.digital.FskFraming
import com.grabber.model.digital.FskParams
import com.grabber.model.digital.FskPreset
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Verifica dei decoder locali su registrazioni WAV reali (fase 5), fuori dai test automatici.
 * Avvio: `.\gradlew.bat :shared:extProbe -PprobeMain=com.grabber.probe.FskWavCheckKt -PprobeArgs="navtex <file.wav>"`
 * Modi: `navtex`, `navtexraw` (flusso di caratteri), `rtty <centro> <shift> <baud> <inv 0/1> <stop>`.
 */
fun main(args: Array<String>) {
    val mode = args[0]
    val bytes = File(args[1]).readBytes()
    val rate = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
    val n = (bytes.size - 44) / 2
    val buf = ByteBuffer.wrap(bytes, 44, n * 2).order(ByteOrder.LITTLE_ENDIAN)
    val samples = FloatArray(n) { buf.short / 32768f }
    println("WAV ${args[1]}: $rate Hz, ${n / rate} s")
    when (mode) {
        "navtexraw" -> {
            val demod = FskDemodulator(rate, 500.0, 170.0, 100.0, markIsLower = args.getOrNull(2) != "hi")
            val clock = SyncBitClock(demod.samplesPerBit)
            val sitor = SitorBDecoder()
            val out = StringBuilder()
            var k = 0
            var ones = 0; var bitsN = 0
            for (s in samples) {
                k++
                if (k % (rate * 5) == 0) { println("t=${k / rate}s livello=${"%.2f".format(demod.level)} afc=${"%.1f".format(demod.afcOffsetHz)} sync=${sitor.isSynchronized} bit1=${if (bitsN > 0) ones * 100 / bitsN else 0}%"); ones = 0; bitsN = 0 }
                val bit = clock.process(demod.process(s)) ?: continue
                ones += bit; bitsN++
                sitor.process(bit)?.let { out.append(it) }
            }
            println(out)
            println("caratteri=${sitor.decodedChars} errori=${sitor.errorChars} afc=${demod.afcOffsetHz}")
        }
        "scan" -> {
            // Energia media per frequenza (Goertzel su blocchi di 1 s), passo 25 Hz.
            for (f in 300..2700 step 25) {
                var e = 0.0
                var blocks = 0
                var start = 0
                while (start + rate <= samples.size) {
                    val w = 2 * Math.PI * f / rate
                    val c = 2 * Math.cos(w)
                    var s1 = 0.0; var s2 = 0.0
                    for (j in start until start + rate) { val s0 = samples[j] + c * s1 - s2; s2 = s1; s1 = s0 }
                    e += s1 * s1 + s2 * s2 - c * s1 * s2
                    blocks++
                    start += rate * 5
                }
                println("$f Hz ${"%.1f".format(10 * Math.log10(e / blocks + 1e-9))} dB")
            }
        }
        "navtex" -> runDecoder(NavtexDecoder(DigitalModeParams()), samples, rate)
        "rtty" -> {
            val p = FskParams(FskPreset.CUSTOM, args[3].toInt(), args[4].toDouble(), args[5] == "1",
                FskFraming.entries.first { it.label == args[6] }, args[2].toInt())
            runDecoder(RttyDecoder(p), samples, rate)
        }
    }
}

private fun runDecoder(decoder: com.grabber.decoder.LocalDecoder, samples: FloatArray, rate: Int) {
    val text = StringBuilder()
    var metrics = DecoderMetrics()
    val sink = object : DecoderSink {
        override fun add(item: DecodedItem) { println("\n=== MESSAGGIO ===\n${item.plainText}\n") }
        override fun appendText(t: String) { text.append(t) }
        override fun status(t: String) = println("[stato] $t")
        override fun skimmer(audioHz: Int, frequencyKhz: Double, wpm: Int, char: Char) = Unit
        override fun metrics(update: (DecoderMetrics) -> DecoderMetrics) { metrics = update(metrics) }
        override fun newImage(width: Int, title: String) = Unit
        override fun imageRow(pixels: IntArray) = Unit
        override fun imageCompleted() = Unit
    }
    val block = 512
    var i = 0
    var lastReport = 0
    while (i < samples.size) {
        val c = minOf(block, samples.size - i)
        decoder.process(samples.copyOfRange(i, i + c), c, rate, sink)
        i += c
        if (i - lastReport >= rate * 10) { lastReport = i; println("[${i / rate}s] $metrics") }
    }
    println(text)
}
