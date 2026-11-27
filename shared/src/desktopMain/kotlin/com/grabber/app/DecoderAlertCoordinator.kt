package com.grabber.app

import com.grabber.alerting.KeywordMatcher
import com.grabber.alerting.TelegramAlertService
import com.grabber.decoder.DecodedEvent
import com.grabber.model.digital.DecodedTextLine
import com.grabber.model.digital.NavtexMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Regole delle notifiche dei decoder, senza I/O: parole chiave dedicate (separate da quelle delle
 * trascrizioni), al massimo una notifica ogni [minIntervalMs] per parola chiave, nessun duplicato
 * dello stesso messaggio NAVTEX (identificativo) né dello stesso testo.
 */
class DecoderAlertRules(private val minIntervalMs: Long = MIN_INTERVAL_MS) {
    companion object {
        const val MIN_INTERVAL_MS = 30_000L
        private const val DUPLICATE_WINDOW_MS = 24L * 3600 * 1000
    }

    private val lastByKeyword = HashMap<String, Long>()
    private val seen = HashMap<String, Long>()

    /** Parole chiave che fanno scattare una notifica per [event], vuoto se non va notificato. */
    @Synchronized
    fun matches(event: DecodedEvent, keywords: String, now: Long): List<String> {
        val item = event.item
        if (item is DecodedTextLine && item.isStatus) return emptyList()
        val text = item.plainText
        val found = KeywordMatcher.findMatches(text, keywords)
        if (found.isEmpty()) return emptyList()
        val key = if (item is NavtexMessage && !item.id.endsWith("00")) "NAVTEX:${item.id}" else "${event.mode}:${text.trim()}"
        seen.entries.removeIf { now - it.value > DUPLICATE_WINDOW_MS }
        if (key in seen) return emptyList()
        val allowed = found.filter { word ->
            val last = lastByKeyword[word.lowercase()]
            last == null || now - last >= minIntervalMs
        }
        if (allowed.isEmpty()) return emptyList()
        allowed.forEach { lastByKeyword[it.lowercase()] = now }
        seen[key] = now
        return allowed
    }
}

/**
 * Notifiche Telegram dei decoder (fase 8, decisione D7): riceve gli elementi completati dai decoder e
 * invia un messaggio con modo, frequenza, ora UTC e testo quando contengono le parole chiave dedicate.
 * Riservato alla licenza come gli altri avvisi.
 */
internal class DecoderAlertCoordinator(
    private val scope: CoroutineScope,
    private val appSettings: AppSettings,
    private val hasAdvancedFeatures: () -> Boolean,
    private val currentFrequencyKhz: () -> Double,
    private val onDeliveryFailure: (String) -> Unit,
    private val rules: DecoderAlertRules = DecoderAlertRules(),
    private val send: suspend (token: String, chatId: String, text: String) -> Pair<Boolean, String> = TelegramAlertService::sendMessage,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val utc = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    fun start(events: Flow<DecodedEvent>) {
        events.onEach(::handle).launchIn(scope)
    }

    private fun handle(event: DecodedEvent) {
        val settings = appSettings.settings.value
        if (!settings.decoderAlertsEnabled || !hasAdvancedFeatures()) return
        if (settings.telegramBotToken.isBlank() || settings.telegramChatId.isBlank()) return
        val words = rules.matches(event, settings.decoderAlertWords, clock())
        if (words.isEmpty()) return
        val message = buildString {
            append("KiwiSDR Grabber — ${event.mode.displayName}\n")
            append("Frequenza: ${"%.3f".format(java.util.Locale.US, currentFrequencyKhz())} kHz\n")
            append("Ora: ${utc.format(Instant.ofEpochMilli(event.item.receivedAtMillis))} UTC\n")
            append("Parole chiave: ${words.joinToString(", ")}\n\n")
            append(event.item.plainText.take(3500))
        }
        scope.launch(Dispatchers.IO) {
            val (ok, detail) = send(settings.telegramBotToken, settings.telegramChatId, message)
            if (ok) {
                AppLogger.info("DecoderAlerts", "Notifica ${event.mode.displayName} inviata (${words.joinToString()})")
            } else {
                AppLogger.warn("DecoderAlerts", "Notifica ${event.mode.displayName} non inviata: $detail")
                onDeliveryFailure("Notifica Telegram del decoder non inviata: $detail")
            }
        }
    }
}
