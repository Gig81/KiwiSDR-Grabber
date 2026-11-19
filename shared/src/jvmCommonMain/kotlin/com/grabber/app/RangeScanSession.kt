package com.grabber.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Piano e segnali trovati della scansione di un intervallo, condivisi da desktop e Android.
 * I parametri sono letti dalle impostazioni a ogni passo; la sorgente dei passi viene ricreata
 * solo quando cambiano, non a ogni passo.
 */
class RangeScanSession(
    private val params: () -> RangeScanParams,
    dwellMillis: () -> Long,
    autoResume: () -> Boolean
) {
    private val hitLog = ScanHitLog()
    private val _hits = MutableStateFlow<List<ScanHit>>(emptyList())

    /** Segnali trovati in questa sessione, dal più recente. */
    val hits: StateFlow<List<ScanHit>> = _hits.asStateFlow()

    @Volatile private var cachedSource: Pair<RangeScanParams, FrequencyRangeScanSource>? = null

    val plan = ScanPlan(
        kind = ScanKind.RANGE,
        source = ::source,
        dwellMillis = dwellMillis,
        autoResume = autoResume
    )

    /** Errore leggibile se i parametri salvati non sono validi, altrimenti null. */
    fun validationError(): String? = validateRangeScan(params())

    fun recordHit(target: ScanTarget, rssiDbm: Double, nowMillis: Long = System.currentTimeMillis()) {
        _hits.value = hitLog.record(target, rssiDbm, nowMillis)
    }

    fun removeHit(frequencyKhz: Double) {
        _hits.value = hitLog.remove(frequencyKhz)
    }

    fun clearHits() {
        _hits.value = hitLog.clear()
    }

    private fun source(): ScanSource {
        val current = params()
        cachedSource?.let { (cachedParams, source) -> if (cachedParams == current) return source }
        return FrequencyRangeScanSource(current).also { cachedSource = current to it }
    }
}
