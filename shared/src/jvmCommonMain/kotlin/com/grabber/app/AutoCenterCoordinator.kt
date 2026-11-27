package com.grabber.app

import com.grabber.audio.analysis.AudioSpectrumAnalyzer
import com.grabber.audio.analysis.SignalCenterEstimator
import com.grabber.model.AudioFrame
import com.grabber.model.ModulationMode
import com.grabber.model.Passband
import com.grabber.model.digital.DigitalMode
import com.grabber.model.digital.DigitalModeCatalog
import com.grabber.waterfall.WaterfallFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/** Origine di una sintonia: l'AFC agisce solo su [USER_TYPED] e [WATERFALL_CLICK] (decisione D9). */
enum class TuneSource { USER_TYPED, WATERFALL_CLICK, PRESET, STEP, SCAN, AFC, OTHER }

/** Esito dell'ultima correzione AFC, mostrato nella console. */
data class AutoCenterResult(
    val outcome: Outcome,
    /** Correzione applicata (o stimata) in Hz. */
    val offsetHz: Double? = null,
    val fromWaterfall: Boolean = false,
    val atMillis: Long
) {
    enum class Outcome(val label: String) {
        APPLIED("corretto"),
        CENTERED("già centrato"),
        NO_SIGNAL("nessun segnale"),
        OUT_OF_RANGE("fuori portata"),
        NOT_APPLICABLE("non applicabile")
    }
}

/** Sintonia corrente vista dall'AFC. */
data class AfcContext(
    val modulation: ModulationMode,
    val frequencyKhz: Double,
    val passband: Passband,
    val activeDecoder: DigitalMode?,
    val fskCenterHz: Int,
    val fskShiftHz: Int
)

/**
 * Autocentraggio one-shot dopo una sintonia dell'utente (§7 del piano): attesa dell'assestamento,
 * misura sullo spettro W/F (stima grossolana e ampia) e/o sull'audio (stima fine entro la banda),
 * correzione con [applyCorrection] e al massimo una rifinitura. Una nuova sintonia annulla la misura.
 */
