package com.grabber.app

import com.grabber.model.ModulationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "Scan"

/** Tempo di assestamento dopo il cambio frequenza: i primi frame possono riferirsi ancora alla frequenza precedente. */
internal const val SCAN_SETTLE_MS = 600L

/** Tipo di scansione: canali preferiti selezionati o intervallo di frequenze. */
enum class ScanKind { CHANNELS, RANGE }

/** Un passo della scansione: frequenza e modo da sintonizzare, con un'etichetta per la UI. */
data class ScanTarget(
    val frequencyKhz: Double,
    val mode: ModulationMode,
    val label: String
)

fun FavoriteFrequency.toScanTarget(): ScanTarget = ScanTarget(frequencyKhz, mode, name)

/** Sequenza dei passi di una scansione, letta per indice (un intervallo può avere decine di migliaia di passi). */
interface ScanSource {
    val size: Int
    fun targetAt(index: Int): ScanTarget
}

/** Sorgente su una lista già pronta, usata per i canali preferiti. */
class ListScanSource(private val targets: List<ScanTarget>) : ScanSource {
    override val size: Int get() = targets.size
    override fun targetAt(index: Int): ScanTarget = targets[index]
}

/**
 * Parametri di una scansione. [source], [dwellMillis] e [autoResume] vengono riletti a ogni passo,
 * così le modifiche fatte durante la scansione hanno effetto dal passo successivo.
 */
class ScanPlan(
    val kind: ScanKind,
    val source: () -> ScanSource,
    val dwellMillis: () -> Long,
    val autoResume: () -> Boolean
)

/** Stato della scansione. `position` parte da 1. */
sealed interface ScanState {
    data object Idle : ScanState

    /** Ascolto del passo in attesa di un segnale sopra la soglia dello squelch. */
    data class Scanning(val kind: ScanKind, val target: ScanTarget, val position: Int, val total: Int) : ScanState

    /** Segnale presente: la scansione resta sul passo finché lo squelch non si richiude. */
    data class SignalFound(val kind: ScanKind, val target: ScanTarget, val position: Int, val total: Int) : ScanState
}

/**
 * Motore unico delle scansioni (canali preferiti e intervallo di frequenze). Su ogni passo attende
 * l'assestamento, azzera il rilevamento del segnale e ascolta per il tempo di permanenza; se il gate RF
 * dello squelch si apre resta in ascolto e, con la ripresa automatica, riparte quando il gate si
 * richiude (dopo l'hang time), altrimenti si ferma sul passo.
 *
 * Una sola scansione alla volta: [start] rifiuta una seconda scansione finché la prima è attiva.
 */
class ScanCoordinator(
    private val scope: CoroutineScope,
    private val signalGateOpen: StateFlow<Boolean>,
    private val tuneTo: (ScanTarget) -> Unit,
    private val resetSignalDetection: () -> Unit,
    private val onSignalFound: (ScanKind, ScanTarget) -> Unit = { _, _ -> },
    private val onStoppedOnSignal: (ScanKind, ScanTarget) -> Unit = { _, _ -> },
    private val settleMillis: Long = SCAN_SETTLE_MS
) {
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    /** Protegge `job` e la sintonia: dopo `stop()` la scansione non cambia più frequenza. */
    private val lock = Any()
    private var job: Job? = null
    private var currentKind: ScanKind? = null

    val isActive: Boolean get() = synchronized(lock) { job?.isActive == true }

    /** Tipo della scansione in corso, oppure null. */
    val activeKind: ScanKind? get() = synchronized(lock) { if (job?.isActive == true) currentKind else null }

    /**
     * Avvia la scansione dal passo [startIndex] (0 = primo; oltre la fine riparte dall'inizio);
     * restituisce false se una scansione è già attiva o se non ci sono passi.
     */
    fun start(plan: ScanPlan, startIndex: Int = 0): Boolean = synchronized(lock) {
        if (job?.isActive == true) return false
        val steps = plan.source().size
        if (steps == 0) return false
        // LAZY: `job` deve essere assegnato prima che la coroutine provi a sintonizzare.
        val newJob = scope.launch(start = CoroutineStart.LAZY) { runScan(plan, startIndex.coerceAtLeast(0)) }
        job = newJob
        currentKind = plan.kind
        newJob.invokeOnCompletion { finishedJob(newJob) }
        newJob.start()
        AppLogger.info(TAG, "Scansione ${plan.kind} avviata su $steps passi")
        true
    }

    fun stop() {
        val stopped = synchronized(lock) {
            val current = job ?: return
            job = null
            currentKind = null
            current.cancel()
            true
        }
        if (stopped) {
            _state.value = ScanState.Idle
            AppLogger.info(TAG, "Scansione fermata")
        }
    }

    private fun finishedJob(finished: Job) = synchronized(lock) {
        if (job === finished) {
            job = null
            currentKind = null
            _state.value = ScanState.Idle
        }
    }

    private suspend fun runScan(plan: ScanPlan, startIndex: Int) {
        var nextIndex = startIndex
        while (true) {
            val source = plan.source()
            if (source.size == 0) {
                AppLogger.info(TAG, "Nessun passo da scansionare: scansione terminata")
                return
            }
            val index = nextIndex % source.size
            val target = source.targetAt(index)
            if (!tuneIfStillScanning(target)) return
            _state.value = ScanState.Scanning(plan.kind, target, index + 1, source.size)

            delay(settleMillis)
            resetSignalDetection()
            val dwell = plan.dwellMillis().coerceAtLeast(1L)
            val signalFound = withTimeoutOrNull(dwell) { signalGateOpen.first { it } } != null

            if (signalFound) {
                _state.value = ScanState.SignalFound(plan.kind, target, index + 1, source.size)
                AppLogger.info(TAG, "Segnale su ${target.label} (${target.frequencyKhz} kHz)")
                onSignalFound(plan.kind, target)
                if (!plan.autoResume()) {
                    onStoppedOnSignal(plan.kind, target)
                    return
                }
                signalGateOpen.first { !it }
            }
            nextIndex = index + 1
        }
    }

    private fun tuneIfStillScanning(target: ScanTarget): Boolean = synchronized(lock) {
        val current = job
        if (current == null || !current.isActive) return false
        tuneTo(target)
        true
    }
}
