package com.grabber.ui.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.grabber.app.KiwiAppController
import com.grabber.app.SettingsData
import com.grabber.ui.designsystem.*
import kotlinx.coroutines.launch

/** Bot Telegram per le notifiche dei QSO (funzione con licenza). */
@Composable
internal fun TelegramSettingsSection(
    controller: KiwiAppController,
    settings: SettingsData,
    isMachineLicensed: Boolean,
    onActivateLicense: () -> Unit,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var tgToken by remember(settings.telegramBotToken) { mutableStateOf(settings.telegramBotToken) }
    var tgChatId by remember(settings.telegramChatId) { mutableStateOf(settings.telegramChatId) }
    var tgEnabled by remember(settings.telegramAlertsEnabled) { mutableStateOf(settings.telegramAlertsEnabled) }
    var tgNotifyAnyQso by remember(settings.telegramNotifyAnyQso) { mutableStateOf(settings.telegramNotifyAnyQso) }
    var tgNotifyQsoStart by remember(settings.telegramNotifyQsoStart) { mutableStateOf(settings.telegramNotifyQsoStart) }
    var tgNotifyQsoEnd by remember(settings.telegramNotifyQsoEnd) { mutableStateOf(settings.telegramNotifyQsoEnd) }
    var tgSensitiveWords by remember(settings.telegramSensitiveWords) { mutableStateOf(settings.telegramSensitiveWords) }
    var tgIntervalMin by remember(settings.telegramMessageIntervalMinutes) { mutableStateOf(settings.telegramMessageIntervalMinutes) }
    var tgSendAudio by remember(settings.telegramSendAudioAttachment) { mutableStateOf(settings.telegramSendAudioAttachment) }
    var tgSendTranscription by remember(settings.telegramSendTranscription) { mutableStateOf(settings.telegramSendTranscription) }
    var tgTestStatus by remember { mutableStateOf<String?>(null) }
    var isSendingTgTest by remember { mutableStateOf(false) }

    CollapsibleSettingsCard(
        title = "CONFIGURAZIONE BOT TELEGRAM PERSONALE (ALLERTE QSO & KEYWORD)",
        icon = Icons.AutoMirrored.Filled.Send,
        iconTint = GrabberColors.Secondary,
        isExpanded = isExpanded,
        onToggle = onToggle,
        subtitle = if (!isMachineLicensed) "Richiede attivazione" else if (tgEnabled) "Notifiche ATTIVE" else "Notifiche disattivate",
        locked = !isMachineLicensed,
        onActivate = onActivateLicense
    ) {
        Text(
            text = "Configura il tuo bot Telegram personale per ricevere notifiche sullo smartphone. Il token è salvato cifrato (Windows DPAPI) nelle impostazioni locali di questo utente. Gli avvisi e le funzioni avanzate richiedono l'attivazione; il riconoscimento delle parole richiede anche il modello Vosk.",
            fontSize = 10.sp,
            color = GrabberColors.TextSecondary
        )

        // Parametri Token & Chat ID
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Telegram Bot Token:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrabberColors.TextPrimary)
                CompactTextField(
                    value = tgToken,
                    onValueChange = { tgToken = it },
                    placeholder = "Es: 123456789:ABCdefGhIjk-lmnOP...",
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    fontSize = 10.sp,
                    visualTransformation = PasswordVisualTransformation()
                )
            }

            Column(modifier = Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Chat ID / Canale:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrabberColors.TextPrimary)
                CompactTextField(
                    value = tgChatId,
                    onValueChange = { tgChatId = it },
                    placeholder = "Es: 987654321 o @tuocanale",
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    fontSize = 10.sp
                )
            }
        }

        // Toggle Abilitazione & Opzione Any QSO
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppCheckbox(
                checked = tgEnabled,
                onCheckedChange = { tgEnabled = it },
                text = "Abilita Notifiche Telegram",
                size = ControlSize.Compact,
                accentColor = GrabberColors.Secondary,
                emphasized = true
            )

            AppCheckbox(
                checked = tgNotifyAnyQso,
                onCheckedChange = { tgNotifyAnyQso = it },
                text = "Avviso per QUALSIASI QSO rilevato",
                size = ControlSize.Compact,
                accentColor = GrabberColors.Warning,
                emphasized = true
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppCheckbox(
                checked = tgNotifyQsoStart,
                onCheckedChange = { tgNotifyQsoStart = it },
                text = "Notifica inizio QSO",
                size = ControlSize.Compact,
                accentColor = GrabberColors.Secondary
            )

            AppCheckbox(
                checked = tgNotifyQsoEnd,
                onCheckedChange = { tgNotifyQsoEnd = it },
                text = "Notifica fine trasmissione (Hang Time)",
                size = ControlSize.Compact,
                accentColor = GrabberColors.Secondary
            )
        }

        // Termini Sensibili / Parole Sentinella
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Termini Sensibili Sentinella (separati da virgola):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GrabberColors.TextPrimary)
            Text("Vengono intercettati automaticamente nel testo trascritto dal motore STT locale.", fontSize = 9.sp, color = GrabberColors.TextSecondary)
            if (settings.sttModelPath.isBlank()) {
                Text(
                    text = "Per rilevare le parole chiave configura prima un modello Vosk nella sezione Trascrizione Vocale Offline.",
                    fontSize = 9.sp,
                    color = GrabberColors.Warning
                )
            }
            CompactTextField(
                value = tgSensitiveWords,
                onValueChange = { tgSensitiveWords = it },
                placeholder = "emergenza, mayday, soccorso, pan pan, alluvione, iu7iya",
                modifier = Modifier.fillMaxWidth().height(32.dp),
                fontSize = 10.sp
            )
        }

        // Opzioni invio allegati e frequenza minima
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AppCheckbox(
                    checked = tgSendAudio,
                    onCheckedChange = { tgSendAudio = it },
                    text = "Allega file audio (MP3/WAV)",
                    size = ControlSize.Dense,
                    accentColor = GrabberColors.Success,
                    labelColor = GrabberColors.TextPrimary
                )
                AppCheckbox(
                    checked = tgSendTranscription,
                    onCheckedChange = { tgSendTranscription = it },
                    text = "Includi trascrizione STT",
                    size = ControlSize.Dense,
                    accentColor = GrabberColors.Success,
                    labelColor = GrabberColors.TextPrimary
                )
            }

            // Intervallo minimo messaggi (anti-flood)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Intervallo minimo:", fontSize = 9.sp, color = GrabberColors.TextSecondary)
                AppSegmentedControl(
                    options = TELEGRAM_INTERVALS.map { it.second },
                    selectedIndex = TELEGRAM_INTERVALS.indexOfFirst { it.first == tgIntervalMin },
                    onSelectionChange = { tgIntervalMin = TELEGRAM_INTERVALS[it].first },
                    size = ControlSize.Dense
                )
            }
        }

        // Pulsanti Azione Telegram: Salva & Test Invio
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = "Salva Impostazioni Telegram",
                    onClick = {
                        controller.updateTelegramSettings(
                            botToken = tgToken,
                            chatId = tgChatId,
                            enabled = tgEnabled,
                            notifyAnyQso = tgNotifyAnyQso,
                            sensitiveWords = tgSensitiveWords,
                            intervalMinutes = tgIntervalMin,
                            sendAudio = tgSendAudio,
                            sendTranscription = tgSendTranscription,
                            notifyQsoStart = tgNotifyQsoStart,
                            notifyQsoEnd = tgNotifyQsoEnd
                        )
                        tgTestStatus = "✓ Impostazioni Telegram salvate con successo!"
                    },
                    icon = Icons.Default.Save,
                    size = ControlSize.Compact,
                    containerColor = GrabberColors.Success,
                    contentColor = Color.Black
                )

                PrimaryButton(
                    text = if (isSendingTgTest) "Invio in corso..." else "Test Invio Notifica",
                    onClick = {
                        if (tgToken.isBlank() || tgChatId.isBlank()) {
                            tgTestStatus = "Inserisci Token e Chat ID prima di eseguire il test."
                        } else {
                            isSendingTgTest = true
                            tgTestStatus = "Invio messaggio di prova in corso..."
                            coroutineScope.launch {
                                val (ok, resText) = controller.sendTelegramTestMessage(tgToken, tgChatId)
                                isSendingTgTest = false
                                tgTestStatus = if (ok) "✓ $resText" else "✗ $resText"
                            }
                        }
                    },
                    enabled = !isSendingTgTest,
                    icon = Icons.AutoMirrored.Filled.Send,
                    size = ControlSize.Compact,
                    containerColor = GrabberColors.Secondary,
                    contentColor = Color.Black
                )
            }

            if (tgTestStatus != null) {
                Text(
                    text = tgTestStatus!!,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (tgTestStatus!!.startsWith("✓")) GrabberColors.Success else GrabberColors.Error
                )
            }
        }
    }
}

/** Intervallo minimo tra due notifiche: minuti ed etichetta. */
private val TELEGRAM_INTERVALS = listOf(
    0 to "Subito",
    1 to "1m",
    5 to "5m",
    15 to "15m",
    30 to "30m",
    60 to "60m"
)