class AutoCenterCoordinator(
    private val scope: CoroutineScope,
    private val isEnabled: () -> Boolean,
    private val context: () -> AfcContext,
    private val latestWaterfall: () -> WaterfallFrame?,
    private val applyCorrection: (newFrequencyKhz: Double) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val settleMs: Long = SETTLE_MS,
    private val measureMs: Long = MEASURE_MS
) {
    companion object {
        const val SETTLE_MS = 400L
        const val MEASURE_MS = 900L
        private const val TAG = "AutoCenter"
        /** Risoluzione minima del waterfall per usarlo (Hz per bin). */
        private const val MAX_WF_BIN_HZ_NARROW = 30.0
        private const val MAX_WF_BIN_HZ_WIDE = 120.0
    }

    private val _lastResult = MutableStateFlow<AutoCenterResult?>(null)
    val lastResult: StateFlow<AutoCenterResult?> = _lastResult.asStateFlow()

    private val analyzer = AudioSpectrumAnalyzer(size = 4096)
    @Volatile private var capturing = false
    private var job: Job? = null

    /** Vero mentre l'AFC raccoglie audio (il tap di analisi lo usa per decidere se copiare i frame). */
    val isCapturing: Boolean get() = capturing

    /** Da chiamare a ogni sintonia: misura solo per le sintonie dell'utente, annulla quelle in corso. */
    fun onTuned(source: TuneSource) {
        if (source == TuneSource.AFC) return
        job?.cancel()
        capturing = false
        if (source != TuneSource.USER_TYPED && source != TuneSource.WATERFALL_CLICK) return
        if (!isEnabled()) return
        job = scope.launch { runMeasurement(refinementAllowed = true) }
    }

    fun cancel() {
        job?.cancel()
        capturing = false
    }

    /** Frame audio dal tap di analisi (un solo flusso, in ordine). */
    fun onAudio(frame: AudioFrame) {
        if (!capturing) return
        val samples = frame.pcmBytes.size / 2
        val data = FloatArray(samples) { i ->
            ((frame.pcmBytes[2 * i].toInt() and 0xFF) or (frame.pcmBytes[2 * i + 1].toInt() shl 8)).toShort() / 32768f
        }
        synchronized(analyzer) { analyzer.push(data, samples, frame.sampleRate) }
    }

    private suspend fun runMeasurement(refinementAllowed: Boolean) {
        delay(settleMs)
        val ctx = context()
        val plan = planFor(ctx)
        if (plan == null) {
            publish(AutoCenterResult.Outcome.NOT_APPLICABLE)
            return
        }
        synchronized(analyzer) { analyzer.reset() }
        capturing = plan.useAudio
        delay(measureMs)
        capturing = false

        var offset: Double? = null
        var fromWaterfall = false
        if (plan.useWaterfall) {
            offset = waterfallOffset(ctx, plan)
            fromWaterfall = offset != null
        }
        if (offset == null && plan.useAudio) offset = audioOffset(ctx, plan)

        if (offset == null) {
            publish(AutoCenterResult.Outcome.NO_SIGNAL)
            return
        }
        if (abs(offset) > plan.searchHz) {
            publish(AutoCenterResult.Outcome.OUT_OF_RANGE, offset, fromWaterfall)
            return
        }
        if (abs(offset) < plan.minCorrectionHz) {
            publish(AutoCenterResult.Outcome.CENTERED, offset, fromWaterfall)
            return
        }
        val rounded = (offset / plan.roundingHz).roundToLong() * plan.roundingHz
        val newFrequency = ctx.frequencyKhz + rounded / 1000.0
        AppLogger.debug(TAG, "AFC: correzione ${rounded.toInt()} Hz (${if (fromWaterfall) "waterfall" else "audio"}) su ${ctx.modulation.displayName}")
        applyCorrection(newFrequency)
        publish(AutoCenterResult.Outcome.APPLIED, rounded, fromWaterfall)
        // Dopo una stima grossolana dal waterfall, una sola rifinitura sull'audio.
        if (refinementAllowed && fromWaterfall && plan.useAudio) runMeasurement(refinementAllowed = false)
    }

    private fun publish(outcome: AutoCenterResult.Outcome, offset: Double? = null, fromWaterfall: Boolean = false) {
        _lastResult.value = AutoCenterResult(outcome, offset, fromWaterfall, clock())
    }

    /**
     * Parametri della misura per la sintonia corrente: riferimento audio, finestra di ricerca, fonti.
     * Null se l'AFC non si applica (USB/LSB voce, decoder che lavorano su tutta la banda).
     */
    internal data class Plan(
        val kind: Kind,
        val referenceAudioHz: Double,
        val searchHz: Double,
        val minCorrectionHz: Double,
        val roundingHz: Double,
        val useAudio: Boolean,
        val useWaterfall: Boolean
    ) {
        enum class Kind { TONE, CARRIER, CENTROID_NFM, FSK, FAX }
    }

    internal fun planFor(ctx: AfcContext): Plan? {
        when (ctx.activeDecoder) {
            DigitalMode.RTTY, DigitalMode.NAVTEX, DigitalMode.DSC ->
                return Plan(Plan.Kind.FSK, ctx.fskCenterHz.toDouble(), ctx.fskShiftHz.toDouble(), 5.0, 1.0, useAudio = true, useWaterfall = false)
            DigitalMode.FAX -> return Plan(Plan.Kind.FAX, 1900.0, 300.0, 15.0, 1.0, useAudio = true, useWaterfall = false)
            null, DigitalMode.CW -> Unit
            else -> return null
        }
        return when (ctx.modulation) {
            ModulationMode.CW -> Plan(Plan.Kind.TONE, ctx.passband.centerHz, 1000.0, 15.0, 1.0, useAudio = true, useWaterfall = true)
            ModulationMode.AM -> Plan(Plan.Kind.CARRIER, 0.0, 5000.0, 100.0, 10.0, useAudio = false, useWaterfall = true)
            ModulationMode.NFM -> Plan(Plan.Kind.CENTROID_NFM, 0.0, 6000.0, 100.0, 10.0, useAudio = false, useWaterfall = true)
            ModulationMode.USB, ModulationMode.LSB -> null
        }
    }

    /** Scostamento (Hz) del segnale dalla frequenza mostrata, dallo spettro W/F. */
    private fun waterfallOffset(ctx: AfcContext, plan: Plan): Double? {
        val frame = latestWaterfall() ?: return null
        if (frame.binCount < 16) return null
        val binHz = frame.spanKhz * 1000.0 / (frame.binCount - 1)
        val maxBin = if (plan.kind == Plan.Kind.TONE) MAX_WF_BIN_HZ_NARROW else MAX_WF_BIN_HZ_WIDE
        if (binHz > maxBin) return null
        val signalKhz = ctx.frequencyKhz
        if (signalKhz < frame.startFreqKhz || signalKhz > frame.stopFreqKhz) return null
        val power = DoubleArray(frame.binCount) { 10.0.pow(frame.getDbm(it) / 10.0) }
        val startHz = frame.startFreqKhz * 1000.0
        val found = when (plan.kind) {
            Plan.Kind.CENTROID_NFM -> SignalCenterEstimator.centroid(power, startHz, binHz, signalKhz * 1000 - plan.searchHz, signalKhz * 1000 + plan.searchHz)
            else -> SignalCenterEstimator.nearestPeak(
                power, startHz, binHz, signalKhz * 1000.0, plan.searchHz,
                minSnrDb = 10.0,
                maxWidthHz = if (plan.kind == Plan.Kind.TONE) 150.0 + 2 * binHz else Double.MAX_VALUE
            )?.frequencyHz
        } ?: return null
        return found - signalKhz * 1000.0
    }

    /** Scostamento (Hz) dall'audio: il segnale deve essere già dentro la banda passante. */
    private fun audioOffset(ctx: AfcContext, plan: Plan): Double? {
        val (power, binHz) = synchronized(analyzer) {
            if (analyzer.blocks == 0) return null
            analyzer.power() to analyzer.binHz
        }
        val low = minOf(ctx.passband.lowCut, ctx.passband.highCut).toDouble().coerceAtLeast(0.0)
        val high = maxOf(abs(ctx.passband.lowCut), abs(ctx.passband.highCut)).toDouble()
        // In LSB l'audio è invertito: qui si gestiscono solo CW e i decoder (bande positive).
        val inBand = { hz: Double -> hz in low..high }
        val found = when (plan.kind) {
            Plan.Kind.FSK -> SignalCenterEstimator.fskCenter(power, binHz, plan.referenceAudioHz, plan.searchHz.coerceAtLeast(50.0), plan.searchHz / 2)
            Plan.Kind.FAX -> SignalCenterEstimator.centroid(power, 0.0, binHz, 1300.0, 2500.0)
            else -> SignalCenterEstimator.nearestPeak(power, 0.0, binHz, plan.referenceAudioHz, (high - low) / 2, minSnrDb = 10.0, maxWidthHz = 150.0)?.frequencyHz
        } ?: return null
        if (!inBand(found)) return null
        return found - plan.referenceAudioHz
    }
}

/** Contesto AFC dai parametri dei decoder (centro e shift FSK del modo attivo). */
fun afcFskParameters(mode: DigitalMode?, rttyCenter: Int, rttyShift: Int): Pair<Int, Int> = when (mode) {
    DigitalMode.NAVTEX, DigitalMode.DSC -> DigitalModeCatalog.SITOR_CENTER_HZ to DigitalModeCatalog.SITOR_SHIFT_HZ
    else -> rttyCenter to rttyShift
}
