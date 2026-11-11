package com.grabber.ui.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.app.KiwiAppController
import com.grabber.app.SettingsData
import com.grabber.ui.designsystem.*
import java.awt.Desktop
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

/** Modello Vosk per la trascrizione offline (funzione con licenza). */
@Composable
internal fun TranscriptionSettingsSection(
    controller: KiwiAppController,
    settings: SettingsData,
    isMachineLicensed: Boolean,
    onActivateLicense: () -> Unit,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    var sttModelError by remember { mutableStateOf<String?>(null) }

    CollapsibleSettingsCard(
        title = "TRASCRIZIONE VOCALE OFFLINE (VOSK)",
        icon = Icons.Default.RecordVoiceOver,
        iconTint = GrabberColors.Success,
        isExpanded = isExpanded,
        onToggle = onToggle,
        subtitle = if (!isMachineLicensed) "Richiede attivazione" else if (settings.sttModelPath.isBlank()) "Modello non configurato" else "Modello locale configurato",
        locked = !isMachineLicensed,
        onActivate = onActivateLicense
    ) {
        Text(
            text = "Le registrazioni completate vengono trascritte localmente in coda. L'audio non viene inviato in rete. Scarica ed estrai un modello italiano Vosk, poi seleziona la cartella del modello (quella che contiene la sottocartella am).",
            fontSize = 10.sp,
            color = GrabberColors.TextSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f).height(32.dp),
                shape = RoundedCornerShape(4.dp),
                color = GrabberColors.SurfaceElevated,
                border = BorderStroke(1.dp, GrabberColors.Border)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = settings.sttModelPath.ifBlank { "Nessun modello selezionato" },
                        fontSize = 10.sp,
                        color = if (settings.sttModelPath.isBlank()) GrabberColors.TextSecondary else GrabberColors.TextPrimary,
                        maxLines = 1
                    )
                }
            }

            OutlinedButton(
                text = "Seleziona modello",
                onClick = {
                    val chooser = JFileChooser().apply {
                        dialogTitle = "Seleziona la cartella del modello Vosk"
                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        isAcceptAllFileFilterUsed = false
                        currentDirectory = File(settings.sttModelPath).takeIf { it.isDirectory }
                            ?: File(System.getProperty("user.home"))
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        val selected = chooser.selectedFile
                        if (File(selected, "am").isDirectory) {
                            controller.setSttModelPath(selected.absolutePath)
                            sttModelError = null
                        } else {
                            sttModelError = "La cartella scelta non sembra un modello Vosk: manca la sottocartella am."
                        }
                    }
                },
                size = ControlSize.Compact,
                contentColor = GrabberColors.TextPrimary
            )

            if (settings.sttModelPath.isNotBlank()) {
                OutlinedButton(
                    text = "Disattiva",
                    onClick = { controller.setSttModelPath("") },
                    size = ControlSize.Compact,
                    contentColor = GrabberColors.Error
                )
            }
        }

        TextButton(onClick = {
            try {
                Desktop.getDesktop().browse(URI("https://alphacephei.com/vosk/models"))
            } catch (e: Exception) {
                sttModelError = "Impossibile aprire la pagina dei modelli Vosk: ${e.message ?: "errore browser"}"
            }
        }) {
            Text("Apri pagina ufficiale dei modelli Vosk", fontSize = 10.sp, color = GrabberColors.Secondary)
        }
        sttModelError?.let { Text(it, color = GrabberColors.Error, fontSize = 10.sp) }
    }
}
