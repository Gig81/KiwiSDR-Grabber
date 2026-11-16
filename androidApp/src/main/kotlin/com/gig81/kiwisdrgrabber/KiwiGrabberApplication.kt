package com.gig81.kiwisdrgrabber

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.grabber.app.AppLogger
import com.grabber.mobile.MobileAppController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Crea l'unico [MobileAppController] del processo e collega il ciclo di vita dell'app:
 * visibilità (primo piano / background) e avvio/arresto del foreground service di ascolto.
 */
class KiwiGrabberApplication : Application() {
    lateinit var controller: MobileAppController
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        controller = MobileAppController(this, BuildConfig.VERSION_NAME)
        observeVisibility()
        observeForegroundService()
    }

    private fun observeVisibility() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = controller.onAppVisibilityChanged(true)
            override fun onStop(owner: LifecycleOwner) = controller.onAppVisibilityChanged(false)
        })
    }

    private fun observeForegroundService() {
        controller.needsForegroundService
            .onEach { needed ->
                val intent = Intent(this, KiwiPlaybackService::class.java)
                if (needed) {
                    try {
                        ContextCompat.startForegroundService(this, intent)
                    } catch (e: IllegalStateException) {
                        // Android 12+: avvio non consentito con l'app in background (caso raro).
                        AppLogger.error(TAG, "Avvio del servizio di ascolto non consentito", e)
                    }
                } else {
                    stopService(intent)
                }
            }
            .launchIn(appScope)
    }

    private companion object {
        const val TAG = "KiwiGrabberApplication"
    }
}
