package com.grabber.app

import com.grabber.kiwi.KiwiClient
import com.grabber.model.ConnectionState
import com.grabber.waterfall.WaterfallBuffer
import com.grabber.waterfall.WaterfallClient
import com.grabber.waterfall.WaterfallPaletteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Stream spettro/waterfall: KiwiSDR usa un socket W/F dedicato, aperto solo dopo l'autenticazione
 * della sessione audio SND e riaperto automaticamente dopo interruzioni temporanee.
 */
internal class WaterfallCoordinator(
    private val scope: CoroutineScope,
    private val client: KiwiClient,
    private val waterfallClient: WaterfallClient,
    private val buffer: WaterfallBuffer,
    private val appSettings: AppSettings
) {
    private companion object {
        const val TAG = "WaterfallCoordinator"
        const val RETRY_DELAY_MS = 2000L
    }

    private val _palette = MutableStateFlow(appSettings.settings.value.waterfallPalette)
    val palette: StateFlow<WaterfallPaletteType> = _palette.asStateFlow()

    private val _autoFollow = MutableStateFlow(appSettings.settings.value.waterfallAutoFollowVfo)
    val autoFollow: StateFlow<Boolean> = _autoFollow.asStateFlow()

    @Volatile private var isStreamRequested = false

    fun start() {
        routeFramesToBuffer()
        openStreamWhenAuthenticated()
        retryWhenTransientlyDisconnected()
    }

    fun applyPersistedSettings(settings: SettingsData, initialFrequencyKhz: Double) {
        waterfallClient.clientName = settings.callsign
        waterfallClient.setDbRange(settings.waterfallMinDb, settings.waterfallMaxDb)
        waterfallClient.setSpeed(settings.waterfallSpeed)
        waterfallClient.setZoom(settings.waterfallZoom)
        waterfallClient.setCenterFrequency(initialFrequencyKhz)
    }

    fun updateServer(host: String, port: Int) = waterfallClient.updateServer(host, port)

    fun updatePassword(password: String) = waterfallClient.updatePassword(password)

    fun requestStream() {
        isStreamRequested = true
    }

    fun connectIfSessionReady() {
        if (client.isAuthenticated.value && client.connectionState.value is ConnectionState.Connected) {
            client.sessionToken.value?.let { token -> waterfallClient.connect(scope, token) }
        }
    }

    /** Chiude lo stream su richiesta dell'utente: non verrà riaperto automaticamente. */
    fun stopStream() {
        isStreamRequested = false
        waterfallClient.disconnect()
    }

    /** Chiude il socket per una disconnessione della sessione audio; verrà riaperto alla riconnessione. */
    fun closeSocket() = waterfallClient.disconnect()

    fun followTuning(frequencyKhz: Double) {
        if (_autoFollow.value) waterfallClient.setCenterFrequency(frequencyKhz)
    }

    fun setZoom(zoom: Int) {
        waterfallClient.setZoom(zoom)
        appSettings.setWaterfallZoom(waterfallClient.currentZoom.value)
    }

    fun zoomBy(delta: Int) = setZoom(waterfallClient.currentZoom.value + delta)

    fun setCenterFrequency(frequencyKhz: Double) = waterfallClient.setCenterFrequency(frequencyKhz)

    fun setPalette(palette: WaterfallPaletteType) {
        _palette.value = palette
        appSettings.setWaterfallPalette(palette)
        buffer.recolor(minDb = waterfallClient.minDb.value, maxDb = waterfallClient.maxDb.value, palette = palette)
    }

    fun setDbRange(minDb: Int, maxDb: Int) {
        waterfallClient.setDbRange(minDb, maxDb)
        appSettings.setWaterfallDbRange(minDb, maxDb)
        buffer.recolor(minDb = minDb, maxDb = maxDb, palette = _palette.value)
    }

    fun setSpeed(speed: Int) {
        waterfallClient.setSpeed(speed)
        appSettings.setWaterfallSpeed(speed)
    }

    fun setAutoFollow(autoFollow: Boolean, currentFrequencyKhz: Double) {
        _autoFollow.value = autoFollow
        appSettings.setWaterfallAutoFollow(autoFollow)
        if (autoFollow) waterfallClient.setCenterFrequency(currentFrequencyKhz)
    }

    fun togglePause() = waterfallClient.togglePause()

    fun clear() = buffer.clear()

    private fun routeFramesToBuffer() {
        waterfallClient.waterfallFlow
            .onEach { frame ->
                buffer.addLine(
                    frame = frame,
                    minDb = waterfallClient.minDb.value,
                    maxDb = waterfallClient.maxDb.value,
                    palette = _palette.value
                )
            }
            .launchIn(scope)
    }

    private fun openStreamWhenAuthenticated() {
        client.isAuthenticated
            .filter { it }
            .onEach {
                if (isStreamRequested && client.connectionState.value is ConnectionState.Connected) {
                    client.sessionToken.value?.let { token -> waterfallClient.connect(scope, token) }
                }
            }
            .launchIn(scope)
    }

    private fun retryWhenTransientlyDisconnected() {
        waterfallClient.connectionState
            .onEach { state ->
                val isClosed = state is ConnectionState.Disconnected || state is ConnectionState.Error
                if (!isClosed || state.isPermanentRejection() || !canOpenStream()) return@onEach
                AppLogger.info(TAG, "Waterfall non disponibile: nuovo tentativo tra 2 secondi...")
                delay(RETRY_DELAY_MS)
                val current = waterfallClient.connectionState.value
                if (canOpenStream() && !current.isPermanentRejection() && current !is ConnectionState.Connected) {
                    client.sessionToken.value?.let { token -> waterfallClient.connect(scope, token) }
                }
            }
            .launchIn(scope)
    }

    private fun canOpenStream(): Boolean =
        isStreamRequested &&
            client.isAuthenticated.value &&
            client.connectionState.value is ConnectionState.Connected &&
            !client.isManualDisconnected()

    /** `badp` indica credenziali rifiutate: ritentare non servirebbe. */
    private fun ConnectionState.isPermanentRejection(): Boolean =
        (this as? ConnectionState.Error)?.message?.contains("badp=") == true
}
