package com.grabber.audio.dsp

/**
 * Riduzione del rumore con un predittore lineare adattivo NLMS ("adaptive line enhancer"): stima il
 * campione corrente dai campioni precedenti ritardati. Le componenti correlate nel tempo (voce,
 * portanti, toni) sono prevedibili e passano; il rumore a larga banda, non prevedibile, viene attenuato.
 *
 * [level] (0–1) miscela il segnale originale con quello stimato: 0 lascia l'audio invariato.
 */
class LmsNoiseReducer(
    private val taps: Int = 32,
    private val delay: Int = 1,
    private val stepSize: Double = 0.01,
    private val leakage: Double = 1e-4
) {
    init {
        require(taps > 0 && delay > 0)
    }

    /** Buffer circolare dei campioni passati; `latest` è l'indice del campione più recente. */
    private val history = DoubleArray(taps + delay)
    private val weights = DoubleArray(taps)
    private var latest = 0

    var level: Double = 0.0
        set(value) {
            field = value.coerceIn(0.0, 1.0)
        }

    /** Indice nel buffer del campione x[n - delay - k]. */
    private fun windowIndex(k: Int): Int {
        val size = history.size
        return ((latest - (delay - 1) - k) % size + size) % size
    }

    /** Elabora un campione (scala ±1). */
    fun process(x: Double): Double {
        var estimate = 0.0
        var power = 0.0
        for (k in 0 until taps) {
            val sample = history[windowIndex(k)]
            estimate += weights[k] * sample
            power += sample * sample
        }
        val error = x - estimate

        // Aggiornamento NLMS normalizzato sulla potenza della finestra, con una piccola perdita
        // che impedisce ai pesi di divergere quando il segnale cambia.
        val normalizedStep = stepSize / (power + 1e-6)
        val keep = 1 - leakage
        for (k in 0 until taps) {
            weights[k] = weights[k] * keep + normalizedStep * error * history[windowIndex(k)]
        }

        latest = (latest + 1) % history.size
        history[latest] = x
        return x + level * (estimate - x)
    }

    fun reset() {
        history.fill(0.0)
        weights.fill(0.0)
        latest = 0
    }
}
