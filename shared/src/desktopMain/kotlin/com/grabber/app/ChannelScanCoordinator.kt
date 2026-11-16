package com.grabber.app

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

private const val TAG = "ChannelScan"

/** Tempo di assestamento dopo il cambio canale: i primi frame possono riferirsi ancora alla frequenza precedente. */
internal const val SCAN_SETTLE_MS = 600L

/** Stato della scansione dei canali preferiti. `position` parte da 1. */
sealed interface ChannelScanState {
    data object Idle : ChannelScanState

    /** Ascolto del canale in attesa di un segnale sopra la soglia dello squelch. */
    data class Scanning(val channel: FavoriteFrequency, val position: Int, val total: Int) : ChannelScanState

    /** Segnale presente: la scansione resta sul canale finché lo squelch non si richiude. */
    data class SignalFound(val channel: FavoriteFrequency, val position: Int, val total: Int) : ChannelScanState
}

/**
 * Scorre ciclicamente i preferiti selezionati per la scansione. Su ogni canale ascolta per il tempo
 * di permanenza; se lo squelch RF si apre resta in ascolto e, con la ripresa automatica, riparte
 * quando il gate si richiude (dopo l'hang time), altrimenti si ferma sul canale.
 *
 * La lista dei canali e le impostazioni vengono rilette a ogni passo, così le modifiche fatte
 * durante la scansione hanno effetto subito.
 */
class ChannelScanCoordinator(
    private val scope: CoroutineScope,
    private val selectedChannels: () -> List<FavoriteFrequency>,
    private val dwellMillis: () -> Long,
    private val autoResume: () -> Boolean,
    private val signalGateOpen: StateFlow<Boolean>,
    private val tuneTo: (FavoriteFrequency) -> Unit,
    private val resetSignalDetection: () -> Unit,
    private val onStoppedOnSignal: (FavoriteFrequency) -> Unit = {},
    private val settleMillis: Long = SCAN_SETTLE_MS
) {
    private val _state = MutableStateFlow<ChannelScanState>(ChannelScanState.Idle)
    val state: StateFlow<ChannelScanState> = _state.asStateFlow()

    /** Protegge `job` e la sintonia: dopo `stop()` la scansione non cambia più frequenza. */
    private val lock = Any()
    private var job: Job? = null

    val isActive: Boolean get() = synchronized(lock) { job?.isActive == true }

    /** Avvia la scansione; restituisce false se è già attiva o se nessun canale è selezionato. */
    fun start(): Boolean = synchronized(lock) {
        if (job?.isActive == true) return false
        if (selectedChannels().isEmpty()) return false
        // LAZY: `job` deve essere assegnato prima che la coroutine provi a sintonizzare.
        val newJob = scope.launch(start = CoroutineStart.LAZY) { runScan() }
        job = newJob
        newJob.invokeOnCompletion { finishedJob(newJob) }
        newJob.start()
        AppLogger.info(TAG, "Scansione avviata su ${selectedChannels().size} canali")
        true
    }

    fun stop() {
        val stopped = synchronized(lock) {
            val current = job ?: return
            job = null
            current.cancel()
            true
        }
        if (stopped) {
            _state.value = ChannelScanState.Idle
            AppLogger.info(TAG, "Scansione fermata")
        }
    }

    private fun finishedJob(finished: Job) = synchronized(lock) {
        if (job === finished) {
            job = null
            _state.value = ChannelScanState.Idle
        }
    }

    private suspend fun runScan() {
        var nextIndex = 0
        while (true) {
            val channels = selectedChannels()
            if (channels.isEmpty()) {
                AppLogger.info(TAG, "Nessun canale selezionato: scansione terminata")
                return
            }
            val index = nextIndex % channels.size
            val channel = channels[index]
            if (!tuneIfStillScanning(channel)) return
            _state.value = ChannelScanState.Scanning(channel, index + 1, channels.size)

            delay(settleMillis)
            resetSignalDetection()
            val signalFound = withTimeoutOrNull(dwellMillis().coerceAtLeast(1L)) { signalGateOpen.first { it } } != null

            if (signalFound) {
                _state.value = ChannelScanState.SignalFound(channel, index + 1, channels.size)
                AppLogger.info(TAG, "Segnale su ${channel.name} (${channel.frequencyKhz} kHz)")
                if (!autoResume()) {
                    onStoppedOnSignal(channel)
                    return
                }
                signalGateOpen.first { !it }
            }
            nextIndex = index + 1
        }
    }

    private fun tuneIfStillScanning(channel: FavoriteFrequency): Boolean = synchronized(lock) {
        val current = job
        if (current == null || !current.isActive) return false
        tuneTo(channel)
        true
    }
}
