package com.grabber.waterfall

import kotlin.math.roundToInt

/**
 * Rappresenta un singolo frame dello spettrometro FFT ricevuto dal server KiwiSDR.
 *
 * @property sequenceNumber Numero di sequenza del pacchetto W/F.
 * @property bins Array di campioni spettrali (tipicamente 1024 bin). Ogni byte unsigned (0..255)
 *                 rappresenta l'ampiezza: dBm = valore - 255 (quindi da -255 dBm a 0 dBm).
 * @property zoom Livello di zoom attuale (0..14).
 * @property centerFreqKhz Frequenza centrale in kHz.
 * @property startFreqKhz Frequenza di inizio visualizzazione in kHz (corrispondente al bin 0).
 * @property spanKhz Larghezza dello spettro visibile in kHz (pari a 30000.0 / 2^zoom).
 * @property stopFreqKhz Frequenza di fine visualizzazione in kHz (corrispondente all'ultimo bin).
 */
data class WaterfallFrame(
    val sequenceNumber: Long,
    val bins: ByteArray,
    val zoom: Int,
    val centerFreqKhz: Double,
    val startFreqKhz: Double,
    val spanKhz: Double,
    val stopFreqKhz: Double
) {
    val binCount: Int get() = bins.size

    /**
     * Calcola l'ampiezza in dBm per un dato bin (0..binCount-1).
     * Restituisce un valore tipicamente compreso tra -160 dBm e 0 dBm.
     */
    fun getDbm(binIndex: Int): Int {
        if (binIndex !in bins.indices) return -140
        val unsignedByte = bins[binIndex].toInt() and 0xFF
        return unsignedByte - 255
    }

    /**
     * Restituisce la frequenza esatta in kHz corrispondente al bin specificato.
     */
    fun frequencyAtBin(binIndex: Int): Double {
        if (bins.isEmpty()) return startFreqKhz
        val fraction = binIndex.toDouble() / (bins.size - 1).coerceAtLeast(1)
        return (startFreqKhz + fraction * spanKhz).coerceIn(0.0, 30000.0)
    }

    /**
     * Converte una frequenza in kHz nell'indice del bin più vicino (0..binCount-1).
     */
    fun binAtFrequency(freqKhz: Double): Int {
        if (bins.isEmpty() || spanKhz <= 0.0) return 0
        val fraction = ((freqKhz - startFreqKhz) / spanKhz).coerceIn(0.0, 1.0)
        return (fraction * (bins.size - 1)).roundToInt().coerceIn(0, bins.size - 1)
    }

    /**
     * Converte una frazione orizzontale (0.0 = estrema sinistra, 1.0 = estrema destra)
     * nella corrispondente frequenza radio in kHz. Utile per Click-to-Tune.
     */
    fun frequencyAtFraction(fraction: Float): Double {
        val f = fraction.toDouble().coerceIn(0.0, 1.0)
        return (startFreqKhz + f * spanKhz).coerceIn(0.0, 30000.0)
    }

    /**
     * Converte una frequenza radio in kHz nella corrispondente frazione orizzontale (0.0 .. 1.0).
     */
    fun fractionAtFrequency(freqKhz: Double): Float {
        if (spanKhz <= 0.0) return 0f
        return ((freqKhz - startFreqKhz) / spanKhz).toFloat().coerceIn(0f, 1f)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WaterfallFrame

        if (sequenceNumber != other.sequenceNumber) return false
        if (!bins.contentEquals(other.bins)) return false
        if (zoom != other.zoom) return false
        if (centerFreqKhz != other.centerFreqKhz) return false
        if (startFreqKhz != other.startFreqKhz) return false
        if (spanKhz != other.spanKhz) return false
        if (stopFreqKhz != other.stopFreqKhz) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sequenceNumber.hashCode()
        result = 31 * result + bins.contentHashCode()
        result = 31 * result + zoom
        result = 31 * result + centerFreqKhz.hashCode()
        result = 31 * result + startFreqKhz.hashCode()
        result = 31 * result + spanKhz.hashCode()
        result = 31 * result + stopFreqKhz.hashCode()
        return result
    }
}

