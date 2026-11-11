package com.grabber.ui.feature.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grabber.app.KiwiAppController
import java.awt.Desktop
import java.io.File

/**
 * Scheda del registratore: a sinistra avvio/arresto, formato, intervallo dei segmenti e gate di
 * registrazione; a destra l'archivio con ricerca nelle trascrizioni, riproduzione ed eliminazione.
 */
@Composable
fun RecordingsTab(
    controller: KiwiAppController,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RecordingControlPanel(
            controller = controller,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        RecordingArchivePanel(
            controller = controller,
            modifier = Modifier.weight(2f).fillMaxHeight()
        )
    }
}

/** Apre la cartella delle registrazioni nel file manager, creandola se non esiste. */
internal fun openRecordingsDirectory(directory: File) = openWithDesktop(directory, createIfMissing = true)

/** Apre file o cartelle con l'applicazione di sistema; un errore lascia semplicemente l'azione senza effetto. */
internal fun openWithDesktop(file: File, createIfMissing: Boolean = false) {
    try {
        if (createIfMissing && !file.exists()) file.mkdirs()
        if (file.exists()) Desktop.getDesktop().open(file)
    } catch (_: Exception) {
    }
}
