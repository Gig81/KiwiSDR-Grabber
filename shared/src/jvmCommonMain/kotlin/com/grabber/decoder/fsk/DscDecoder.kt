package com.grabber.decoder.fsk

import com.grabber.decoder.DecoderSink
import com.grabber.decoder.LocalDecoder
import com.grabber.model.digital.DigitalMode
import com.grabber.model.digital.DigitalModeCatalog
import com.grabber.model.digital.DigitalModeParams
import com.grabber.model.digital.DscCall

/**
 * Simboli DSC (ITU-R M.493): 10 bit, i primi 7 ricevuti sono l'informazione (il primo è il bit meno
 * significativo), gli ultimi 3 il numero di zeri dell'informazione trasmesso a partire dal bit più
 * significativo. La fasatura alterna il simbolo DX (125) ai simboli RX 111…104; ogni simbolo del
 * messaggio è trasmesso in posizione DX e ripetuto in posizione RX cinque posizioni dopo.
 */
object DscSymbols {
    const val DX = 125
    const val RX7 = 111
    const val EOS = 127
    const val ARQ = 117
    const val ABQ = 122

    /** Simbolo completo (10 bit) per il valore informativo [value]. */
    fun encode(value: Int): Int {
        val zeros = 7 - Integer.bitCount(value and 0x7F)
        val reversed = ((zeros and 1) shl 2) or (zeros and 2) or ((zeros shr 2) and 1)
        return (reversed shl 7) or (value and 0x7F)
    }

    fun isValid(symbol: Int): Boolean = encode(symbol and 0x7F) == symbol

    fun value(symbol: Int): Int = symbol and 0x7F
}

/**
 * Ricompone le chiamate DSC dai bit sincroni: aggancio sulla fasatura (DX, RX, DX), lettura delle
 * posizioni fino alla fine della sequenza e scelta per ogni campo della copia DX o RX valida.
 */
class DscCallDecoder {
    data class Call(
        val format: Int,
        val category: Int?,
        val address: String?,
        val selfId: String?,
        val command1: Int?,
        val command2: Int?,
        val frequency1: String?,
        val frequency2: String?,
        val errors: Int,
        val symbols: Int,
        val eccOk: Boolean
    )

    private val window = IntArray(3)
    private var bits = 0
    private var register = 0
    private var synced = false
    private var position = 0
    private var bitInSymbol = 0
    private val received = HashMap<Int, Int>()

    /** Elabora un bit (1 = Y); restituisce una chiamata completa quando termina la sequenza. */
    fun process(bit: Int): Call? {
        register = (register ushr 1) or (bit shl 9)
        if (!synced) {
            bits++
            if (bits < 30) {
                shiftWindow()
                return null
            }
            shiftWindow()
            checkSync()
            return null
        }
        if (++bitInSymbol < 10) return null
        bitInSymbol = 0
        received[position] = register
        position++
        return if (isComplete()) finish() else null
    }

    // La finestra contiene gli ultimi tre simboli allineati alla posizione di bit corrente.
    private val history = IntArray(30)
    private var historyIndex = 0

    private fun shiftWindow() {
        history[historyIndex] = register
        historyIndex = (historyIndex + 1) % 30
    }

    private fun checkSync() {
        // Simboli che terminano 20, 10 e 0 bit fa.
        val s2 = register
        val s1 = history[(historyIndex + 19) % 30]
        val s0 = history[(historyIndex + 9) % 30]
        if (s0 != DX_W || s2 != DX_W) return
        val rx = DscSymbols.value(s1)
        if (!DscSymbols.isValid(s1) || rx !in 107..DscSymbols.RX7) return
        // RX7 è in posizione 1, RX6 in 3, …: il DX successivo è nella posizione dopo.
        val rxPosition = 2 * (DscSymbols.RX7 - rx) + 1
        synced = true
        position = rxPosition + 2
        bitInSymbol = 0
        received.clear()
    }

    private fun isComplete(): Boolean {
        if (position > MAX_POSITIONS) return true
        if (position < MIN_POSITIONS) return false
        // Fine sequenza: simbolo di chiusura nelle posizioni DX di eos (54, 58, 60 per la lunghezza standard).
        val last = position - 1
        val eos = listOf(last - 1, last - 3, last - 7).count { p ->
            val s = received[p] ?: return@count false
            DscSymbols.isValid(s) && DscSymbols.value(s) in END_SYMBOLS
        }
        return eos >= 2
    }

    /** Valore del simbolo DX alla posizione [dx], o della copia RX cinque posizioni dopo; null se entrambi errati. */
    private fun pick(dx: Int): Int? {
        val a = received[dx]
        if (a != null && DscSymbols.isValid(a)) return DscSymbols.value(a)
        val b = received[dx + 5]
        if (b != null && DscSymbols.isValid(b)) return DscSymbols.value(b)
        return null
    }

    private fun finish(): Call? {
        synced = false
        bits = 0
        val total = received.size
        val errors = received.values.count { !DscSymbols.isValid(it) }
        // Posizioni DX dei campi (fasatura in 0..11).
        val format = pick(12) ?: pick(14) ?: return null
        val fields = (0 until 21).map { pick(FIELD_START + 2 * it) }
        fun digits(from: Int, count: Int): String? {
            val parts = fields.subList(from, from + count)
            if (parts.any { it == null || it > 99 }) return null
            return parts.joinToString("") { it.toString().padStart(2, '0') }
        }
        val address = digits(0, 5)
        val category = fields[5]
        val selfId = digits(6, 5)
        val ecc = fields.subList(0, 20).filterNotNull().fold(format) { acc, v -> acc xor v }
        val eccSymbol = pick(56)
        return Call(
            format = format,
            category = category,
            address = address,
            selfId = selfId,
            command1 = fields[11],
            command2 = fields[12],
            frequency1 = digits(13, 3),
            frequency2 = digits(16, 3),
            errors = errors,
            symbols = total,
            eccOk = eccSymbol != null && eccSymbol == ecc
        ).also { received.clear() }
    }

