package com.grabber.monitor

import com.grabber.app.AppLogger
import com.grabber.kiwi.KiwiServerDefaults
import com.grabber.model.StationInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.HttpURLConnection
import java.net.URI

private const val TAG = "StationMonitor"

/**
 * Interroga periodicamente `/status` del ricevitore ed espone le informazioni della stazione.
 * Lo storico degli utenti connessi è registrato da `KiwiAppController` a ogni cambio dei nominativi.
 */
class StationMonitor(
    var host: String = KiwiServerDefaults.HOST,
    var port: Int = KiwiServerDefaults.PORT,
    private val pollIntervalSeconds: Int = 15
) {
    private val _stationInfo = MutableStateFlow(StationInfo())
    val stationInfo: StateFlow<StationInfo> = _stationInfo.asStateFlow()

    private var monitorJob: Job? = null

    fun updateServer(newHost: String, newPort: Int) {
        host = newHost.trim().ifEmpty { KiwiServerDefaults.HOST }
        port = newPort
    }

    fun start(scope: CoroutineScope) {
        if (monitorJob != null) return

        monitorJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    _stationInfo.value = fetchStatus()
                } catch (e: Exception) {
                    AppLogger.warn(TAG, "Stato della stazione non disponibile: ${e.message}")
                }
                delay(pollIntervalSeconds * 1000L)
            }
        }
    }

    private fun fetchStatus(): StationInfo {
        val url = URI("http://$host:$port/status").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        try {
            return conn.inputStream.bufferedReader(Charsets.UTF_8).useLines(::parseStatus)
        } finally {
            conn.disconnect()
        }
    }

    /** Il formato di `/status` è una riga `chiave=valore` per campo. */
    private fun parseStatus(lines: Sequence<String>): StationInfo {
        val fields = lines
            .map { it.split("=", limit = 2) }
            .filter { it.size == 2 }
            .associate { (key, value) -> key.trim() to value.trim() }
        return StationInfo(
            name = fields["name"].orEmpty(),
            antenna = fields["antenna"].orEmpty(),
            activeUsers = fields["users"]?.toIntOrNull() ?: 0,
            maxUsers = fields["users_max"]?.toIntOrNull() ?: 0,
            snr = fields["snr"].orEmpty(),
            uptime = fields["uptime"].orEmpty(),
            version = fields["sw_version"].orEmpty(),
            gps = fields["gps"].orEmpty()
        )
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }
}

