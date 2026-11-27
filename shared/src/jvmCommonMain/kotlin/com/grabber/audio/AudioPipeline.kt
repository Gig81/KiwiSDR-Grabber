package com.grabber.audio

import com.grabber.audio.dsp.AudioDspChain
import com.grabber.audio.dsp.AudioDspConfig
import com.grabber.audio.dsp.DspTarget
import com.grabber.audio.squelch.SquelchController
import com.grabber.model.AudioFrame

/**
 * Percorso dei frame audio SND verso altoparlante e registratore:
 *
 * ```
 * frame ─┬▶ [DSP pre-squelch]* ─▶ squelch ─▶ [DSP post-squelch]** ─▶ altoparlante
 *        │         │
 *        │         └──────────────▶ registratore (e pre-buffer dello squelch)
 *        └▶ analisi (copia originale: decoder locali, AFC, riconoscimento del modo)
 * ```
 * \* solo con destinazione *Ascolto e registrazione*: così pre-buffer, registrazione, ascolto e
 * allegati Telegram contengono lo stesso audio filtrato.
 * \*\* solo con destinazione *Solo ascolto*: il registratore riceve l'audio originale.
 *
 * Le due catene hanno stato separato. RSSI e gate RF non cambiano perché dipendono dall'header
 * del frame. Senza [licensed] il DSP resta spento qualunque sia la configurazione salvata.
 * [process] va chiamato da un solo flusso, in ordine (dispatcher seriale dei frame).
 */
class AudioPipeline(
    private val squelch: SquelchController,
    private val playbackSink: (ByteArray) -> Unit,
    private val recordSink: (AudioFrame) -> Unit,
    private val preSquelchDsp: AudioDspChain = AudioDspChain(),
    private val postSquelchDsp: AudioDspChain = AudioDspChain(),
    /** Copia del frame originale per analisi e decoder locali (prima di DSP e squelch). */
    private val analysisSink: ((AudioFrame) -> Unit)? = null
) {
    @Volatile private var config: AudioDspConfig = AudioDspConfig()
    @Volatile private var licensed: Boolean = false
    private var lastTarget: DspTarget = DspTarget.OFF

    /** Destinazione effettiva: spenta senza licenza. */
    val effectiveTarget: DspTarget get() = if (licensed) config.target else DspTarget.OFF

    fun configure(config: AudioDspConfig, licensed: Boolean) {
        val normalized = config.normalized()
        this.config = normalized
        this.licensed = licensed
        preSquelchDsp.configure(normalized)
        postSquelchDsp.configure(normalized)
    }

    /** Azzera lo stato dei filtri, per esempio dopo un cambio di frequenza. */
    fun resetFilters() {
        preSquelchDsp.reset()
        postSquelchDsp.reset()
    }

    fun process(frame: AudioFrame) {
        analysisSink?.invoke(frame)
        val target = effectiveTarget
        if (target != lastTarget) {
            // La catena che entra in uso non deve partire da uno stato vecchio.
            when (target) {
                DspTarget.LISTEN -> postSquelchDsp.reset()
                DspTarget.LISTEN_AND_RECORD -> preSquelchDsp.reset()
                DspTarget.OFF -> Unit
            }
            lastTarget = target
        }

        val pipelineFrame = if (target == DspTarget.LISTEN_AND_RECORD) {
            val processed = preSquelchDsp.process(frame.pcmBytes, frame.sampleRate)
            if (processed === frame.pcmBytes) frame else frame.copy(pcmBytes = processed)
        } else {
            frame
        }

        val result = squelch.processFrame(pipelineFrame)
        val playback = if (target == DspTarget.LISTEN) {
            postSquelchDsp.process(result.processedPcmBytes, frame.sampleRate)
        } else {
            result.processedPcmBytes
        }
        playbackSink(playback)
        recordSink(pipelineFrame)
    }
}
