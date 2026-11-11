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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.app.KiwiAppController
import com.grabber.app.SettingsData
import com.grabber.ui.designsystem.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** Machine ID e attivazione della licenza. */
@Composable
internal fun LicenseSettingsSection(
    controller: KiwiAppController,
    settings: SettingsData,
    isMachineLicensed: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val machineId = remember { controller.machineId }
    var licenseInput by remember(settings.licenseKey) { mutableStateOf(settings.licenseKey) }
    var licenseStatusMsg by remember { mutableStateOf<String?>(null) }
    var copiedMachineIdFeedback by remember { mutableStateOf(false) }

    CollapsibleSettingsCard(
        title = "GESTIONE LICENZA DI UTILIZZO (HARDWARE-LOCKED)",
        icon = Icons.Default.VpnKey,
        iconTint = GrabberColors.Warning,
        isExpanded = isExpanded,
        onToggle = onToggle,
        subtitle = "Machine ID: $machineId",
        badge = {
            StatusBadge(
                text = if (isMachineLicensed) "✓ ATTIVATA" else "NON ATTIVATO",
                color = if (isMachineLicensed) GrabberColors.Success else GrabberColors.Warning,
                size = ControlSize.Compact
            )
        }
    ) {
        Text(
            text = "La licenza firmata è vincolata al Machine ID di questo computer. Comunica questo ID al titolare del progetto e inserisci qui il codice di attivazione ricevuto. L'attivazione avviene localmente: l'app non invia richieste a un bot interno.",
            fontSize = 10.sp,
            color = GrabberColors.TextSecondary
        )

        // Visualizzazione Machine ID
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Machine ID Hardware:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrabberColors.TextPrimary)
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = GrabberColors.LcdBackground,
                border = BorderStroke(1.dp, GrabberColors.Border)
            ) {
                Text(
                    text = machineId,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrabberColors.LcdPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            SecondaryButton(
                text = if (copiedMachineIdFeedback) "Copiato!" else "Copia ID",
                onClick = {
                    try {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(machineId), null)
                        copiedMachineIdFeedback = true
                    } catch (e: Exception) {
                        licenseStatusMsg = "✗ Impossibile copiare il Machine ID: ${e.message ?: "errore clipboard"}"
                    }
                },
                icon = Icons.Default.ContentCopy,
                size = ControlSize.Dense,
                contentColor = GrabberColors.Secondary
            )
        }

        // Form Inserimento Codice Licenza
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Codice Licenza:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrabberColors.TextPrimary)
            CompactTextField(
                value = licenseInput,
                onValueChange = { licenseInput = it },
                placeholder = "Codice KSR1 ricevuto dal titolare del progetto",
                modifier = Modifier.weight(1f).height(32.dp),
                fontSize = 10.sp
            )

            PrimaryButton(
                text = "Attiva Licenza",
                onClick = {
                    if (controller.activateLicense(licenseInput)) {
                        licenseStatusMsg = "✓ Licenza verificata e attivata con successo per questa macchina!"
                    } else {
                        licenseStatusMsg = "✗ Codice licenza non valido per questa macchina. La licenza salvata non è stata modificata."
                    }
                },
                icon = Icons.Default.Check,
                size = ControlSize.Compact,
                containerColor = GrabberColors.Success,
                contentColor = Color.Black
            )
        }

        if (licenseStatusMsg != null) {
            Text(
                text = licenseStatusMsg!!,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (licenseStatusMsg!!.startsWith("✓")) GrabberColors.Success else GrabberColors.Error
            )
        }
    }
}
