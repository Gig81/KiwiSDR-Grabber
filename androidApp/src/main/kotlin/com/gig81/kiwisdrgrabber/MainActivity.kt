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
        setContent { KiwiGrabberApp(controller) }
    }
}
