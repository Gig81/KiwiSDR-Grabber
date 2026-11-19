package com.grabber.audio

import com.grabber.audio.dsp.AudioDspConfig
import com.grabber.audio.dsp.DspPreset
import com.grabber.audio.dsp.DspTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * Stato e comandi del DSP condivisi da `KiwiAppController` (desktop) e `MobileAppController` (Android).
 *
 * Ogni modifica si applica subito alla [pipeline] e viene salvata con [persist] dopo una breve pausa
 * (i cursori generano molte modifiche al secondo). Senza licenza ([isLicensed]) i comandi vengono
 * rifiutati (restituiscono false) e la pipeline tratta il DSP come spento.
 */
class AudioDspControl(
    private val scope: CoroutineScope,
    private val pipeline: AudioPipeline,
    initialConfig: AudioDspConfig,
    private val isLicensed: () -> Boolean,
    private val persist: (AudioDspConfig) -> Unit,
    private val saveDebounceMillis: Long = 300L
) {
    private val _config = MutableStateFlow(initialConfig.normalized())

    /** Configurazione corrente (anche se non ancora salvata su disco). */
    val config: StateFlow<AudioDspConfig> = _config.asStateFlow()

    private val _licensed = MutableStateFlow(isLicensed())

    /** Vero se la catena sta elaborando l'audio: destinazione attiva, almeno un filtro, licenza valida. */
    val isActive: StateFlow<Boolean> = combine(_config, _licensed) { config, licensed -> isActive(config, licensed) }
        .stateIn(scope, SharingStarted.Eagerly, isActive(_config.value, _licensed.value))

    private val saveLock = Any()
    private var saveJob: Job? = null

    init {
        apply()
    }

    /** Da chiamare quando cambia la licenza: il DSP si accende o si spegne senza riavvio. */
    fun refreshLicense() {
        _licensed.value = isLicensed()
        apply()
    }

    fun setTarget(target: DspTarget): Boolean = update { it.copy(targetName = target.name) }

    fun setEqGain(band: Int, gainDb: Double): Boolean = update { config ->
        if (band !in config.eqGainsDb.indices) return@update config
        config.copy(eqGainsDb = config.eqGainsDb.toMutableList().also { it[band] = gainDb }).custom()
    }

    /** Frequenze dei filtri in Hz; 0 disattiva il filtro. */
    fun setHighPass(hz: Int): Boolean = update { it.copy(highPassHz = hz).custom() }

    fun setLowPass(hz: Int): Boolean = update { it.copy(lowPassHz = hz).custom() }

    fun setNotch(hz: Int, q: Double = _config.value.notchQ): Boolean = update { it.copy(notchHz = hz, notchQ = q).custom() }

    fun setNoiseReduction(percent: Int): Boolean = update { it.copy(noiseReductionPercent = percent).custom() }

    fun applyPreset(preset: DspPreset): Boolean = update { it.withPreset(preset) }

    /** Toglie tutti i filtri mantenendo la destinazione. */
    fun reset(): Boolean = update { it.withPreset(DspPreset.NEUTRAL) }

    /** Confronto A/B: con `true` l'audio torna originale finché non si richiama con `false`. Non viene salvato. */
    fun setBypass(bypassed: Boolean) {
        if (!isLicensed()) return
        if (bypassed) {
            pipeline.configure(_config.value.copy(targetName = DspTarget.OFF.name), licensed = true)
        } else {
            apply()
        }
    }

    /** Salva subito una modifica ancora in attesa (chiusura dell'app). */
    fun flush() {
        val job = synchronized(saveLock) { saveJob.also { saveJob = null } }
        if (job != null && job.isActive) {
            job.cancel()
            persist(_config.value)
        }
    }

    private fun AudioDspConfig.custom(): AudioDspConfig = copy(presetName = DspPreset.CUSTOM.name)

    private fun update(transform: (AudioDspConfig) -> AudioDspConfig): Boolean {
        if (!isLicensed()) return false
        val updated = _config.updateAndGet { transform(it).normalized() }
        apply()
        synchronized(saveLock) {
            saveJob?.cancel()
            saveJob = scope.launch {
                delay(saveDebounceMillis)
                persist(updated)
            }
        }
        return true
    }

    private fun apply() {
        pipeline.configure(_config.value, licensed = isLicensed())
    }

    private fun isActive(config: AudioDspConfig, licensed: Boolean): Boolean =
        licensed && config.target != DspTarget.OFF && !config.isNeutral
}
