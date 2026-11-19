package com.grabber.audio.dsp

import kotlinx.serialization.Serializable

/** Dove si applica la catena DSP. "Solo registrazione" non è previsto: si registrerebbe audio diverso da quello ascoltato. */
enum class DspTarget(val displayName: String) {
    OFF("Spento"),
    LISTEN("Solo ascolto"),
    LISTEN_AND_RECORD("Ascolto e registrazione");

    companion object {
        fun fromName(name: String?): DspTarget = entries.firstOrNull { it.name == name } ?: OFF
    }
}

/** Limiti dei parametri DSP; un valore 0 per i filtri significa "disattivato". */
object DspLimits {
    /** Frequenze centrali delle bande dell'equalizzatore (Hz): shelf alle estremità, peaking in mezzo. */
    val EQ_BANDS_HZ = listOf(100, 250, 500, 1000, 1800, 2700, 4000)
    const val EQ_MIN_DB = -12.0
    const val EQ_MAX_DB = 12.0
    val HIGH_PASS_HZ = 50..1000
    val LOW_PASS_HZ = 1000..6000
    val NOTCH_HZ = 200..5000
    const val NOTCH_Q_MIN = 1.0
    const val NOTCH_Q_MAX = 50.0
    const val NOTCH_Q_DEFAULT = 10.0
}

/**
 * Configurazione della catena DSP salvata nelle impostazioni. È immutabile: ogni modifica produce
 * una nuova istanza applicata atomicamente dalla catena all'inizio del blocco audio successivo.
 */
@Serializable
data class AudioDspConfig(
    val targetName: String = DspTarget.OFF.name,
    val highPassHz: Int = 0,
    val lowPassHz: Int = 0,
    val notchHz: Int = 0,
    val notchQ: Double = DspLimits.NOTCH_Q_DEFAULT,
    val noiseReductionPercent: Int = 0,
    val eqGainsDb: List<Double> = List(DspLimits.EQ_BANDS_HZ.size) { 0.0 },
    // Stringa letterale: `DspPreset.NEUTRAL.name` qui creerebbe un ciclo nell'inizializzazione dell'enum.
    val presetName: String = "NEUTRAL"
) {
    val target: DspTarget get() = DspTarget.fromName(targetName)

    /** Nessun filtro attivo: la catena lascia l'audio invariato, indipendentemente dalla destinazione. */
    val isNeutral: Boolean
        get() = highPassHz == 0 && lowPassHz == 0 && notchHz == 0 && noiseReductionPercent == 0 &&
            eqGainsDb.all { it == 0.0 }

    /** Riconduce ogni valore nei limiti (file modificati a mano o versioni diverse). */
    fun normalized(): AudioDspConfig = copy(
        targetName = target.name,
        highPassHz = highPassHz.inRangeOrOff(DspLimits.HIGH_PASS_HZ),
        lowPassHz = lowPassHz.inRangeOrOff(DspLimits.LOW_PASS_HZ),
        notchHz = notchHz.inRangeOrOff(DspLimits.NOTCH_HZ),
        notchQ = if (notchQ.isNaN()) DspLimits.NOTCH_Q_DEFAULT else notchQ.coerceIn(DspLimits.NOTCH_Q_MIN, DspLimits.NOTCH_Q_MAX),
        noiseReductionPercent = noiseReductionPercent.coerceIn(0, 100),
        eqGainsDb = List(DspLimits.EQ_BANDS_HZ.size) { i ->
            val gain = eqGainsDb.getOrNull(i) ?: 0.0
            if (gain.isNaN()) 0.0 else gain.coerceIn(DspLimits.EQ_MIN_DB, DspLimits.EQ_MAX_DB)
        },
        presetName = DspPreset.fromName(presetName)?.name ?: DspPreset.CUSTOM.name
    )

    /** Applica i filtri del preset mantenendo la destinazione scelta. */
    fun withPreset(preset: DspPreset): AudioDspConfig =
        preset.config.copy(targetName = targetName, presetName = preset.name)

    private fun Int.inRangeOrOff(range: IntRange): Int = if (this <= 0) 0 else coerceIn(range.first, range.last)
}

/** Preset predefiniti. `CUSTOM` indica una configurazione modificata a mano. */
enum class DspPreset(val displayName: String, val config: AudioDspConfig) {
    NEUTRAL("Neutro", AudioDspConfig()),
    SSB_VOICE(
        "Voce SSB",
        AudioDspConfig(highPassHz = 300, lowPassHz = 2800, eqGainsDb = listOf(-3.0, -1.0, 0.0, 1.0, 3.0, 2.0, 0.0))
    ),
    AM_BROADCAST(
        "AM broadcast",
        AudioDspConfig(highPassHz = 80, lowPassHz = 5000, eqGainsDb = listOf(2.0, 1.0, 0.0, 0.0, 0.0, 1.0, 2.0))
    ),
    CW_NARROW("CW stretto", AudioDspConfig(highPassHz = 450, lowPassHz = 1000)),
    QRM_REDUCTION(
        "Riduzione QRM",
        AudioDspConfig(highPassHz = 250, lowPassHz = 3000, noiseReductionPercent = 60)
    ),
    CUSTOM("Personalizzato", AudioDspConfig());

    companion object {
        fun fromName(name: String?): DspPreset? = entries.firstOrNull { it.name == name }
        val selectable: List<DspPreset> get() = entries.filter { it != CUSTOM }
    }
}
