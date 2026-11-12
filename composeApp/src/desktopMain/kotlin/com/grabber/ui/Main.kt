package com.grabber.ui

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.grabber.app.AppErrorReporter
import com.grabber.app.AppLogger
import com.grabber.app.KiwiAppController
import com.grabber.app.LogLevel
import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.Dimension
import java.awt.GridLayout
import java.io.File
import java.net.URI
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.SwingUtilities

private const val INSTALLER_COMPLETE_ARGUMENT = "--installer-complete"

fun main(args: Array<String>) {
    val logDirectory = File(System.getProperty("user.home") ?: ".", ".kiwigrabber/logs")
    AppErrorReporter.install(logDirectory)
    try {
        if (!AppLogger.init(logDirectory, LogLevel.WARN)) {
            AppErrorReporter.report(
                "inizializzazione del file di log",
                AppLogger.initializationError()
                    ?: IllegalStateException("Impossibile inizializzare il log in ${logDirectory.absolutePath}")
            )
        }

        if (INSTALLER_COMPLETE_ARGUMENT in args) {
            showInstallerCompletionChoices()
            return
        }

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
    } catch (error: Throwable) {
        AppErrorReporter.report("avvio dell'applicazione", error)
    } finally {
        AppLogger.shutdown()
    }
}

private fun showInstallerCompletionChoices() {
    var launchApplication = false
    var viewReleaseNotes = false
    SwingUtilities.invokeAndWait {
        val launchCheckBox = JCheckBox("Avvia KiwiSDR Grabber", true)
        val releaseNotesCheckBox = JCheckBox("Apri le note di rilascio nel browser", false)
        val dialogContent = JPanel(BorderLayout(0, 12)).apply {
            add(JLabel("Installazione completata. Scegli cosa fare:"), BorderLayout.NORTH)
            add(JPanel(GridLayout(0, 1, 0, 6)).apply {
                add(launchCheckBox)
                add(releaseNotesCheckBox)
            }, BorderLayout.CENTER)
        }

        if (JOptionPane.showConfirmDialog(
                null,
                dialogContent,
                "KiwiSDR Grabber - Installazione completata",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE
            ) == JOptionPane.OK_OPTION
        ) {
            launchApplication = launchCheckBox.isSelected
            viewReleaseNotes = releaseNotesCheckBox.isSelected
        }
    }

    if (viewReleaseNotes) {
        try {
            val version = System.getProperty("app.version")?.takeIf { it.isNotBlank() } ?: "latest"
            val releaseNotesUrl = URI("https://github.com/Gig81/KiwiSDR-Grabber/releases/tag/v$version")
            check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                "Il browser predefinito non è disponibile."
            }
            Desktop.getDesktop().browse(releaseNotesUrl)
        } catch (error: Exception) {
            AppErrorReporter.report("apertura delle note di rilascio", error)
        }
    }

    if (launchApplication) {
        try {
            val installDirectory = File(System.getProperty("java.home")).parentFile
                ?: error("Impossibile individuare la cartella di installazione.")
            val application = File(installDirectory, "KiwiSDRRecorder.exe")
            check(application.isFile) { "Eseguibile non trovato: ${application.absolutePath}" }
            ProcessBuilder(application.absolutePath).start()
        } catch (error: Exception) {
            AppErrorReporter.report("avvio dell'applicazione dopo l'installazione", error)
        }
    }
}
