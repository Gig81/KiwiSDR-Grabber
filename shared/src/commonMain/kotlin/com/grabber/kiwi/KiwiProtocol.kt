package com.grabber.kiwi

import com.grabber.model.ModulationMode
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Helper centralizzato per costruire i comandi testuali del protocollo KiwiSDR.
 *
 * L'obiettivo è mantenere la logica di serializzazione dei comandi isolata dal client
 * WebSocket e dai servizi di controllo del ricevitore, evitando stringhe hardcoded in più classi.
 */
object KiwiProtocol {
    private const val AUTH_PREFIX = "SET auth t=kiwi p="
    private const val REDACTED = "***"
    private const val UNRESERVED_SYMBOLS = "-_.!~*'()"

    /**
     * Autentica il client verso il server KiwiSDR.
     * La password viene codificata come `encodeURIComponent`: il server la decodifica
     * con `kiwi_str_decode_inplace`, quindi spazi e caratteri speciali restano validi.
     */
    fun auth(password: String) = AUTH_PREFIX + encodeComponent(password)

    /**
     * Imposta il nome identificativo del client presente nell'header di controllo.
     */
    fun identUser(name: String) = "SET ident_user=${encodeComponent(name)}"

    /**
     * Restituisce il comando con la password oscurata, da usare nei log e nei messaggi diagnostici.
     */
    fun redactSecrets(command: String): String =
        if (command.startsWith(AUTH_PREFIX)) AUTH_PREFIX + REDACTED else command

    /** Frequenza in kHz con tre decimali e punto come separatore, indipendente dalla lingua di sistema. */
    internal fun formatKhz(freqKhz: Double): String = formatDecimal(freqKhz, 3)

    /** Numero con [decimals] cifre decimali (arrotondato) e punto come separatore, senza API `java.*`. */
    internal fun formatDecimal(value: Double, decimals: Int): String {
        var scale = 1L
        repeat(decimals) { scale *= 10 }
        val total = (value * scale).roundToLong()
        val sign = if (total < 0) "-" else ""
        val units = abs(total)
        if (decimals == 0) return "$sign$units"
        return "$sign${units / scale}.${(units % scale).toString().padStart(decimals, '0')}"
    }

    /**
     * Percent-encoding UTF-8 equivalente a `encodeURIComponent` di JavaScript,
     * lo stesso usato dal client web ufficiale KiwiSDR.
     */
    internal fun encodeComponent(value: String): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            if (code < 0x80 && (char.isLetterOrDigit() || char in UNRESERVED_SYMBOLS)) {
                append(char)
            } else {
                append('%').append(code.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }

    /**
     * Convalida la negoziazione del rate audio tra client e server.
     */
    fun arOk(inRate: Int, outRate: Int) = "SET AR OK in=$inRate out=$outRate"

    /**
     * Abilita o disabilita la compressione del flusso audio del server.
     */
    fun compression(enabled: Boolean) = "SET compression=${if (enabled) 1 else 0}"

    /**
     * Applica la nuova frequenza e la modalità di demodulazione.
     *
     * [freqKhz] è la frequenza mostrata all'utente; il server riceve la portante
     * (vedi [carrierFrequencyKhz]): in CW le due differiscono del centro della banda passante.
     */
    fun tune(mode: ModulationMode, freqKhz: Double, lowCut: Int? = null, highCut: Int? = null): String {
        val lc = lowCut ?: mode.defaultLowCut
        val hc = highCut ?: mode.defaultHighCut
        val carrier = carrierFrequencyKhz(mode, freqKhz, lc, hc)
        return "SET mod=${mode.code} low_cut=$lc high_cut=$hc freq=${formatKhz(carrier)}"
    }

    /**
     * Portante da inviare al server per la frequenza mostrata. In CW, come nel client web KiwiSDR,
     * la frequenza mostrata è quella del segnale: portante + centro della banda passante
     * (con la banda predefinita 300–700 Hz il tono si sente a 500 Hz). Negli altri modi coincidono.
     */
    fun carrierFrequencyKhz(mode: ModulationMode, displayKhz: Double, lowCut: Int, highCut: Int): Double =
        if (mode == ModulationMode.CW) displayKhz - (lowCut + highCut) / 2000.0 else displayKhz

    /** Inverso di [carrierFrequencyKhz]: frequenza da mostrare per una portante. */
    fun displayFrequencyKhz(mode: ModulationMode, carrierKhz: Double, lowCut: Int, highCut: Int): Double =
        if (mode == ModulationMode.CW) carrierKhz + (lowCut + highCut) / 2000.0 else carrierKhz

    /**
     * Configura l'AGC del ricevitore, inclusi i parametri di soglia e guadagno manuale.
     */
    fun agc(enabled: Boolean, gain: Int = 50): String {
        val en = if (enabled) 1 else 0
        return "SET agc=$en hang=0 thresh=-100 slope=6 decay=1000 manGain=$gain"
    }

    /**
     * Richiede tutte le etichette DX memorizzate sul ricevitore (risposta `MSG mkr=`).
     * Il server accetta solo la variante completa a 8 parametri, con la maschera in esadecimale
     * `0x…`: una variante sconosciuta viene trattata come app non Kiwi (`too_busy` e disconnessione).
     * `filter_tod=0` e `anti_clutter=0` restituiscono l'elenco completo, senza filtri di orario o spaziatura.
     */
    fun dxMarkers(): String =
        "SET MARKER db=0 min=0.000 max=30000.000 zoom=0 width=1024 eibi_types_mask=0xffffffff filter_tod=0 anti_clutter=0"

    /**
     * Comando di keepalive richiesto per mantenere la sessione attiva.
     */
    val keepalive = "SET keepalive"
}
