package com.grabber.decoder.server

import com.grabber.decoder.DecoderSink
import com.grabber.kiwi.ExtProtocol
import com.grabber.kiwi.ext.ExtDataFrame
import com.grabber.kiwi.ext.ExtMessageParser
import com.grabber.kiwi.ext.ExtTextMessage
import com.grabber.model.digital.DigitalMode
import com.grabber.model.digital.DigitalModeParams

/**
 * Adattatore di un'estensione del server: comandi di avvio e arresto e interpretazione dei risultati.
 * Le chiamate arrivano in ordine da un solo flusso.
 */
interface ServerDecoderSession {
    val mode: DigitalMode
    val extensionName: String get() = requireNotNull(mode.extensionName)
    fun startCommands(): List<String>
    fun stopCommands(): List<String>
    fun onText(message: ExtTextMessage, sink: DecoderSink, clock: () -> Long)
    fun onData(frame: ExtDataFrame, sink: DecoderSink) = Unit

    /** Comandi da inviare quando cambiano i parametri con il decoder attivo (vuoto = riavvio non necessario). */
    fun paramsChangedCommands(params: DigitalModeParams): List<String> = emptyList()
}

/**
 * Crea l'adattatore per [mode]. [centerKhz] è la frequenza pubblicata scelta (serve a WSPR),
 * [carrierKhz] la portante corrente (serve allo skimmer per le frequenze assolute).
 */
fun serverSessionFor(
    mode: DigitalMode,
    params: DigitalModeParams,
    centerKhz: Double,
    carrierKhz: () -> Double
): ServerDecoderSession? = when (mode) {
    DigitalMode.FT8 -> Ft8Session(DigitalMode.FT8, 0)
    DigitalMode.FT4 -> Ft8Session(DigitalMode.FT4, 1)
    DigitalMode.WSPR -> WsprSession(centerKhz)
    DigitalMode.CW -> CwSession(params)
    DigitalMode.CW_SKIMMER -> CwSkimmerSession(params, carrierKhz)
    DigitalMode.FAX -> FaxSession(params)
    DigitalMode.SSTV -> SstvSession(params)
    DigitalMode.HFDL -> TextStreamSession(DigitalMode.HFDL, listOf(ExtProtocol.Hfdl.START, ExtProtocol.Hfdl.displayFrequency(centerKhz)), listOf(ExtProtocol.Hfdl.STOP))
    DigitalMode.ALE -> TextStreamSession(DigitalMode.ALE, listOf(ExtProtocol.Ale.START), listOf(ExtProtocol.Ale.STOP))
    DigitalMode.RTTY, DigitalMode.NAVTEX, DigitalMode.DSC -> null
}

private fun ExtTextMessage.decoded(key: String): String? = value(key)?.let(ExtMessageParser::decodeComponent)

/** FT8/FT4: righe `chars=` con intestazioni, decodifiche e riepilogo del ciclo. */
class Ft8Session(override val mode: DigitalMode, private val protocol: Int) : ServerDecoderSession {
    override fun startCommands() = listOf(ExtProtocol.Ft8.start(protocol), ExtProtocol.Ft8.protocol(protocol))
    override fun stopCommands() = listOf(ExtProtocol.Ft8.CLOSE)

    override fun onText(message: ExtTextMessage, sink: DecoderSink, clock: () -> Long) {
        val chars = message.decoded("chars") ?: return
        for (raw in chars.split('\n')) {
            val line = ExtMessageParser.stripMarkup(raw).trimEnd()
            if (line.isBlank()) continue
            val parsed = ExtMessageParser.parseFt8Line(line, clock())
            when {
                parsed != null -> sink.add(parsed)
                " decoded " in line -> sink.metrics { it.copy(status = line.substringAfter(' ').trim()) }
                line.trimStart().startsWith("UTC") -> Unit
                "new freq" in line -> sink.status(line.trim('-', ' '))
                else -> sink.status(line.trim())
            }
        }
    }
}

/** WSPR: spot in `WSPR_DECODED`, stato del ciclo in `WSPR_STATUS`. `WSPR_UPLOAD` è ignorato (nessun caricamento). */
class WsprSession(private val centerKhz: Double) : ServerDecoderSession {
    override val mode = DigitalMode.WSPR

