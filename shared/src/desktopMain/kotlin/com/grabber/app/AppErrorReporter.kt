package com.grabber.app

import java.awt.BorderLayout
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.io.File
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities

object AppErrorReporter {
    private const val TAG = "UncaughtException"
    private val defaultLogDirectory: File
        get() = File(System.getProperty("user.home") ?: ".", ".kiwigrabber/logs")

    @Volatile
    private var logDirectory: File = defaultLogDirectory

    fun install(logDir: File = defaultLogDirectory) {
        logDirectory = logDir
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            report("thread '${thread.name}'", error)
        }
    }

    fun report(context: String, error: Throwable) {
        try {
            if (!AppLogger.isInitialized()) AppLogger.init(logDirectory, LogLevel.WARN)
            AppLogger.error(TAG, "Eccezione non gestita in $context", error)
        } catch (loggingError: Throwable) {
            System.err.println("[FATAL] Impossibile registrare l'eccezione non gestita.")
            loggingError.printStackTrace(System.err)
            error.printStackTrace(System.err)
        }

        val logFile = AppLogger.currentLogFile()
        val logStatus = if (AppLogger.isInitialized() && logFile != null) {
            "Log salvato in: ${logFile.absolutePath}"
        } else {
            "Il file di log non è disponibile. Comunica il rapporto qui sotto."
        }
        val report = buildString {
            appendLine("KiwiSDR Grabber ha rilevato un errore non gestito.")
            appendLine("Contesto: $context")
            appendLine(logStatus)
            appendLine()
            append(error.stackTraceToString())
        }
        showReport(report)
    }

    private fun showReport(report: String) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println(report)
            return
        }

        val showDialog = Runnable {
            val details = JTextArea(report, 20, 85).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                caretPosition = 0
            }
            val panel = JPanel(BorderLayout(0, 8)).apply {
                add(JLabel("Seleziona e copia i dettagli per comunicarli all'assistenza."), BorderLayout.NORTH)
                add(JScrollPane(details), BorderLayout.CENTER)
            }
            JOptionPane.showMessageDialog(
                null,
                panel,
                "Errore di KiwiSDR Grabber",
                JOptionPane.ERROR_MESSAGE
            )
        }

        try {
            if (EventQueue.isDispatchThread()) showDialog.run() else SwingUtilities.invokeAndWait(showDialog)
        } catch (dialogError: Exception) {
            System.err.println("[FATAL] Impossibile mostrare il rapporto di errore.")
            dialogError.printStackTrace(System.err)
            System.err.println(report)
        }
    }
}
