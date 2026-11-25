package com.grabber.decoder.fsk

/**
 * Decodifica SITOR-B (FEC, ITU-R M.476/M.625) dai bit sincroni a 100 baud.
 *
 * 1. Allineamento dei caratteri: tra le 7 posizioni possibili si sceglie quella con la quota più alta
 *    di codici validi (4 mark su 7).
 * 2. Fase: ogni carattere è trasmesso due volte, prima in posizione RX ("rep") e cinque caratteri dopo
 *    in posizione DX ("alpha"); la parità giusta è quella in cui le due copie coincidono più spesso
 *    (o in cui compaiono i caratteri di fasatura).
 * 3. FEC: si usa la copia DX se valida, altrimenti quella RX; se entrambe sono errate il carattere è
 *    perso e viene segnalato con [ERROR_CHAR].
 */
class SitorBDecoder {
    companion object {
        const val ERROR_CHAR = '▒'
        private const val HISTORY = 64
        private const val REPEAT_DISTANCE = 5
        private const val SCORE_ALPHA = 0.08
        private const val SYNC_ON = 0.75
        private const val SYNC_OFF = 0.5
    }

    private var register = 0
    private var bitCount = 0L
    private val alignmentScore = DoubleArray(7)
    private var alignment = -1
    private val codes = IntArray(HISTORY)
    private var slot = 0L
    private val parityScore = DoubleArray(2)
    private var figures = false

    /** Vero se il decoder è allineato ai caratteri. */
    val isSynchronized: Boolean get() = alignment >= 0

    var decodedChars = 0L
        private set
    var errorChars = 0L
        private set

    /** Elabora un bit (1 = B/mark); restituisce il carattere decodificato, [ERROR_CHAR] o null. */
    fun process(bit: Int): Char? {
        register = (register ushr 1) or (bit shl 6)
        val position = (bitCount % 7).toInt()
        bitCount++
        if (bitCount < 7) return null
        val valid = Ccir476.isValid(register)
        alignmentScore[position] += ((if (valid) 1.0 else 0.0) - alignmentScore[position]) * SCORE_ALPHA

        if (alignment < 0) {
            val best = alignmentScore.indices.maxBy { alignmentScore[it] }
            if (alignmentScore[best] > SYNC_ON) {
                alignment = best
                slot = 0
                parityScore.fill(0.0)
                figures = false
            }
            return null
        }
        if (position != alignment) return null
        if (alignmentScore[alignment] < SYNC_OFF) {
            alignment = -1
            return null
        }
        return onCode(register)
    }

    private fun onCode(code: Int): Char? {
        val index = (slot % HISTORY).toInt()
        codes[index] = code
        val parity = (slot % 2).toInt()
        if (code == Ccir476.ALPHA) parityScore[parity] += 1.0
        if (code == Ccir476.REP) parityScore[1 - parity] += 1.0
        if (slot >= REPEAT_DISTANCE) {
            val earlier = codes[((slot - REPEAT_DISTANCE) % HISTORY).toInt()]
            if (Ccir476.isValid(code) && code == earlier) parityScore[parity] += 0.5
        }
        parityScore[0] *= 0.98
        parityScore[1] *= 0.98
        slot++
        val alphaParity = if (parityScore[0] >= parityScore[1]) 0 else 1
        if (parity != alphaParity || slot <= REPEAT_DISTANCE) return null
        val rx = codes[((slot - 1 - REPEAT_DISTANCE) % HISTORY).toInt()]
        val chosen = when {
            Ccir476.isValid(code) -> code
            Ccir476.isValid(rx) -> rx
            else -> -1
        }
        return emit(chosen)
    }

    private fun emit(code: Int): Char? {
        if (code < 0) {
            decodedChars++
            errorChars++
            return ERROR_CHAR
        }
        when (code) {
            Ccir476.ALPHA, Ccir476.REP, Ccir476.BETA, Ccir476.CHAR32 -> return null
            Ccir476.LETTERS -> { figures = false; return null }
            Ccir476.FIGURES -> { figures = true; return null }
        }
        val char = Ccir476.charOf(code, figures) ?: return null
        decodedChars++
        return char
    }

    fun reset() {
        register = 0
        bitCount = 0
        alignmentScore.fill(0.0)
        alignment = -1
        slot = 0
        parityScore.fill(0.0)
        figures = false
    }
}

/**
 * Ricompone i messaggi NAVTEX dal flusso di caratteri: `ZCZC B1B2B3B4`, testo, `NNNN`.
 * Un messaggio senza `NNNN` viene chiuso come incompleto quando ne inizia un altro o con [flush].
 */
class NavtexMessageAssembler(private val maxBodyLength: Int = 12_000) {
    /** Messaggio grezzo: identificativo a 4 caratteri, testo e conteggio dei caratteri errati. */
    data class RawMessage(val header: String, val body: String, val errors: Int, val total: Int, val incomplete: Boolean)

    private val window = StringBuilder()
    private var inMessage = false
    private var header = StringBuilder()
    private val body = StringBuilder()
    private var errors = 0
    private var total = 0

    fun process(char: Char): RawMessage? {
        var completed: RawMessage? = null
        window.append(char)
        if (window.length > 8) window.deleteCharAt(0)
        if (window.endsWith("ZCZC")) {
            if (inMessage) completed = finish(incomplete = true)
            inMessage = true
            header.clear()
            body.clear()
            errors = 0
            total = 0
            return completed
        }
        if (!inMessage) return null
        if (header.length < 4) {
            if (char == ' ' && header.isEmpty()) return null
            if (char == '\r' || char == '\n') return null
            header.append(char)
            return null
        }
        total++
        if (char == SitorBDecoder.ERROR_CHAR) errors++
        body.append(char)
        if (window.endsWith("NNNN")) {
            body.setLength((body.length - 4).coerceAtLeast(0))
            return finish(incomplete = false)
        }
        if (body.length > maxBodyLength) return finish(incomplete = true)
        return null
    }

    /** Chiude il messaggio in corso (perdita del segnale). */
    fun flush(): RawMessage? = if (inMessage && body.isNotBlank()) finish(incomplete = true) else null.also { inMessage = false }

    private fun finish(incomplete: Boolean): RawMessage {
        inMessage = false
        val text = body.toString().replace("\r\n", "\n").replace('\r', '\n')
            .lines().joinToString("\n") { it.trimEnd() }.trim('\n', ' ')
        return RawMessage(header.toString(), text, errors, total, incomplete)
    }

    fun reset() {
        window.clear()
        inMessage = false
        header.clear()
        body.clear()
    }
}