    override fun startCommands() = listOf(
        ExtProtocol.Wspr.bfo(ExtProtocol.Wspr.DEFAULT_BFO_HZ),
        ExtProtocol.Wspr.STACK_DECODER_OFF,
        ExtProtocol.Wspr.frequencies(centerKhz),
        ExtProtocol.Wspr.capture(true)
    )

    override fun stopCommands() = listOf(ExtProtocol.Wspr.capture(false))

    override fun onText(message: ExtTextMessage, sink: DecoderSink, clock: () -> Long) {
        message.decoded("WSPR_DECODED")?.let { line ->
            ExtMessageParser.parseWsprSpot(line, clock())?.let(sink::add)
        }
        message.value("WSPR_STATUS")?.toIntOrNull()?.let { status ->
            val text = when (status) {
                1 -> "In attesa del prossimo ciclo pari"
                2 -> "Sincronizzazione"
                3 -> "Acquisizione del ciclo (2 minuti)"
                4 -> "Decodifica in corso"
                else -> null
            }
            sink.metrics { it.copy(status = text) }
        }
    }
}

/** CW singolo segnale: testo in `cw_chars`, velocità in `cw_wpm`. */
class CwSession(private val params: DigitalModeParams, private val passbandOffsetHz: Int = 500) : ServerDecoderSession {
    override val mode = DigitalMode.CW

    override fun startCommands(): List<String> = buildList {
        add(ExtProtocol.Cw.start())
        addAll(paramsChangedCommands(params))
    }

    override fun paramsChangedCommands(params: DigitalModeParams): List<String> = buildList {
        add(ExtProtocol.Cw.wpm(if (params.cwAutoWpm) 0 else params.cwWpm))
        // L'impostazione della velocità reinizializza il decoder: la soglia va inviata subito dopo.
        add(ExtProtocol.Cw.autoThreshold(params.cwAutoThreshold))
        if (!params.cwAutoThreshold) add(ExtProtocol.Cw.threshold(params.cwThresholdDb))
        add(ExtProtocol.Cw.wordSpaceCorrection(params.cwWordSpaceCorrection))
        // cw_wpm reinizializza il decoder e ne azzera l'offset: cw_pboff va sempre inviato per ultimo
        // (senza, il server non elabora i campioni: verificato nella fase 0).
        add(ExtProtocol.Cw.passbandOffset(passbandOffsetHz))
    }

    override fun stopCommands() = listOf(ExtProtocol.Cw.STOP)

    override fun onText(message: ExtTextMessage, sink: DecoderSink, clock: () -> Long) {
        message.decoded("cw_chars")?.let(sink::appendText)
        message.value("cw_wpm")?.toIntOrNull()?.let { wpm -> sink.metrics { it.copy(wpm = wpm) } }
        message.value("cw_train")?.toIntOrNull()?.let { train ->
            val status = if (train < 0) "Errore di apprendimento ${-train}/4" else if (train > 0) "Apprendimento $train/${ExtProtocol.Cw.DEFAULT_TRAINING}" else null
            sink.metrics { it.copy(status = status) }
        }
    }
}

/** CW Skimmer: un carattere per volta per segnale, `cw_chars=<car><Hz>,<wpm>`. */
class CwSkimmerSession(private val params: DigitalModeParams, private val carrierKhz: () -> Double) : ServerDecoderSession {
    override val mode = DigitalMode.CW_SKIMMER
    override fun startCommands() = listOf(ExtProtocol.CwSkimmer.START) + paramsChangedCommands(params)
    override fun paramsChangedCommands(params: DigitalModeParams) =
        listOf(ExtProtocol.CwSkimmer.params(params.skimmerPowerCalc, params.skimmerFilterNeighbors))
    override fun stopCommands() = listOf(ExtProtocol.CwSkimmer.STOP)

    override fun onText(message: ExtTextMessage, sink: DecoderSink, clock: () -> Long) {
        val decoded = message.decoded("cw_chars") ?: return
        val char = ExtMessageParser.parseSkimmerChar(decoded) ?: return
        // Frequenza mostrata con 100 Hz di risoluzione, come il client web.
        val audioRounded = (char.audioHz / 100) * 100
        sink.skimmer(char.audioHz, carrierKhz() + audioRounded / 1000.0, char.wpm, char.char)
    }
}

