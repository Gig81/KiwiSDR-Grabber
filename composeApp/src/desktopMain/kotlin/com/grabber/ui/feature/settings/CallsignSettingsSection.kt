package com.grabber.ui.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.app.KiwiAppController
import com.grabber.app.SettingsData
import com.grabber.ui.designsystem.*

/** Nominativo trasmesso al server KiwiSDR. */
@Composable
internal fun CallsignSettingsSection(
    controller: KiwiAppController,
    settings: SettingsData,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    var callsignInput by remember(settings.callsign) { mutableStateOf(settings.callsign) }

    CollapsibleSettingsCard(
        title = "IDENTIFICATIVO OPERATORE / CALLSIGN",
        icon = Icons.Default.Person,
        iconTint = GrabberColors.Secondary,
        isExpanded = isExpanded,
        onToggle = onToggle,
        subtitle = "Nominativo trasmesso al server: ${settings.callsign}"
    ) {
        Text(
            text = "Questo nominativo viene trasmesso al server KiwiSDR e visualizzato nella tabella utenti sia nel web browser che nel programma.",
            fontSize = 10.sp,
            color = GrabberColors.TextSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactTextField(
                value = callsignInput,
                onValueChange = { callsignInput = it },
                modifier = Modifier.weight(1f).height(32.dp),
                placeholder = "Es: IU7IYA, CB-Roberto, ecc.",
                fontSize = 11.sp
            )

            PrimaryButton(
                text = "Salva & Invia",
                onClick = {
                    val trimmed = callsignInput.trim().ifEmpty { "KiwiGrabber" }
                    controller.setIdentUser(trimmed)
                },
                icon = Icons.Default.Save,
                size = ControlSize.Compact,
                containerColor = GrabberColors.Secondary,
                contentColor = Color.Black
            )
        }
    }
}
