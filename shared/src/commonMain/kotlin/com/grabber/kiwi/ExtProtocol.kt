package com.grabber.kiwi

import com.grabber.kiwi.KiwiProtocol.formatDecimal
import kotlin.math.pow

/**
 * Comandi testuali del WebSocket `/EXT` di KiwiSDR (estensioni con decoder sul server).
 *
 * Reimplementati dal protocollo osservato (sorgenti `extensions/ext.cpp` e client delle estensioni,
 * verificati con le tracce della fase 0): nessun codice KiwiSDR è incluso. La sintonia non passa da
 * qui ma dal socket SND ([KiwiProtocol.tune]), come fa il client web.
 */
object ExtProtocol {
    /**
     * Collega il socket all'estensione [extensionName] (nome esatto, sensibile alle maiuscole).
     * Il server ignora `rx_chan` e usa il canale della SND con lo stesso token.
     */
    fun switchToClient(extensionName: String, firstTime: Boolean = true, rxChannel: Int = -1): String =
        "SET ext_switch_to_client=$extensionName first_time=${if (firstTime) 1 else 0} rx_chan=$rxChannel"

    /** Stacca l'estensione dal canale. Non ferma il decoder: va preceduto dal comando di stop. */
    fun blur(rxChannel: Int = -1): String = "SET ext_blur=$rxChannel"

    /** Keepalive del socket `/EXT` (timeout lato server 60 s). */
    const val KEEPALIVE = "SET keepalive"

    /** FT8 (protocollo 0) e FT4 (protocollo 1). */
    object Ft8 {
        const val EXTENSION = "FT8"
        fun start(protocol: Int) = "SET ft8_start=$protocol debug=0"
        fun protocol(protocol: Int) = "SET ft8_protocol=$protocol"
        const val CLEAR = "SET ft8_clear"
        const val CLOSE = "SET ft8_close"
    }

    /** WSPR: il nome dell'estensione è minuscolo. */
    object Wspr {
        const val EXTENSION = "wspr"
        const val DEFAULT_BFO_HZ = 750
        const val FILTER_WIDTH_HZ = 300
        fun bfo(hz: Int) = "SET BFO=$hz"
        const val STACK_DECODER_OFF = "SET stack_decoder=0"

        /** [centerKhz] è la frequenza centrale del sottobanda WSPR (dial + 1,5 kHz). */
        fun frequencies(centerKhz: Double, bfoHz: Int = DEFAULT_BFO_HZ): String {
            val dialKhz = centerKhz - bfoHz / 1000.0
            val offsetHz = ((centerKhz - kotlin.math.floor(centerKhz)) * 1000).let { kotlin.math.round(it).toInt() }
            return "SET dialfreq=${formatDecimal(dialKhz, 2)} centerfreq=${formatDecimal(centerKhz, 2)} cf_offset=$offsetHz"
        }

        fun capture(enabled: Boolean) = "SET capture=${if (enabled) 1 else 0}"
    }

    /** Decoder CW singolo segnale. */
    object Cw {
        const val EXTENSION = "CW_decoder"
        const val DEFAULT_TRAINING = 100
        fun start(training: Int = DEFAULT_TRAINING) = "SET cw_start=$training"

        /** Distanza del centro della banda passante dalla portante, in Hz (500 in CW standard). */
        fun passbandOffset(hz: Int) = "SET cw_pboff=$hz"

        /** 0 = velocità automatica. */
        fun wpm(wpm: Int, training: Int = DEFAULT_TRAINING) = "SET cw_wpm=$wpm,$training"
        fun autoThreshold(enabled: Boolean) = "SET cw_auto_thresh=${if (enabled) 1 else 0}"

        /** Soglia in dB convertita in potenza lineare, come il client web. */
        fun threshold(db: Int): String {
            val linear = kotlin.math.round(10.0.pow(db / 10.0)).toLong()
            return "SET cw_threshold=$linear"
        }

        fun wordSpaceCorrection(enabled: Boolean) = "SET cw_wsc=${if (enabled) 1 else 0}"
        const val STOP = "SET cw_stop"
    }

    /** CW Skimmer: decodifica tutti i segnali CW nella banda passante. */
    object CwSkimmer {
        const val EXTENSION = "CW_skimmer"
        const val START = "SET cws_start"

        /** [powerCalc]: 0 rapporto medio, 1 fondo medio, 2 soglia. */
        fun params(powerCalc: Int, filterNeighbors: Boolean) = "SET cws_params=$powerCalc,${if (filterNeighbors) 1 else 0}"
        const val STOP = "SET cws_stop"
    }

    /** Fax meteo HF. */
    object Fax {
        const val EXTENSION = "FAX"
        const val CARRIER_OFFSET_KHZ = 1.9
        const val PASSBAND_LOW = 1300
        const val PASSBAND_HIGH = 2500
        val LPM_VALUES = listOf(60, 90, 100, 120, 180, 240)

        fun start(lpm: Int, phasing: Boolean, autoStop: Boolean) =
            "SET fax_start lpm=$lpm phasing=${if (phasing) 1 else 0} autostop=${if (autoStop) 1 else 0} debug=0"

        /** Correzione dell'inclinazione: spostamento normalizzato (-1…1) della riga. */
        fun shift(normalized: Double) = "SET fax_shift=${formatDecimal(normalized, 6)}"
        const val STOP = "SET fax_stop"
    }

    /** SSTV: comandi senza prefisso dell'estensione. */
    object Sstv {
        const val EXTENSION = "SSTV"
        const val START = "SET start"
        const val STOP = "SET stop"
        const val RESET = "SET reset"
        fun autoAdjust(enabled: Boolean) = "SET noadj=${if (enabled) 0 else 1}"
    }

    /** HFDL (dumphfdl sul server). */
    object Hfdl {
        const val EXTENSION = "HFDL"
        const val START = "SET start"
        const val STOP = "SET stop"
        const val PASSBAND_LOW = 300
        const val PASSBAND_HIGH = 2600
        fun displayFrequency(khz: Double) = "SET display_freq=${formatDecimal(khz, 2)}"
    }

    /** ALE 2G. La scansione lato server (`SET tune=…,1`) non si usa: risintonizzerebbe la SND senza il controller. */
    object Ale {
        const val EXTENSION = "ALE_2G"
        const val START = "SET start"
        const val STOP = "SET stop"
        const val PASSBAND_LOW = 600
        const val PASSBAND_HIGH = 2650
    }
}
