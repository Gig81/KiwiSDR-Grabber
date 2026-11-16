package com.gig81.kiwisdrgrabber.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gig81.kiwisdrgrabber.BuildConfig
import com.gig81.kiwisdrgrabber.ui.components.Panel
import com.gig81.kiwisdrgrabber.ui.theme.KiwiColors
import com.gig81.kiwisdrgrabber.ui.theme.NumericStyle
import com.grabber.kiwi.KiwiServerDefaults
import com.grabber.mobile.MobileAppController

@Composable
fun SettingsScreen(controller: MobileAppController) {
    Column(
        // Su tablet il contenuto resta centrato con una larghezza leggibile.
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).wrapContentWidth().widthIn(max = 640.dp).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ServerSection(controller)
        LicenseSection(controller)
        AboutSection()
    }
}

@Composable
private fun ServerSection(controller: MobileAppController) {
    val settings by controller.settings.collectAsStateWithLifecycle()
    var address by remember(settings.serverAddress) { mutableStateOf(settings.serverAddress) }
    var password by remember(settings.serverPassword) { mutableStateOf(settings.serverPassword) }
    var callsign by remember(settings.callsign) { mutableStateOf(settings.callsign) }
    var showPassword by remember { mutableStateOf(false) }
    val addressError = validateAddress(address)
    val changed = address.trim() != settings.serverAddress || password != settings.serverPassword || callsign.trim() != settings.callsign

    Panel(title = "Ricevitore KiwiSDR") {
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Indirizzo (host:porta)") },
            placeholder = { Text(KiwiServerDefaults.ADDRESS) },
            isError = addressError != null,
            supportingText = { Text(addressError ?: "Porta predefinita ${KiwiServerDefaults.PORT}") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Password del ricevitore (facoltativa)") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showPassword) "Nascondi password" else "Mostra password"
                    )
                }
            },
            supportingText = {
                Text("Salvata cifrata sul dispositivo. Il protocollo KiwiSDR la trasmette in chiaro: non riutilizzare password importanti.")
            }
        )
        OutlinedTextField(
            value = callsign,
            onValueChange = { callsign = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Nome mostrato agli altri utenti") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = changed && addressError == null,
                onClick = {
                    controller.setServerAddress(address)
                    if (password != settings.serverPassword) controller.setServerPassword(password)
                    if (callsign.trim() != settings.callsign) controller.setCallsign(callsign)
                }
            ) { Text("Salva") }
            OutlinedButton(onClick = { address = KiwiServerDefaults.ADDRESS }) { Text("Predefinito") }
        }
        Text(
            "Cambiare indirizzo o password chiude la connessione in corso.",
            style = MaterialTheme.typography.bodySmall,
            color = KiwiColors.TextSecondary
        )
    }
}

/** Verifica formale: host senza spazi e porta numerica valida, se indicata. */
private fun validateAddress(address: String): String? {
    val clean = address.trim()
        .removePrefix("http://").removePrefix("https://").removePrefix("ws://").removePrefix("wss://")
        .trimEnd('/')
    if (clean.isEmpty()) return "Inserisci l'indirizzo del ricevitore"
    if (clean.any { it.isWhitespace() }) return "L'indirizzo non può contenere spazi"
    val parts = clean.split(':')
    if (parts.size > 2 || parts[0].isBlank()) return "Formato atteso: host oppure host:porta"
    if (parts.size == 2) {
        val port = parts[1].toIntOrNull()
        if (port == null || port !in 1..65535) return "Porta non valida (1-65535)"
    }
    return null
}

@Composable
private fun LicenseSection(controller: MobileAppController) {
    val context = LocalContext.current
    val activated by controller.isActivated.collectAsStateWithLifecycle()
    val machineId = remember { controller.machineId }
    var code by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }

    Panel(title = "Licenza") {
        if (activated) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = KiwiColors.Success)
                Spacer(Modifier.width(8.dp))
                Text("Versione attivata: registrazione, ascolto in background e preferiti personali disponibili.")
            }
        } else {
            Text(
                "L'attivazione è gratuita e offline. Invia l'ID dispositivo allo sviluppatore (ad esempio su Telegram) " +
                    "e inserisci qui il codice ricevuto. Sblocca registrazione, ascolto in background e preferiti personali.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text("ID dispositivo", style = MaterialTheme.typography.labelMedium, color = KiwiColors.TextSecondary)
        SelectionContainer {
            Text(machineId, style = NumericStyle.copy(fontSize = 18.sp), color = KiwiColors.LcdPrimary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copyToClipboard(context, machineId) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Copia")
            }
            OutlinedButton(onClick = { shareDeviceId(context, machineId) }) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Condividi")
            }
        }
        if (!activated) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it; result = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Codice di attivazione (KSR1:…)") },
                minLines = 2
            )
            Button(enabled = code.isNotBlank(), onClick = {
                result = if (controller.activateLicense(code)) {
                    "Attivazione completata."
                } else {
                    "Codice non valido per questo dispositivo. Verifica di aver inviato l'ID mostrato qui sopra."
                }
            }) { Text("Attiva") }
            result?.let { Text(it, color = KiwiColors.Error, style = MaterialTheme.typography.bodySmall) }
        }
        Text(
            "L'ID cambia dopo un ripristino di fabbrica o con un nuovo profilo utente: in quel caso richiedi un nuovo codice.",
            style = MaterialTheme.typography.bodySmall,
            color = KiwiColors.TextSecondary
        )
    }
}

@Composable
private fun AboutSection() {
    Panel(title = "Informazioni") {
        Text("KiwiSDR Grabber per Android ${BuildConfig.VERSION_NAME}")
        Text(
            "Client di ascolto per ricevitori KiwiSDR. Licenza PolyForm Noncommercial 1.0.0. " +
                "Per escludere l'app dall'ottimizzazione della batteria durante l'ascolto prolungato, usa le impostazioni di sistema.",
            style = MaterialTheme.typography.bodySmall,
            color = KiwiColors.TextSecondary
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("ID dispositivo KiwiSDR Grabber", text))
}

private fun shareDeviceId(context: Context, machineId: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Richiesta attivazione KiwiSDR Grabber Android\nID dispositivo: $machineId")
    }
    context.startActivity(Intent.createChooser(intent, "Condividi l'ID dispositivo"))
}
