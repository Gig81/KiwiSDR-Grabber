package com.grabber.audio.vad

/**
 * Risultato dell'analisi di Voice Activity Detection (VAD) su un blocco audio.
 */
data class VadResult(
    /**
     * True se la voce umana è considerata attiva (sopra soglia di confidenza o in hangover).
     */
    val isVoice: Boolean,

    /**
     * Livello di confidenza stimato della presenza di voce umana (0.0f = nessun segnale vocale, 1.0f = voce certa).
     */
    val confidence: Float,

    /**
     * Rapporto di energia nella banda vocale standard (300 - 3000 Hz) rispetto all'energia totale a banda larga.
     * Valori tipici: per voce umana > 0.65 - 0.90; per rumore bianco / QRN a banda larga < 0.50.
     */
    val voiceBandRatio: Float,

    /**
     * Fattore di cresta (Crest Factor = Picco / RMS).
     * Scariche impulsive di fulmini e disturbi statici (QRN) presentano valori molto alti (> 6.0).
     */
    val crestFactor: Float,

    /**
     * True se il blocco audio è stato classificato come disturbo impulsivo (QRN, scoppio statico, spike).
     */
    val isImpulseNoise: Boolean,

    /**
     * Picco di autocorrelazione normalizzata nella banda fondamentale vocale umana (85 - 300 Hz).
     * Misura l'armonicità prodotta dalle corde vocali. Tipicamente > 0.35 per vocali, < 0.20 per rumore.
     */
    val pitchHarmonicity: Float,

    /**
     * Livello RMS audio in dBFS (Decibel Full Scale, tipicamente da -90 dBFS a 0 dBFS).
     */
    val rmsDb: Float,

    /**
     * Zero Crossing Rate normalizzato (frazioni di attraversamenti per campione).
     */
    val zeroCrossingRate: Float
)

/**
 * Interfaccia comune per motori di rilevamento attività vocale (VAD).
 */
interface VoiceActivityDetector {
    /**
     * Frequenza di campionamento del segnale audio (default 12000 Hz per KiwiSDR).
     */
    val sampleRate: Int

    /**
     * Soglia di confidenza (0.1f - 0.9f) per considerare la voce attiva. Default 0.50f.
     */
    var sensitivityThreshold: Float

    /**
     * Tempo di hangover in millisecondi per mantenere aperto il gate tra le sillabe o durante consonanti non vocalizzate.
     */
    var hangoverMs: Int

    /**
     * Analizza un blocco di byte PCM signed 16-bit little-endian e restituisce il verdetto VAD.
     */
    fun process(pcmBytes: ByteArray): VadResult

    /**
     * Resetta lo stato interno dei filtri, integratori e timer di hangover.
     */
    fun reset()
}

