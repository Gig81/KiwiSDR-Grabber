package com.grabber.update

import com.grabber.app.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "UpdateChecker"
private const val TIMEOUT_MS = 6_000

/**
 * Controlla su GitHub se esiste una release più recente di [currentVersion]. Usato da entrambi i
 * controller all'avvio; la richiesta è anonima (nessun dato dell'utente) e un errore di rete
 * produce solo [UpdateCheckState.Unavailable], senza bloccare l'app.
 */
class UpdateChecker(
    private val currentVersion: String,
    private val fetchLatestReleaseJson: suspend () -> String = { httpGet(GITHUB_LATEST_RELEASE_API, currentVersion) }
) {
    private val _state = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Checking)
    val state: StateFlow<UpdateCheckState> = _state.asStateFlow()

    fun start(scope: CoroutineScope) {
        scope.launch { _state.value = check() }
    }

    suspend fun check(): UpdateCheckState {
        if (parseVersion(currentVersion) == null) {
            return UpdateCheckState.Unavailable("Versione di sviluppo: controllo degli aggiornamenti non eseguito.")
        }
        return try {
            val release = parseLatestRelease(fetchLatestReleaseJson())
                ?: return UpdateCheckState.Unavailable("Risposta di GitHub non riconosciuta.")
            updateStateFor(release, currentVersion).also { state ->
                if (state is UpdateCheckState.Available) {
                    AppLogger.info(TAG, "Nuova versione disponibile: ${state.latestVersion} (in uso $currentVersion)")
                }
            }
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Controllo aggiornamenti non riuscito: ${e.message ?: e.javaClass.simpleName}")
            UpdateCheckState.Unavailable("Impossibile verificare gli aggiornamenti.")
        }
    }
}

private suspend fun httpGet(url: String, appVersion: String): String = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "KiwiSDR-Grabber/$appVersion")
        val code = connection.responseCode
        check(code == HttpURLConnection.HTTP_OK) { "HTTP $code" }
        connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    } finally {
        connection.disconnect()
    }
}
