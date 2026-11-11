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
    internal fun formatKhz(freqKhz: Double): String {
        val totalHertz = (freqKhz * 1000).roundToLong()
        val sign = if (totalHertz < 0) "-" else ""
        val hertz = abs(totalHertz)
        return "$sign${hertz / 1000}.${(hertz % 1000).toString().padStart(3, '0')}"
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
     */
    fun tune(mode: ModulationMode, freqKhz: Double, lowCut: Int? = null, highCut: Int? = null): String {
        val lc = lowCut ?: mode.defaultLowCut
        val hc = highCut ?: mode.defaultHighCut
        return "SET mod=${mode.code} low_cut=$lc high_cut=$hc freq=${formatKhz(freqKhz)}"
    }

    /**
     * Configura l'AGC del ricevitore, inclusi i parametri di soglia e guadagno manuale.
     */
    fun agc(enabled: Boolean, gain: Int = 50): String {
        val en = if (enabled) 1 else 0
        return "SET agc=$en hang=0 thresh=-100 slope=6 decay=1000 manGain=$gain"
    }

    /**
     * Comando di keepalive richiesto per mantenere la sessione attiva.
     */
    val keepalive = "SET keepalive"
}
