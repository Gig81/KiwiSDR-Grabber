package com.grabber.decoder.fsk

import com.grabber.decoder.DecoderSink
import com.grabber.decoder.LocalDecoder
import com.grabber.decoder.LocalDecoderFactory
import com.grabber.model.digital.DigitalMode
import com.grabber.model.digital.DigitalModeCatalog
import com.grabber.model.digital.DigitalModeParams
import com.grabber.model.digital.FskParams
import com.grabber.model.digital.NavtexMessage

/** Decoder locali disponibili nell'app: RTTY/FSK, NAVTEX e DSC. */
object FskDecoders : LocalDecoderFactory {
    override fun create(mode: DigitalMode, params: DigitalModeParams): LocalDecoder? = when (mode) {
        DigitalMode.RTTY -> RttyDecoder(params.rtty)
        DigitalMode.NAVTEX -> NavtexDecoder(params)
        DigitalMode.DSC -> DscDecoder(params)
        else -> null
    }
}

/** Aggiorna gli indicatori al massimo ogni [intervalSamples] campioni. */
private class MetricsThrottle(private val intervalSamples: Int) {
    private var counter = 0
    fun due(samples: Int): Boolean {
        counter += samples
        if (counter < intervalSamples) return false
        counter = 0
        return true
    }
}

/**
 * RTTY/FSK asincrono ITA2. Il testo esce solo con segnale presente (con isteresi), così il rumore
 * non riempie l'uscita di caratteri casuali.
 */
class RttyDecoder(private var params: FskParams) : LocalDecoder {
    override val mode = DigitalMode.RTTY

    private var sampleRate = 0
    private var demodulator: FskDemodulator? = null
    private var bits: AsyncBitDecoder? = null
    private val ita2 = Ita2Decoder()
    private var throttle = MetricsThrottle(6000)
    private var recentFrames = 0
    private var recentErrors = 0
    private var squelchOpen = false

    private fun setup(rate: Int) {
        sampleRate = rate
        val p = params.normalized()
        val demod = FskDemodulator(rate, p.centerHz.toDouble(), p.shiftHz.toDouble(), p.baud, markIsLower = p.inverted)
        demodulator = demod
        bits = AsyncBitDecoder(demod.samplesPerBit, dataBits = 5, stopBits = p.framing.stopBits)
        ita2.reset()
        throttle = MetricsThrottle(rate / 2)
    }

    override fun process(samples: FloatArray, count: Int, sampleRate: Int, sink: DecoderSink) {
        if (sampleRate != this.sampleRate || demodulator == null) setup(sampleRate)
        val demod = demodulator ?: return
        val decoder = bits ?: return
        for (i in 0 until count) {
            val soft = demod.process(samples[i])
            val frame = decoder.process(soft) ?: continue
            squelchOpen = if (squelchOpen) demod.level > SQUELCH_CLOSE else demod.level > SQUELCH_OPEN
            if (!squelchOpen) continue
            recentFrames++
            if (frame.framingError) recentErrors++
            ita2.decode(frame.code)?.let { sink.appendText(it.toString()) }
        }
        if (throttle.due(count)) {
            val errorPercent = if (recentFrames > 0) recentErrors * 100 / recentFrames else null
            val sync = squelchOpen && errorPercent != null && errorPercent < 30
            sink.metrics {
                it.copy(
                    level = demod.level.toFloat(),
                    synchronized = sync,
                    errorPercent = errorPercent,
                    status = afcStatus(demod.afcOffsetHz)
                )
            }
            recentFrames = 0
            recentErrors = 0
        }
    }

    override fun reset() {
        demodulator?.reset()
        bits?.reset()
        ita2.reset()
    }

    override fun updateParams(params: DigitalModeParams): Boolean {
        if (params.rtty == this.params) return true
        this.params = params.rtty
        if (sampleRate > 0) setup(sampleRate)
        return true
    }

    private companion object {
        const val SQUELCH_OPEN = 0.3
        const val SQUELCH_CLOSE = 0.2
    }
}

