package com.grabber.ui

import androidx.compose.foundation.layout.*
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
import com.grabber.ui.designsystem.GrabberColors

/**
 * Dialog iniziale per la configurazione del primo avvio.
 */
@Composable
internal fun InitialSetupDialog(
    settings: com.grabber.app.SettingsData,
    onComplete: (callsign: String, serverAddress: String, serverPassword: String, telegramToken: String?, telegramChatId: String?) -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var callsign by remember(settings.callsign) { mutableStateOf(settings.callsign) }
    var serverAddress by remember(settings.serverAddress) { mutableStateOf(settings.serverAddress) }
    var serverPassword by remember(settings.serverPassword) { mutableStateOf(settings.serverPassword) }
    var configureTelegram by remember { mutableStateOf(false) }
    var telegramToken by remember(settings.telegramBotToken) { mutableStateOf(settings.telegramBotToken) }
    var telegramChatId by remember(settings.telegramChatId) { mutableStateOf(settings.telegramChatId) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                if (step == 0) "Configurazione iniziale" else "Notifiche Telegram (facoltativo)",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.widthIn(min = 360.dp, max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (step == 0) "Inserisci il nominativo e l'indirizzo del ricevitore. Se il KiwiSDR richiede una password, inseriscila qui. La password viene salvata cifrata (Windows DPAPI) nel file impostazioni locale."
                    else "Puoi configurare Telegram ora oppure saltare questo passaggio e completarlo in seguito nelle Impostazioni. Il token del bot viene salvato cifrato (Windows DPAPI) nel file impostazioni locale.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                if (step == 0) {
                    OutlinedTextField(
                        value = callsign,
                        onValueChange = { callsign = it; error = null },
                        label = { Text("Nome utente / Callsign") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = error?.contains("callsign", ignoreCase = true) == true
                    )
                    OutlinedTextField(
                        value = serverAddress,
                        onValueChange = { serverAddress = it; error = null },
                        label = { Text("Indirizzo del grabber (host[:porta])") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = error?.contains("indirizzo", ignoreCase = true) == true
                    )
                    OutlinedTextField(
                        value = serverPassword,
                        onValueChange = { serverPassword = it },
                        label = { Text("Password KiwiSDR (facoltativa)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation()
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Switch(
                            checked = configureTelegram,
                            onCheckedChange = { configureTelegram = it; error = null }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Configura avvisi Telegram", color = Color.White, fontSize = 12.sp)
                    }
                    if (configureTelegram) {
                        OutlinedTextField(
                            value = telegramToken,
                            onValueChange = { telegramToken = it; error = null },
                            label = { Text("Token del bot") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            isError = error != null
                        )
                        OutlinedTextField(
                            value = telegramChatId,
                            onValueChange = { telegramChatId = it; error = null },
                            label = { Text("Chat ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = error != null
                        )
                    }
                }

                error?.let { Text(it, color = GrabberColors.Error, fontSize = 11.sp) }
            }
        },
        confirmButton = {
            if (step == 0) {
                Button(onClick = {
                    val cleanCallsign = callsign.trim()
                    val cleanAddress = serverAddress.trim()
                    when {
                        cleanCallsign.isEmpty() -> error = "Inserisci un callsign."
                        !isValidSetupServerAddress(cleanAddress) -> error = "Controlla l'indirizzo del server e l'eventuale porta."
                        else -> {
                            callsign = cleanCallsign
                            serverAddress = cleanAddress
                            error = null
                            step = 1
                        }
                    }
                }) {
                    Text("Avanti")
                }
            } else {
                Button(onClick = {
                    if (configureTelegram && (telegramToken.isBlank() || telegramChatId.isBlank())) {
                        error = "Inserisci sia il token del bot che il Chat ID, oppure disattiva la configurazione Telegram."
                    } else {
                        onComplete(
                            callsign.trim(),
                            serverAddress.trim(),
                            serverPassword,
                            telegramToken.trim().takeIf { configureTelegram },
                            telegramChatId.trim().takeIf { configureTelegram }
                        )
                    }
                }) {
                    Text("Completa")
                }
            }
        },
        dismissButton = {
            if (step == 0) {
                TextButton(onClick = {
                    callsign = "KiwiGrabberDesktop"
                    serverAddress = DEFAULT_SERVER_ADDRESS
                    serverPassword = ""
                    error = null
                    step = 1
                }) {
                    Text("Usa predefiniti")
                }
            } else {
                Row {
                    TextButton(onClick = { step = 0; error = null }) {
                        Text("Indietro")
                    }
                    TextButton(onClick = {
                        onComplete(callsign.trim(), serverAddress.trim(), serverPassword, null, null)
                    }) {
                        Text("Salta Telegram")
                    }
                }
            }
        },
        containerColor = GrabberColors.SurfaceElevated
    )
}

private fun isValidSetupServerAddress(address: String): Boolean {
    val match = Regex("^(?:(?:https?|wss?)://)?[A-Za-z0-9.-]+(?::([0-9]{1,5}))?/?$")
        .matchEntire(address) ?: return false
    val port = match.groupValues[1]
    return port.isEmpty() || port.toIntOrNull() in 1..65535
}
