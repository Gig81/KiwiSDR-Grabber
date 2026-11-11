package com.grabber.audio.squelch

import kotlin.math.roundToInt

/**
 * Utilità per la conversione standard IARU HF tra livello RSSI in dBm e scala S-Meter.
 *
 * Riferimento HF:
 * S9 = -73 dBm (50 uV su 50 Ohm)
 * Ogni punto S sotto S9 equivale a 6 dB.
 * S0 = -127 dBm (limite inferiore di sensibilità KiwiSDR).
 * Sopra S9 la scala indica i decibel relativi (S9+10dB, S9+20dB, ecc.).
 */
object SMeterUtils {
    const val MIN_DBM = -127.0
    const val MAX_DBM = -43.0 // S9+30 dB
    const val S9_DBM = -73.0

    /**
     * Converte un valore in dBm nella stringa S-Meter corrispondente (es. "S4", "S9+12dB").
     * Le fasce sotto S9 sono centrate attorno ai valori nominali IARU a intervalli di 6 dB.
     */
    fun dbmToSUnits(dbm: Double): String {
        return when {
            dbm <= -124.0 -> "S0"
            dbm <= -118.0 -> "S1"
            dbm <= -112.0 -> "S2"
            dbm <= -106.0 -> "S3"
            dbm <= -100.0 -> "S4"
            dbm <= -94.0 -> "S5"
            dbm <= -88.0 -> "S6"
            dbm <= -82.0 -> "S7"
            dbm <= -76.0 -> "S8"
            dbm <= -73.0 -> "S9"
            else -> {
                val overDb = (dbm - S9_DBM).roundToInt()
                "S9+${overDb}dB"
            }
        }
    }

    /**
     * Converte un indice S in dBm.
     * sIndex: 0..9 per S0..S9, oppure oltre (es. 10 per S9+10dB, 11 per S9+20dB).
     */
    fun sIndexToDbm(sIndex: Int): Double {
        return when {
            sIndex <= 0 -> -127.0
            sIndex in 1..9 -> -127.0 + (sIndex * 6.0)
            sIndex == 10 -> -63.0 // S9+10dB
            sIndex == 11 -> -53.0 // S9+20dB
            else -> -43.0         // S9+30dB
        }
    }

    /**
     * Normalizza il valore dBm per gli slider UI nell'intervallo [0.0f, 1.0f].
     */
    fun dbmToNormalized(dbm: Double): Float {
        return ((dbm - MIN_DBM) / (MAX_DBM - MIN_DBM)).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Converte il valore normalizzato dello slider [0.0f, 1.0f] in dBm.
     */
    fun normalizedToDbm(fraction: Float): Double {
        return (MIN_DBM + fraction.coerceIn(0f, 1f) * (MAX_DBM - MIN_DBM))
    }
}
