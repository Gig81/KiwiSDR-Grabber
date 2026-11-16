package com.gig81.kiwisdrgrabber

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.grabber.app.AppLogger
import com.grabber.model.ConnectionState
import com.grabber.mobile.MobileAppController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.Locale

/**
 * Foreground service `mediaPlayback`: tiene vivo il processo durante l'ascolto in background
 * e a schermo spento (solo versione attivata). Mostra stato e frequenza con l'azione
 * *Disconnetti*; la rimozione dell'app dai recenti chiude la sessione e libera lo slot del ricevitore.
 */
class KiwiPlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wifiLock: WifiManager.WifiLock? = null

    private val controller: MobileAppController
        get() = (application as KiwiGrabberApplication).controller

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startInForeground(buildNotification("Connessione in corso…", ""))
        acquireWifiLock()
        combine(controller.connectionState, controller.currentFrequencyKhz, controller.currentMode) { state, freq, mode ->
            buildNotification(statusText(state), String.format(Locale.ITALY, "%.2f kHz · %s", freq, mode.displayName))
        }
            .onEach { notification -> getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification) }
            .launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            AppLogger.info(TAG, "Disconnessione richiesta dalla notifica")
            controller.stopSession()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        AppLogger.info(TAG, "App rimossa dai recenti: chiusura della sessione")
        controller.stopSession()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        wifiLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground(notification: android.app.Notification) {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    private fun acquireWifiLock() {
        val wifi = applicationContext.getSystemService(WifiManager::class.java) ?: return
        wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "KiwiGrabber:ascolto").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_playback), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.notification_channel_playback_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, text: String): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val disconnect = PendingIntent.getService(
            this, 1,
            Intent(this, KiwiPlaybackService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Disconnetti", disconnect)
            .build()
    }

    private fun statusText(state: ConnectionState): String = when (state) {
        is ConnectionState.Connected -> "In ascolto"
        is ConnectionState.Connecting -> "Connessione in corso…"
        is ConnectionState.Reconnecting -> "Riconnessione tra ${state.secondsLeft} s…"
        is ConnectionState.Error -> "Errore di connessione"
        is ConnectionState.Disconnected -> "Disconnesso"
    }

    private companion object {
        const val TAG = "KiwiPlaybackService"
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 1
        const val ACTION_DISCONNECT = "com.gig81.kiwisdrgrabber.action.DISCONNECT"
    }
}
