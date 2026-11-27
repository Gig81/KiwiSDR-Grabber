package com.grabber.decoder.fsk

/**
 * Alfabeto telegrafico ITA2 (Baudot-Murray) a 5 bit, con i caratteri numerici della variante
 * statunitense US-TTY (usata dai radioamatori) o internazionale. Il codice ha il primo bit ricevuto
 * nel bit 0.
 */
class Ita2Decoder(
    private val usTty: Boolean = true,
    /** Torna alle lettere dopo uno spazio (unshift on space), utile con disturbi. */
    private val unshiftOnSpace: Boolean = true
) {
    private var figures = false

    /** Restituisce il carattere, o null per i codici di controllo (LTRS, FIGS, NUL). */
    fun decode(code: Int): Char? {
        when (code and 0x1F) {
            LTRS -> { figures = false; return null }
            FIGS -> { figures = true; return null }
            0x00 -> return null
        }
        val c = code and 0x1F
        if (c == SPACE && unshiftOnSpace) figures = false
        val table = if (figures) (if (usTty) FIGS_US else FIGS_ITA2) else LETTERS
        return table[c].takeIf { it != '\u0000' }
    }

    fun reset() {
        figures = false
    }

    companion object {
        const val LTRS = 0x1F
        const val FIGS = 0x1B
        const val SPACE = 0x04
        private const val N = '\u0000'

        private val LETTERS = charArrayOf(
            N, 'E', '\n', 'A', ' ', 'S', 'I', 'U', '\r', 'D', 'R', 'J', 'N', 'F', 'C', 'K',
            'T', 'Z', 'L', 'W', 'H', 'Y', 'P', 'Q', 'O', 'B', 'G', N, 'M', 'X', 'V', N
        )
        private val FIGS_US = charArrayOf(
            N, '3', '\n', '-', ' ', '\u0007', '8', '7', '\r', '$', '4', '\'', ',', '!', ':', '(',
            '5', '"', ')', '2', '#', '6', '0', '1', '9', '?', '&', N, '.', '/', ';', N
        )
        private val FIGS_ITA2 = charArrayOf(
            N, '3', '\n', '-', ' ', '\'', '8', '7', '\r', N, '4', '\u0007', ',', N, ':', '(',
            '5', '+', ')', '2', N, '6', '0', '1', '9', '?', N, N, '.', '/', '=', N
        )
    }
}

/**
 * Alfabeto CCIR 476 / ITU-R M.476 a 7 bit con esattamente 4 bit "B" (mark): ogni codice con un numero
 * diverso di mark è un errore rilevato. Il codice ha il primo bit ricevuto nel bit 0.
 */
object Ccir476 {
    const val ALPHA = 0x0F
    const val BETA = 0x33
    const val REP = 0x66
    const val CHAR32 = 0x6A
    const val LETTERS = 0x5A
    const val FIGURES = 0x36

    private val letters = HashMap<Int, Char>()
    private val figures = HashMap<Int, Char>()

    init {
        // Coppie codice → (lettera, cifra/segno) secondo la tabella ITU.
        val table = listOf(
            0x47 to ('A' to '-'), 0x72 to ('B' to '?'), 0x1D to ('C' to ':'), 0x53 to ('D' to '$'),
            0x56 to ('E' to '3'), 0x1B to ('F' to '!'), 0x35 to ('G' to '&'), 0x69 to ('H' to '#'),
            0x4D to ('I' to '8'), 0x17 to ('J' to '\''), 0x1E to ('K' to '('), 0x65 to ('L' to ')'),
            0x39 to ('M' to '.'), 0x59 to ('N' to ','), 0x71 to ('O' to '9'), 0x2D to ('P' to '0'),
            0x2E to ('Q' to '1'), 0x55 to ('R' to '4'), 0x4B to ('S' to '\u0007'), 0x74 to ('T' to '5'),
            0x4E to ('U' to '7'), 0x3C to ('V' to ';'), 0x27 to ('W' to '2'), 0x3A to ('X' to '/'),
            0x2B to ('Y' to '6'), 0x63 to ('Z' to '"'), 0x5C to (' ' to ' '), 0x6C to ('\n' to '\n'),
            0x78 to ('\r' to '\r')
        )
        for ((code, chars) in table) {
            letters[code] = chars.first
            figures[code] = chars.second
        }
    }

    fun isValid(code: Int): Boolean = Integer.bitCount(code and 0x7F) == 4

    /** Carattere stampabile del codice nello stato indicato, null per i codici di controllo. */
    fun charOf(code: Int, figuresShift: Boolean): Char? = (if (figuresShift) figures else letters)[code]
}
