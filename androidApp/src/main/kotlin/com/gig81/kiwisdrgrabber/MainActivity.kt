package com.gig81.kiwisdrgrabber

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gig81.kiwisdrgrabber.ui.KiwiGrabberApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val controller = (application as KiwiGrabberApplication).controller
        // Avvio dell'app (non una ricreazione dopo rotazione): ripristina la connessione attiva alla chiusura.
        val isLaunch = savedInstanceState == null
        if (isLaunch) controller.restoreSessionOnLaunch()
        setContent { KiwiGrabberApp(controller, showSplash = isLaunch) }
    }
}
