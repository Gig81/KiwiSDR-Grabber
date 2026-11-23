package com.grabber.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "ConnectionResume"

/** Attesa dopo l'autenticazione prima di riprendere: il server deve aver applicato sintonia e audio. */
internal const val RESUME_DELAY_MS = 600L

/** Tempo massimo di attesa dell'autenticazione (`badp=0`) dopo la riconnessione. */
internal const val RESUME_AUTH_TIMEOUT_MS = 10_000L

/** Punto da cui riprendere una scansione: tipo e indice (da 0) del passo su cui si trovava. */
data class ScanResumePoint(val kind: ScanKind, val stepIndex: Int)

/** Punto di ripresa della scansione in corso, oppure null se non c'è una scansione attiva. */
fun ScanState.resumePoint(): ScanResumePoint? = when (this) {
    is ScanState.Scanning -> ScanResumePoint(kind, position - 1)
    is ScanState.SignalFound -> ScanResumePoint(kind, position - 1)
    ScanState.Idle -> null
}

/**
 * Ricorda registrazione e scansione attive quando la connessione cade per motivi di rete e le
 * riprende quando il client si riconnette, su desktop e Android.
 *
 * Il controller chiama [onConnectionLost] alla perdita non voluta della connessione (stato
 * `Reconnecting`) **prima** di fermare la scansione, [onConnected] a connessione ristabilita e
 * [clear] alla disconnessione voluta dall'utente o al rifiuto del server: in quei casi non si
 * riprende nulla.
 */
class ConnectionResumeCoordinator(
    private val scope: CoroutineScope,
    private val isAuthenticated: StateFlow<Boolean>,
    private val scanState: StateFlow<ScanState>,
    private val isRecording: () -> Boolean,
    /** Ferma la registrazione in corso (il file viene chiuso e salvato) ricordandone i parametri. */
    private val suspendRecording: () -> Unit,
    private val resumeRecording: () -> Unit,
    private val resumeScan: (ScanResumePoint) -> Unit,
    private val resumeDelayMillis: Long = RESUME_DELAY_MS,
    private val authTimeoutMillis: Long = RESUME_AUTH_TIMEOUT_MS
) {
    private val lock = Any()
    private var recordingToResume = false
    private var scanToResume: ScanResumePoint? = null
    private var resumeJob: Job? = null

    /** Vero se alla riconnessione c'è qualcosa da riprendere. */
    val hasPendingResume: Boolean get() = synchronized(lock) { recordingToResume || scanToResume != null }

    /**
     * Connessione persa per motivi di rete. Le chiamate successive durante i tentativi di
     * riconnessione non cancellano quanto già memorizzato.
     */
    fun onConnectionLost() {
        val scanPoint = scanState.value.resumePoint()
        val wasRecording = isRecording()
        synchronized(lock) {
            resumeJob?.cancel()
            resumeJob = null
            if (scanPoint != null) scanToResume = scanPoint
            if (wasRecording) recordingToResume = true
        }
        if (wasRecording) {
            AppLogger.info(TAG, "Connessione persa durante la registrazione: verrà ripresa alla riconnessione")
            suspendRecording()
        }
        if (scanPoint != null) {
            AppLogger.info(TAG, "Connessione persa durante la scansione ${scanPoint.kind}: verrà ripresa dal passo ${scanPoint.stepIndex + 1}")
        }
    }

    /** Connessione ristabilita: dopo l'autenticazione riprende registrazione e scansione memorizzate. */
    fun onConnected() {
        synchronized(lock) {
            if (!recordingToResume && scanToResume == null) return
            resumeJob?.cancel()
            resumeJob = scope.launch {
                val authenticated = withTimeoutOrNull(authTimeoutMillis) { isAuthenticated.first { it } } != null
                if (!authenticated) {
                    AppLogger.warn(TAG, "Autenticazione non confermata dopo la riconnessione: ripresa rinviata")
                    return@launch
                }
                delay(resumeDelayMillis)
                val (recording, scan) = synchronized(lock) {
                    val pending = recordingToResume to scanToResume
                    recordingToResume = false
                    scanToResume = null
                    resumeJob = null
                    pending
                }
                if (recording) {
                    AppLogger.info(TAG, "Riconnessione riuscita: ripresa della registrazione")
                    resumeRecording()
                }
                if (scan != null) {
                    AppLogger.info(TAG, "Riconnessione riuscita: ripresa della scansione ${scan.kind}")
                    resumeScan(scan)
                }
            }
        }
    }

    /** L'utente ha sintonizzato o fermato la scansione durante la riconnessione: non va ripresa. */
    fun forgetScan() {
        synchronized(lock) { scanToResume = null }
    }

    /** L'utente ha fermato la registrazione: non va ripresa. */
    fun forgetRecording() {
        synchronized(lock) { recordingToResume = false }
    }

    /** Disconnessione voluta o rifiuto del server: dimentica quanto da riprendere. */
    fun clear() {
        synchronized(lock) {
            resumeJob?.cancel()
            resumeJob = null
            recordingToResume = false
            scanToResume = null
        }
    }
}
