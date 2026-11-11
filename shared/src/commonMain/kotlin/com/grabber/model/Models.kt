package com.grabber.model

/**
 * Stato di connettività del client KiwiSDR.
 *
 * Viene usato per rappresentare le transizioni di rete, il tentativo di riconnessione e
 * i momenti in cui il sistema è in attesa di un flusso audio valido.
 */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val message: String = "Connessione in corso...") : ConnectionState
    data object Connected : ConnectionState
    data class Error(val message: String) : ConnectionState
    data class Reconnecting(val attempt: Int, val secondsLeft: Int, val reason: String = "") : ConnectionState
}

/**
 * Frame audio PCM ricevuto dal server KiwiSDR.
 *
 * Il payload è un array di byte mono 16-bit e la proprietà `sampleRate` deve essere inclusa
 * nella semantica di uguaglianza, perché frame con stesso contenuto ma frequenze diverse non
 * sono equivalenti dal punto di vista del processamento audio.
 */
data class AudioFrame(
    val sequence: Long,
    val rssi: Double,
    val pcmBytes: ByteArray,
    val sampleRate: Int = 12000
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFrame) return false
        return sequence == other.sequence &&
            rssi == other.rssi &&
            sampleRate == other.sampleRate &&
            pcmBytes.contentEquals(other.pcmBytes)
    }

    override fun hashCode(): Int {
        var result = sequence.hashCode()
        result = 31 * result + rssi.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + pcmBytes.contentHashCode()
        return result
    }
}

data class StationInfo(
    val name: String = "",
    val antenna: String = "",
    val activeUsers: Int = 0,
    val maxUsers: Int = 0,
    val snr: String = "",
    val uptime: String = "",
    val version: String = "",
    val gps: String = ""
)

