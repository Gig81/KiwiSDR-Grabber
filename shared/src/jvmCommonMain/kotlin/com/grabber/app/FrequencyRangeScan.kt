package com.grabber.app

import com.grabber.model.ModulationMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** Limiti dei parametri della scansione di un intervallo. */
object RangeScanLimits {
    const val MIN_FREQUENCY_KHZ = 0.0
    const val MAX_FREQUENCY_KHZ = 30000.0
    const val MIN_STEP_KHZ = 0.1
    const val MAX_STEP_KHZ = 1000.0
    const val MAX_STEPS = 300_000
    const val MIN_DWELL_MS = 300
    const val MAX_DWELL_MS = 10_000
    const val DEFAULT_DWELL_MS = 1500
}

/**
 * Intervallo da scansionare: da [startKhz] a [endKhz] (inclusa se multipla del passo) con passo
 * [stepKhz] e modo [mode]. Le frequenze in [excludedKhz] vengono saltate, con una tolleranza di
 * mezzo passo.
 */
data class RangeScanParams(
    val startKhz: Double,
    val endKhz: Double,
    val stepKhz: Double,
    val mode: ModulationMode,
    val excludedKhz: List<Double> = emptyList()
)

/** Restituisce un messaggio d'errore leggibile se i parametri non sono validi, altrimenti null. */
fun validateRangeScan(params: RangeScanParams): String? {
    val (start, end, step) = Triple(params.startKhz, params.endKhz, params.stepKhz)
    return when {
        start.isNaN() || end.isNaN() || step.isNaN() -> "Valori dell'intervallo non validi."
        start < RangeScanLimits.MIN_FREQUENCY_KHZ || end > RangeScanLimits.MAX_FREQUENCY_KHZ ->
            "L'intervallo deve essere compreso tra 0 e 30.000 kHz."
        start >= end -> "La frequenza iniziale deve essere minore di quella finale."
        step < RangeScanLimits.MIN_STEP_KHZ || step > RangeScanLimits.MAX_STEP_KHZ ->
            "Il passo deve essere compreso tra 0,1 e 1.000 kHz."
        rawStepCount(start, end, step) > RangeScanLimits.MAX_STEPS ->
            "Troppi passi: aumenta il passo o riduci l'intervallo (massimo ${RangeScanLimits.MAX_STEPS})."
        FrequencyRangeScanSource(params).size == 0 -> "Tutte le frequenze dell'intervallo sono escluse."
        else -> null
    }
}

/** Numero di passi tra inizio e fine, fine inclusa se multipla del passo (tolleranza per l'errore in virgola mobile). */
private fun rawStepCount(start: Double, end: Double, step: Double): Long =
    floor((end - start) / step + 1e-9).toLong() + 1

/** Arrotonda a 1 Hz per evitare la deriva dovuta alla somma ripetuta in virgola mobile. */
internal fun roundToHz(khz: Double): Double = (khz * 1000.0).roundToLong() / 1000.0

/**
 * Passi di un intervallo di frequenze calcolati per indice (`start + i * step`), senza accumulare errori.
 * Gli indici esclusi sono calcolati una sola volta alla creazione.
 */
class FrequencyRangeScanSource(private val params: RangeScanParams) : ScanSource {
    private val allowedIndices: IntArray

    init {
        val valid = !params.startKhz.isNaN() && !params.endKhz.isNaN() && !params.stepKhz.isNaN() &&
            params.stepKhz > 0 && params.endKhz >= params.startKhz
        val total = if (valid) rawStepCount(params.startKhz, params.endKhz, params.stepKhz) else 0L
        allowedIndices = if (total <= 0 || total > RangeScanLimits.MAX_STEPS) {
            IntArray(0)
        } else {
            val tolerance = params.stepKhz / 2.0
            val excluded = params.excludedKhz
            (0 until total.toInt())
                .filter { i -> excluded.none { abs(frequencyAt(i) - it) < tolerance } }
                .toIntArray()
        }
    }

    private fun frequencyAt(index: Int): Double = roundToHz(params.startKhz + index * params.stepKhz)

    override val size: Int get() = allowedIndices.size

    override fun targetAt(index: Int): ScanTarget {
        val frequency = frequencyAt(allowedIndices[index])
        return ScanTarget(frequency, params.mode, String.format(Locale.US, "%.3f kHz", frequency))
    }
}

/** Segnale trovato durante la scansione di un intervallo. */
data class ScanHit(
    val frequencyKhz: Double,
    val mode: ModulationMode,
    val firstSeenMillis: Long,
    val lastSeenMillis: Long,
    val count: Int,
    val peakRssiDbm: Double
)

/**
 * Elenco dei segnali trovati nella sessione, dal più recente. Un nuovo rilevamento sulla stessa
 * frequenza aggiorna la voce esistente (conteggio, ultima volta, picco) invece di duplicarla.
 */
class ScanHitLog(private val maxEntries: Int = 200) {
    private val entries = ArrayList<ScanHit>()

    @Synchronized
    fun record(target: ScanTarget, rssiDbm: Double, nowMillis: Long): List<ScanHit> {
        val index = entries.indexOfFirst { abs(it.frequencyKhz - target.frequencyKhz) < 0.0005 && it.mode == target.mode }
        if (index >= 0) {
            val previous = entries.removeAt(index)
            entries.add(0, previous.copy(
                lastSeenMillis = nowMillis,
                count = previous.count + 1,
                peakRssiDbm = maxOf(previous.peakRssiDbm, rssiDbm)
            ))
        } else {
            entries.add(0, ScanHit(target.frequencyKhz, target.mode, nowMillis, nowMillis, 1, rssiDbm))
            while (entries.size > maxEntries) entries.removeAt(entries.lastIndex)
        }
        return entries.toList()
    }

    @Synchronized
    fun remove(frequencyKhz: Double): List<ScanHit> {
        entries.removeAll { abs(it.frequencyKhz - frequencyKhz) < 0.0005 }
        return entries.toList()
    }

    @Synchronized
    fun clear(): List<ScanHit> {
        entries.clear()
        return emptyList()
    }
}

/** Aggiunge una frequenza esclusa (arrotondata a 1 Hz), senza duplicati, mantenendo l'elenco ordinato. */
fun List<Double>.withRangeScanExclusion(frequencyKhz: Double): List<Double> {
    val rounded = roundToHz(frequencyKhz)
    return if (any { abs(it - rounded) < 0.0005 }) this else (this + rounded).sorted()
}

fun List<Double>.withoutRangeScanExclusion(frequencyKhz: Double): List<Double> =
    filterNot { abs(it - frequencyKhz) < 0.0005 }

/** Valori inseriti dall'utente arrotondati a 1 Hz; vanno poi controllati con [validateRangeScan]. */
fun rangeScanInput(
    startKhz: Double,
    endKhz: Double,
    stepKhz: Double,
    mode: ModulationMode,
    excludedKhz: List<Double> = emptyList()
): RangeScanParams = RangeScanParams(roundToHz(startKhz), roundToHz(endKhz), roundToHz(stepKhz), mode, excludedKhz)
