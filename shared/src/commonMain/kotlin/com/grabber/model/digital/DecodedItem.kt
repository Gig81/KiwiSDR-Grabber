package com.grabber.model.digital

/**
 * Elemento prodotto da un decoder, in ordine di arrivo. [receivedAtMillis] è l'ora di ricezione
 * nell'app (epoch ms), indipendente dall'orario riportato dal server.
 */
sealed interface DecodedItem {
    val receivedAtMillis: Long

    /** Testo leggibile su una riga, usato per il salvataggio, le notifiche e la copia. */
    val plainText: String
}

/** Riga di testo continuo (CW, RTTY, HFDL, ALE) o messaggio di stato del decoder. */
data class DecodedTextLine(
    override val receivedAtMillis: Long,
    val text: String,
    /** Vero per le righe informative (intestazioni, cambi di frequenza), escluse dalle notifiche. */
    val isStatus: Boolean = false
) : DecodedItem {
    override val plainText: String get() = text
}

/** Messaggio FT8/FT4 di un ciclo. */
data class Ft8Message(
    override val receivedAtMillis: Long,
    /** Ora UTC dell'inizio del ciclo, "HH:MM:SS". */
    val utc: String,
    val snrDb: Double,
    val dtSeconds: Double,
    /** Frequenza audio del segnale nella banda passante. */
    val audioHz: Int,
    val message: String,
    /** Stazione che trasmette (de), se riconoscibile. */
    val callsign: String? = null,
    /** Locatore Maidenhead a 4 caratteri, se presente nel messaggio. */
    val locator: String? = null
) : DecodedItem {
    val isCq: Boolean get() = message.startsWith("CQ ") || message == "CQ"
    override val plainText: String
        get() = "$utc ${formatSigned(snrDb, 0)} dB ${formatSigned(dtSeconds, 1)} s $audioHz Hz  $message"
}

/** Spot WSPR. */
data class WsprSpot(
    override val receivedAtMillis: Long,
    /** Ora UTC "HHMM". */
    val utc: String,
    val snrDb: Int,
    val dtSeconds: Double,
    val frequencyMhz: Double,
    val drift: Int,
    val callsign: String,
    val locator: String?,
    val powerDbm: Int?,
    val powerLabel: String?
) : DecodedItem {
    override val plainText: String
        get() = "$utc $snrDb dB ${frequencyMhz.toString().take(9)} MHz $callsign ${locator.orEmpty()} ${powerDbm?.let { "$it dBm" }.orEmpty()}".trim()
}

/** Segnale del CW Skimmer: testo accumulato per frequenza. */
data class SkimmerSignal(
    override val receivedAtMillis: Long,
    /** Frequenza assoluta del segnale in kHz. */
    val frequencyKhz: Double,
    val wpm: Int,
    val text: String
) : DecodedItem {
    override val plainText: String get() = "${(kotlin.math.round(frequencyKhz * 10) / 10)} kHz ${wpm} WPM: $text"
}

/** Messaggio NAVTEX completo (`ZCZC B1B2B3B4 … NNNN`). */
data class NavtexMessage(
    override val receivedAtMillis: Long,
    /** Stazione trasmittente (lettera B1). */
    val station: Char,
    /** Tipo di messaggio (lettera B2), es. A avvisi di navigazione, B meteo, D ricerca e soccorso. */
    val type: Char,
    /** Numero progressivo (B3B4). */
    val number: String,
    val body: String,
    /** Percentuale di caratteri non correggibili nel messaggio. */
    val errorPercent: Int,
    /** Vero se il messaggio è arrivato senza `NNNN` (interrotto). */
    val incomplete: Boolean = false
) : DecodedItem {
    val id: String get() = "$station$type$number"
    val typeDescription: String get() = navtexTypeDescription(type)
    override val plainText: String get() = "ZCZC $id ${body.replace('\n', ' ')}"

    companion object {
        fun navtexTypeDescription(type: Char): String = when (type.uppercaseChar()) {
            'A' -> "Avviso di navigazione"
            'B' -> "Avviso meteo"
            'C' -> "Ghiaccio"
            'D' -> "Ricerca e soccorso"
            'E' -> "Previsioni meteo"
            'F' -> "Servizio piloti"
            'G' -> "AIS"
            'H' -> "LORAN"
            'J' -> "GNSS"
            'K' -> "Altri aiuti elettronici"
            'L' -> "Avviso di navigazione (aggiuntivo)"
            'V', 'W', 'X', 'Y' -> "Servizi speciali"
            'Z' -> "Nessun messaggio"
            else -> "Tipo $type"
        }
    }
}

/** Chiamata DSC decodificata (solo informativa). */
data class DscCall(
    override val receivedAtMillis: Long,
    val format: String,
    val category: String,
    val selfId: String,
    val address: String?,
    val details: String,
    val errorPercent: Int
) : DecodedItem {
    override val plainText: String
        get() = "DSC $format $category da $selfId${address?.let { " a $it" }.orEmpty()} $details".trim()
}

/** Indicatori del decoder attivo mostrati nella scheda. */
data class DecoderMetrics(
    val wpm: Int? = null,
    /** Livello del segnale normalizzato 0..1 (decoder locali). */
    val level: Float? = null,
    /** Percentuale di caratteri errati (decoder locali). */
    val errorPercent: Int? = null,
    val synchronized: Boolean? = null,
    /** Stato testuale del decoder (es. "in attesa del ciclo", "fasato", nome del modo SSTV). */
    val status: String? = null
)

/** Immagine in costruzione (FAX in scala di grigi, SSTV RGB). Le righe sono pixel ARGB. */
data class DecodedImage(
    val mode: DigitalMode,
    val width: Int,
    /** Righe ricevute finora, ognuna di [width] pixel ARGB. */
    val rows: List<IntArray>,
    val title: String,
    val startedAtMillis: Long,
    /** Contatore che cambia a ogni nuova riga, per aggiornare la UI senza confrontare i pixel. */
    val version: Long
) {
    val height: Int get() = rows.size
}

internal fun formatSigned(value: Double, decimals: Int): String {
    val text = com.grabber.kiwi.KiwiProtocol.formatDecimal(value, decimals)
    return if (value >= 0) "+$text" else text
}