/**
 * NAVTEX (SITOR-B, 100 baud, shift 170 Hz, centro audio 500 Hz). In modo CW il mark ("B") è il tono
 * più alto (verificato su registrazioni reali del 518 kHz).
 * Messaggi con filtro dei duplicati per identificativo (salvo numero 00, che va sempre mostrato).
 */
class NavtexDecoder(private var params: DigitalModeParams) : LocalDecoder {
    override val mode = DigitalMode.NAVTEX

    private var sampleRate = 0
    private var demodulator: FskDemodulator? = null
    private var clock: SyncBitClock? = null
    private val sitor = SitorBDecoder()
    private val assembler = NavtexMessageAssembler()
    private var throttle = MetricsThrottle(6000)
    private val seenIds = HashMap<String, Long>()
    private var lastErrors = 0L
    private var lastTotal = 0L
    private var silentSamples = 0L

    private fun setup(rate: Int) {
        sampleRate = rate
        val demod = FskDemodulator(
            rate,
            DigitalModeCatalog.SITOR_CENTER_HZ.toDouble(),
            DigitalModeCatalog.SITOR_SHIFT_HZ.toDouble(),
            DigitalModeCatalog.SITOR_BAUD,
            markIsLower = false
        )
        demodulator = demod
        clock = SyncBitClock(demod.samplesPerBit)
        sitor.reset()
        assembler.reset()
        throttle = MetricsThrottle(rate / 2)
    }

    override fun process(samples: FloatArray, count: Int, sampleRate: Int, sink: DecoderSink) {
        if (sampleRate != this.sampleRate || demodulator == null) setup(sampleRate)
        val demod = demodulator ?: return
        val bitClock = clock ?: return
        for (i in 0 until count) {
            val soft = demod.process(samples[i])
            val bit = bitClock.process(soft) ?: continue
            val char = sitor.process(bit) ?: continue
            assembler.process(char)?.let { publish(it, sink) }
        }
        // Dopo 60 s senza sincronismo un messaggio rimasto aperto viene chiuso come incompleto.
        silentSamples = if (sitor.isSynchronized) 0 else silentSamples + count
        if (silentSamples > sampleRate * 60L) {
            assembler.flush()?.let { publish(it, sink) }
            silentSamples = 0
        }
        if (throttle.due(count)) {
            val total = sitor.decodedChars - lastTotal
            val errors = sitor.errorChars - lastErrors
            lastTotal = sitor.decodedChars
            lastErrors = sitor.errorChars
            sink.metrics {
                it.copy(
                    level = demod.level.toFloat(),
                    synchronized = sitor.isSynchronized,
                    errorPercent = if (total > 0) (errors * 100 / total).toInt() else it.errorPercent,
                    status = afcStatus(demod.afcOffsetHz)
                )
            }
        }
    }

    private fun publish(raw: NavtexMessageAssembler.RawMessage, sink: DecoderSink) {
        val header = raw.header.uppercase()
        if (header.length < 4) return
        val id = header.take(4)
        val now = System.currentTimeMillis()
        if (params.navtexDiscardDuplicates && !id.endsWith("00") && !raw.incomplete && raw.errors == 0) {
            val previous = seenIds[id]
            seenIds[id] = now
            if (previous != null && now - previous < DUPLICATE_WINDOW_MS) return
        }
        sink.add(
            NavtexMessage(
                receivedAtMillis = now,
                station = id[0],
                type = id[1],
                number = id.substring(2, 4),
                body = raw.body,
                errorPercent = if (raw.total > 0) raw.errors * 100 / raw.total else 0,
                incomplete = raw.incomplete
            )
        )
    }

    override fun reset() {
        demodulator?.reset()
        clock?.reset()
        sitor.reset()
    }

    override fun updateParams(params: DigitalModeParams): Boolean {
        this.params = params
        return true
    }

    private companion object {
        const val DUPLICATE_WINDOW_MS = 24L * 3600 * 1000
    }
}

private fun afcStatus(offsetHz: Double): String? =
    if (kotlin.math.abs(offsetHz) >= 2.0) "AFC ${if (offsetHz > 0) "+" else ""}${offsetHz.toInt()} Hz" else null
