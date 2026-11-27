package com.grabber.model

/**
 * Banda passante audio del canale, in Hz rispetto alla portante (valori `low_cut`/`high_cut`
 * del comando `SET mod=…` di KiwiSDR).
 */
data class Passband(val lowCut: Int, val highCut: Int) {
    /** Centro della banda rispetto alla portante, in Hz. */
    val centerHz: Double get() = (lowCut + highCut) / 2.0

    val widthHz: Int get() = highCut - lowCut

    /**
     * Limiti della banda rispetto alla frequenza mostrata, in kHz. In CW la frequenza mostrata è già
     * quella del segnale (portante + centro banda), quindi la banda è centrata sulla frequenza.
     */
    fun relativeToDisplayKhz(mode: ModulationMode): Pair<Double, Double> {
        val shift = if (mode == ModulationMode.CW) centerHz else 0.0
        return (lowCut - shift) / 1000.0 to (highCut - shift) / 1000.0
    }

    fun isDefaultFor(mode: ModulationMode): Boolean = this == defaultFor(mode)

    companion object {
        fun defaultFor(mode: ModulationMode) = Passband(mode.defaultLowCut, mode.defaultHighCut)
    }
}