    fun reset() {
        synced = false
        bits = 0
        received.clear()
    }

    private companion object {
        val DX_W = DscSymbols.encode(DscSymbols.DX)
        val END_SYMBOLS = setOf(DscSymbols.EOS, DscSymbols.ARQ, DscSymbols.ABQ)
        const val FIELD_START = 16
        const val MIN_POSITIONS = 62
        const val MAX_POSITIONS = 80
    }
}

/**
 * Decoder DSC locale (MF/HF, 100 baud, shift 170 Hz, centro audio 500 Hz in modo CW). Solo
 * informativo: non va usato per la sicurezza della vita in mare.
 */
class DscDecoder(@Suppress("unused") private var params: DigitalModeParams) : LocalDecoder {
    override val mode = DigitalMode.DSC

    private var sampleRate = 0
    private var demodulator: FskDemodulator? = null
    private var clock: SyncBitClock? = null
    private val calls = DscCallDecoder()
    private var metricsCounter = 0

    private fun setup(rate: Int) {
        sampleRate = rate
        // Il simbolo Y (1) è il tono basso: polarità opposta al NAVTEX (come nei menu del client web).
        val demod = FskDemodulator(
            rate,
            DigitalModeCatalog.SITOR_CENTER_HZ.toDouble(),
            DigitalModeCatalog.SITOR_SHIFT_HZ.toDouble(),
            DigitalModeCatalog.SITOR_BAUD,
            markIsLower = true
        )
        demodulator = demod
        clock = SyncBitClock(demod.samplesPerBit)
        calls.reset()
    }

    override fun process(samples: FloatArray, count: Int, sampleRate: Int, sink: DecoderSink) {
        if (sampleRate != this.sampleRate || demodulator == null) setup(sampleRate)
        val demod = demodulator ?: return
        val bitClock = clock ?: return
        for (i in 0 until count) {
            val bit = bitClock.process(demod.process(samples[i])) ?: continue
            calls.process(bit)?.let { sink.add(describe(it)) }
        }
        metricsCounter += count
        if (metricsCounter >= sampleRate / 2) {
            metricsCounter = 0
            sink.metrics { it.copy(level = demod.level.toFloat(), synchronized = demod.hasSignal) }
        }
    }

    override fun reset() {
        demodulator?.reset()
        clock?.reset()
        calls.reset()
    }

    override fun updateParams(params: DigitalModeParams): Boolean {
        this.params = params
        return true
    }

    companion object {
        fun formatName(code: Int): String = when (code) {
            102 -> "Area geografica"
            112 -> "SOCCORSO"
            114 -> "Gruppo"
            116 -> "Tutte le navi"
            120 -> "Stazione individuale"
            121 -> "Riservato"
            123 -> "Individuale semiautomatica"
            else -> "Formato $code"
        }

        fun categoryName(code: Int?): String = when (code) {
            100 -> "Ordinaria"
            108 -> "Sicurezza"
            110 -> "Urgenza"
            112 -> "Soccorso"
            null -> "?"
            else -> "Categoria $code"
        }

        fun commandName(code: Int?): String? = when (code) {
            100 -> "fonia F3E/G3E"
            101 -> "fonia duplex"
            103 -> "interrogazione"
            104 -> "impossibile rispondere"
            105 -> "fine chiamata"
            106 -> "dati F1B"
            109 -> "fonia J3E"
            110 -> "conferma di soccorso"
            112 -> "ritrasmissione di soccorso"
            113 -> "telex F1B FEC"
            115 -> "telex F1B ARQ"
            118 -> "prova"
            121 -> "richiesta di posizione"
            126, null -> null
            else -> "comando $code"
        }

        /** Frequenza da 6 cifre in centinaia di Hz (es. "021820" → 2182,0 kHz); null se non è una frequenza. */
        fun frequencyText(digits: String?): String? {
            if (digits == null || digits.startsWith("9") || digits == "126126") return null
            val value = digits.toLongOrNull() ?: return null
            return "${value / 10},${value % 10} kHz"
        }

        /** MMSI a 9 cifre da 10 cifre (l'ultima è sempre 0). */
        fun mmsi(digits: String?): String? = digits?.take(9)

        internal fun describe(call: DscCallDecoder.Call): DscCall {
            val details = buildList {
                commandName(call.command1)?.let(::add)
                commandName(call.command2)?.let(::add)
                frequencyText(call.frequency1)?.let { add("RX $it") }
                frequencyText(call.frequency2)?.let { add("TX $it") }
                if (!call.eccOk) add("controllo ECC non superato")
            }.joinToString(" · ")
            val distress = call.format == 112
            return DscCall(
                receivedAtMillis = System.currentTimeMillis(),
                format = formatName(call.format),
                category = if (distress) categoryName(112) else categoryName(call.category),
                // Nel formato di soccorso il primo campo è l'identità di chi trasmette.
                selfId = mmsi(if (distress) call.address else call.selfId) ?: "?",
                address = if (distress || call.format == 116) null else mmsi(call.address),
                details = if (distress) "allerta di soccorso (dettagli non decodificati)" else details,
                errorPercent = if (call.symbols > 0) call.errors * 100 / call.symbols else 0
            )
        }
    }
}
