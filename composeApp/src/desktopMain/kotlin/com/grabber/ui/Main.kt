package com.grabber.ui

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.grabber.app.KiwiAppController
import java.awt.Dimension

fun main() {
    if (System.getenv("SKIKO_RENDER_API") == null && System.getProperty("skiko.renderApi") == null) {
        System.setProperty("skiko.renderApi", "SOFTWARE_FAST")
    }

    application {
        val controller = remember { KiwiAppController() }
        val appVersion = System.getProperty("app.version")?.takeIf { it.isNotBlank() } ?: "dev"

        Window(
            onCloseRequest = {
                controller.shutdown()
                exitApplication()
            },
            title = "KiwiSDR Grabber & Station Recorder v$appVersion",
            state = rememberWindowState(
                width = 1100.dp,
                height = 740.dp,
                placement = WindowPlacement.Maximized
            )
        ) {
            SideEffect {
                window.minimumSize = Dimension(960, 640)
            }
            App(
                controller = controller,
                onExitApplication = {
                    controller.shutdown()
                    exitApplication()
                }
            )
        }
    }
}
