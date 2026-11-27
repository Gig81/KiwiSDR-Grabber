package com.grabber.decoder

import com.grabber.model.digital.DigitalMode
import com.grabber.model.digital.DigitalModeParams

/**
 * Decoder eseguito nell'app sull'audio SND (RTTY/FSK, NAVTEX, DSC). Riceve i campioni originali
 * (prima di DSP e squelch) normalizzati in -1..1, sempre da un solo flusso e in ordine.
 */
interface LocalDecoder {
    val mode: DigitalMode

    fun process(samples: FloatArray, count: Int, sampleRate: Int, sink: DecoderSink)

    /** Azzera lo stato (buco nel flusso audio, nuova sintonia). */
    fun reset()

    /** Nuovi parametri con il decoder attivo; restituisce false se serve ricrearlo. */
    fun updateParams(params: DigitalModeParams): Boolean = false
}

/** Crea il decoder locale per un modo, o null se il modo non ne ha uno. */
fun interface LocalDecoderFactory {
    fun create(mode: DigitalMode, params: DigitalModeParams): LocalDecoder?
}
