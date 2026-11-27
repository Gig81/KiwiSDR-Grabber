package com.grabber.model.digital

/** Dove avviene la decodifica: estensione del server KiwiSDR (`/EXT`) o nell'app sull'audio SND. */
enum class DecoderSource(val label: String) {
    SERVER("Server"),
    LOCAL("Locale")
}

/** Forma dell'uscita del decoder, che decide il componente usato dalla scheda "Modi digitali". */
enum class DecoderOutputKind {
    /** Righe strutturate (FT8/FT4, WSPR). */
    TABLE,
    /** Testo continuo (CW, RTTY, HFDL, ALE). */
    TEXT,
    /** Testo per segnale (CW Skimmer). */
    SKIMMER,
    /** Immagine (FAX, SSTV). */
    IMAGE,
    /** Messaggi completi (NAVTEX, DSC). */
    MESSAGES
}

/**
 * Modi digitali della scheda "Modi digitali", nell'ordine dell'elenco.
 *
 * @param extensionName nome dell'estensione `/EXT` (decoder del server), null per i decoder locali.
 * @param configKey chiave della configurazione del ricevitore (`load_cfg`) con l'abilitazione `<chiave>.enable`.
 * @param requires12kHz il decoder del server funziona solo con il canale a 12 kHz (non 20,25 kHz).
 */
enum class DigitalMode(
    val displayName: String,
    val source: DecoderSource,
    val extensionName: String?,
    val configKey: String?,
    val output: DecoderOutputKind,
    val requires12kHz: Boolean,
    val description: String
) {
    FT8("FT8", DecoderSource.SERVER, "FT8", "ft8", DecoderOutputKind.TABLE, true,
        "Messaggi FT8 dei radioamatori, cicli di 15 s. Decodifica tutti i segnali nella banda passante."),
    FT4("FT4", DecoderSource.SERVER, "FT8", "ft8", DecoderOutputKind.TABLE, true,
        "Messaggi FT4 dei radioamatori, cicli di 7,5 s."),
    WSPR("WSPR", DecoderSource.SERVER, "wspr", "WSPR", DecoderOutputKind.TABLE, false,
        "Spot dei beacon WSPR, cicli di 2 minuti. L'app non carica spot su wsprnet."),
    CW("CW", DecoderSource.SERVER, "CW_decoder", "cw", DecoderOutputKind.TEXT, false,
        "Telegrafia di un singolo segnale al centro della banda passante, con velocità automatica."),
    CW_SKIMMER("CW Skimmer", DecoderSource.SERVER, "CW_skimmer", "cws", DecoderOutputKind.SKIMMER, false,
        "Telegrafia di tutti i segnali nella banda passante, una riga per segnale."),
    FAX("Fax meteo", DecoderSource.SERVER, "FAX", "fax", DecoderOutputKind.IMAGE, false,
        "Carte meteo HF-FAX in scala di grigi (DWD, Met Office, NOAA…)."),
    SSTV("SSTV", DecoderSource.SERVER, "SSTV", "sstv", DecoderOutputKind.IMAGE, true,
        "Immagini a colori trasmesse dai radioamatori, modo riconosciuto dall'intestazione VIS."),
    RTTY("RTTY / FSK", DecoderSource.LOCAL, null, null, DecoderOutputKind.TEXT, false,
        "Telescrivente FSK (ITA2): radioamatori e bollettini meteo, decodificata nell'app."),
    NAVTEX("NAVTEX", DecoderSource.LOCAL, null, null, DecoderOutputKind.MESSAGES, false,
        "Avvisi ai naviganti SITOR-B (518, 490, 4209,5 kHz), decodificati nell'app."),
    HFDL("HFDL", DecoderSource.SERVER, "HFDL", "hfdl", DecoderOutputKind.TEXT, false,
        "Messaggi dati aeronautici HF (dumphfdl sul server)."),
    ALE("ALE 2G", DecoderSource.SERVER, "ALE_2G", "ale_2g", DecoderOutputKind.TEXT, false,
        "Chiamate di collegamento automatico ALE 2G (MIL-STD-188-141)."),
    DSC("DSC", DecoderSource.LOCAL, null, null, DecoderOutputKind.MESSAGES, false,
        "Chiamate digitali selettive marittime, decodificate nell'app. Solo informativo: non per la sicurezza in mare.");

    val isServer: Boolean get() = source == DecoderSource.SERVER
}

/**
 * Disponibilità di un modo sul ricevitore corrente.
 * [reason] spiega perché il modo non è disponibile (estensione assente, canale a 20 kHz…), null se lo è.
 */
data class DigitalModeAvailability(val mode: DigitalMode, val reason: String? = null) {
    val isAvailable: Boolean get() = reason == null
}

/** Stato del decoder attivo (al massimo uno per sessione). */
sealed interface DecoderState {
    data object Idle : DecoderState
    data class Starting(val mode: DigitalMode) : DecoderState
    data class Running(val mode: DigitalMode) : DecoderState
    /** Errore temporaneo: il decoder riparte dopo la riconnessione. */
    data class Error(val mode: DigitalMode, val message: String) : DecoderState
    /** Rifiuto del ricevitore: nessun nuovo tentativo automatico. */
    data class Unavailable(val mode: DigitalMode, val message: String) : DecoderState

    val activeMode: DigitalMode?
        get() = when (this) {
            Idle -> null
            is Starting -> mode
            is Running -> mode
            is Error -> mode
            is Unavailable -> mode
        }
}
