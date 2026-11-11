package com.grabber.ui.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grabber.app.DEFAULT_SERVER_ADDRESS
import com.grabber.app.KiwiAppController
import com.grabber.app.SettingsData
import com.grabber.app.parseServerAddress
import com.grabber.ui.designsystem.*
import java.awt.Desktop
import java.net.URI

/** Indirizzo e password del ricevitore KiwiSDR. */
@Composable
internal fun ServerSettingsSection(
    controller: KiwiAppController,
    settings: SettingsData,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    var serverAddressInput by remember(settings.serverAddress) { mutableStateOf(settings.serverAddress) }
    var serverPasswordInput by remember(settings.serverPassword) { mutableStateOf(settings.serverPassword) }
    var serverAddressFeedback by remember { mutableStateOf<String?>(null) }

    CollapsibleSettingsCard(
        title = "SERVER KIWISDR / RICEVITORE REMOTO",
        icon = Icons.Default.Dns,
        iconTint = GrabberColors.Secondary,
        isExpanded = isExpanded,
        onToggle = onToggle,
        subtitle = "Indirizzo attivo: ${settings.serverAddress}",
        badge = {
            val (h, p) = parseServerAddress(settings.serverAddress)
            StatusBadge(
                text = "$h:$p",
                color = GrabberColors.Secondary,
                size = ControlSize.Compact,
                monospace = true
            )
        }
    ) {
        Text(
            text = "Specifica l'indirizzo e, se necessario, la password del ricevitore KiwiSDR. La password è salvata cifrata (Windows DPAPI) nelle impostazioni locali di questo utente e applicata sia alla ricezione audio sia allo spettro; usa una password dedicata.",
            fontSize = 10.sp,
            color = GrabberColors.TextSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Indirizzo Server:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = GrabberColors.TextPrimary
            )
            CompactTextField(
                value = serverAddressInput,
                onValueChange = {
                    serverAddressInput = it
                    serverAddressFeedback = null
                },
                placeholder = "Es: $DEFAULT_SERVER_ADDRESS oppure sdr.example.com:8073",
                modifier = Modifier.weight(1f).height(32.dp),
                fontSize = 10.sp
            )

            PrimaryButton(
                text = "Salva Server",
                onClick = {
                    val clean = serverAddressInput.trim()
                    if (clean.isBlank()) {
                        serverAddressFeedback = "✗ Errore: L'indirizzo non può essere vuoto!"
                        return@PrimaryButton
                    }
                    controller.setServerAddress(clean)
                    serverAddressFeedback = "✓ Indirizzo salvato e applicato: ${controller.serverEndpoint}"
                },
                icon = Icons.Default.Save,
                size = ControlSize.Compact,
                containerColor = GrabberColors.Secondary,
                contentColor = Color.Black
            )

            OutlinedButton(
                text = "Predefinito",
                onClick = {
                    serverAddressInput = DEFAULT_SERVER_ADDRESS
                    controller.setServerAddress(DEFAULT_SERVER_ADDRESS)
                    serverAddressFeedback = "✓ Ripristinato server predefinito ($DEFAULT_SERVER_ADDRESS)"
                },
                icon = Icons.Default.Restore,
                size = ControlSize.Compact,
                contentColor = GrabberColors.TextSecondary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Password server:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = GrabberColors.TextPrimary
            )
            CompactTextField(
                value = serverPasswordInput,
                onValueChange = {
                    serverPasswordInput = it
                    serverAddressFeedback = null
                },
                placeholder = "Vuota se il ricevitore non richiede password",
                modifier = Modifier.weight(1f).height(32.dp),
                fontSize = 10.sp,
                visualTransformation = PasswordVisualTransformation()
            )
            PrimaryButton(
                text = "Salva password",
                onClick = {
                    controller.setServerPassword(serverPasswordInput)
                    serverAddressFeedback = "✓ Password salvata. Se eri connesso, riconnettiti per applicarla."
                },
                icon = Icons.Default.Save,
                size = ControlSize.Compact,
                containerColor = GrabberColors.Secondary,
                contentColor = Color.Black
            )
        }

        OutlinedButton(
            text = "Mappa pubblica KiwiSDR",
            onClick = {
                try {
                    val desktop = Desktop.getDesktop()
                    if (!Desktop.isDesktopSupported() || !desktop.isSupported(Desktop.Action.BROWSE)) {
                        serverAddressFeedback = "✗ Apertura del browser non supportata su questo sistema."
                    } else {
                        desktop.browse(URI("https://kiwisdr.com/public/"))
                        serverAddressFeedback = "✓ Mappa pubblica dei ricevitori KiwiSDR aperta nel browser."
                    }
                } catch (e: Exception) {
                    serverAddressFeedback = "✗ Impossibile aprire la mappa KiwiSDR: ${e.message ?: "errore browser"}"
                }
            },
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            size = ControlSize.Compact,
            contentColor = GrabberColors.Primary
        )

        if (serverAddressFeedback != null) {
            Text(
                text = serverAddressFeedback!!,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (serverAddressFeedback!!.startsWith("✓")) GrabberColors.Success else GrabberColors.Error
            )
        }
    }
}
