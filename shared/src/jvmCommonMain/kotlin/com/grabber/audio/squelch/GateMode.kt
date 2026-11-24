package com.grabber.audio.squelch

/**
 * Criterio che apre il gate di ascolto o di registrazione.
 *
 * - [OFF]: nessun gate (ascolto libero, registrazione continua o a intervalli).
 * - [RF]: solo squelch RF (segnale sopra la soglia, con isteresi e hang time).
 * - [VOICE]: solo Voice Activity Detection, indipendente dalla soglia RF.
 * - [RF_AND_VOICE]: serve un segnale sopra la soglia **e** la voce rilevata dal VAD.
 *
 * Le modalità con voce richiedono la licenza (come il VAD).
 */
enum class GateMode(val usesRf: Boolean, val usesVoice: Boolean) {
    OFF(usesRf = false, usesVoice = false),
    RF(usesRf = true, usesVoice = false),
    VOICE(usesRf = false, usesVoice = true),
    RF_AND_VOICE(usesRf = true, usesVoice = true);

    fun withRf(enabled: Boolean): GateMode = of(rf = enabled, voice = usesVoice)

    fun withVoice(enabled: Boolean): GateMode = of(rf = usesRf, voice = enabled)

    /** Senza licenza le modalità con voce perdono la componente VAD. */
    fun restrictedTo(voiceAllowed: Boolean): GateMode = if (voiceAllowed) this else withVoice(false)

    companion object {
        fun of(rf: Boolean, voice: Boolean): GateMode = when {
            rf && voice -> RF_AND_VOICE
            rf -> RF
            voice -> VOICE
            else -> OFF
        }

        fun fromName(name: String?): GateMode? = entries.find { it.name == name }
    }
}