/** Fax meteo: righe `DAT` di 1024 pixel in scala di grigi (comando 254), 255 = nuova pagina. */
class FaxSession(private val params: DigitalModeParams) : ServerDecoderSession {
    override val mode = DigitalMode.FAX

    companion object {
        const val CMD_DRAW = 254
        const val CMD_CLEAR = 255
        const val WIDTH = 1024
    }

    override fun startCommands() = listOf(ExtProtocol.Fax.start(params.faxLpm, params.faxPhasing, params.faxAutoStop))
    override fun stopCommands() = listOf(ExtProtocol.Fax.STOP)

    override fun onText(message: ExtTextMessage, sink: DecoderSink, clock: () -> Long) {
        if (message.has("fax_phased")) sink.metrics { it.copy(status = "Fasatura completata") }
        message.value("fax_autostopped")?.let { value ->
            if (value == "1") {
                sink.metrics { it.copy(status = "Fermato automaticamente a fine carta") }
                sink.imageCompleted()
            } else {
                sink.metrics { it.copy(status = "In ricezione") }
            }
        }
    }

    override fun onData(frame: ExtDataFrame, sink: DecoderSink) {
        when (frame.command) {
            CMD_CLEAR -> sink.newImage(WIDTH, "Fax")
            CMD_DRAW -> {
                val count = minOf(WIDTH, frame.payload.size)
                val row = IntArray(count) { i ->
                    val v = frame.payload[i].toInt() and 0xFF
                    (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                }
                sink.imageRow(row)
            }
        }
    }
}

/**
 * SSTV: `DAT` comando 0/1 con SNR, larghezza e altezza (u16 big-endian) e una riga RGB;
 * `img_size`, `new_img`, `mode_name`, `status`, `fsk_id` come messaggi.
 */
class SstvSession(private val params: DigitalModeParams) : ServerDecoderSession {
    override val mode = DigitalMode.SSTV
    private var modeName: String = ""

    override fun startCommands() = listOf(ExtProtocol.Sstv.START, ExtProtocol.Sstv.autoAdjust(params.sstvAutoAdjust))
    override fun stopCommands() = listOf(ExtProtocol.Sstv.STOP)
    override fun paramsChangedCommands(params: DigitalModeParams) = listOf(ExtProtocol.Sstv.autoAdjust(params.sstvAutoAdjust))

    override fun onText(message: ExtTextMessage, sink: DecoderSink, clock: () -> Long) {
        message.decoded("mode_name")?.let { name ->
            modeName = name
            sink.metrics { it.copy(status = name) }
        }
        message.decoded("new_img")?.let { name ->
            if (name.isNotBlank()) modeName = name
            sink.newImage(320, modeName.ifBlank { "SSTV" })
        }
        message.decoded("status")?.let { status -> sink.metrics { it.copy(status = listOf(modeName, status).filter { s -> s.isNotBlank() }.joinToString(" · ")) } }
        message.decoded("result")?.let { result -> if (result.isNotBlank()) sink.status(result) }
        message.decoded("fsk_id")?.let { id -> if (id.isNotBlank()) sink.status("ID FSK: $id") }
    }

    override fun onData(frame: ExtDataFrame, sink: DecoderSink) {
        if (frame.command != 0 && frame.command != 1) return
        val p = frame.payload
        if (p.size < 5) return
        val width = ((p[1].toInt() and 0xFF) shl 8) or (p[2].toInt() and 0xFF)
        val pixels = minOf(width, (p.size - 5) / 3)
        if (pixels <= 0) return
        val row = IntArray(pixels) { i ->
            val o = 5 + i * 3
            (0xFF shl 24) or ((p[o].toInt() and 0xFF) shl 16) or ((p[o + 1].toInt() and 0xFF) shl 8) or (p[o + 2].toInt() and 0xFF)
        }
        sink.imageRow(row)
    }
}

/** Estensioni che producono testo in `chars=` (HFDL, ALE). */
class TextStreamSession(
    override val mode: DigitalMode,
    private val start: List<String>,
    private val stop: List<String>
) : ServerDecoderSession {
    override fun startCommands() = start
    override fun stopCommands() = stop

    override fun onText(message: ExtTextMessage, sink: DecoderSink, clock: () -> Long) {
        message.decoded("chars")?.let { sink.appendText(ExtMessageParser.stripMarkup(it)) }
    }
}
