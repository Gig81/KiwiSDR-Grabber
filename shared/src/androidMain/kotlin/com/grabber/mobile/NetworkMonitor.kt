package com.grabber.mobile

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.grabber.app.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Osserva la rete predefinita: segnala quando torna disponibile (cambio Wi-Fi ↔ rete mobile)
 * e se è a consumo, per ridurre il traffico del waterfall sulla rete mobile.
 */
class NetworkMonitor(
    context: Context,
    private val onNetworkAvailable: () -> Unit
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private val _isMetered = MutableStateFlow(connectivity.isActiveNetworkMetered)
    val isMetered: StateFlow<Boolean> = _isMetered.asStateFlow()

    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            AppLogger.info(TAG, "Rete disponibile")
            onNetworkAvailable()
        }

        override fun onLost(network: Network) {
            AppLogger.info(TAG, "Rete persa")
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _isMetered.value = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
    }

    @Synchronized
    fun start() {
        if (registered) return
        try {
            connectivity.registerDefaultNetworkCallback(callback)
            registered = true
        } catch (e: RuntimeException) {
            AppLogger.error(TAG, "Monitoraggio della rete non disponibile", e)
        }
    }

    @Synchronized
    fun stop() {
        if (!registered) return
        runCatching { connectivity.unregisterNetworkCallback(callback) }
            .onFailure { AppLogger.warn(TAG, "Deregistrazione del monitoraggio rete non riuscita", it) }
        registered = false
    }

    private companion object {
        const val TAG = "NetworkMonitor"
    }
}
