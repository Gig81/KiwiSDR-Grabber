package com.grabber.app

import com.grabber.alerting.KeywordMatcher
import com.grabber.alerting.TelegramAlertService
import com.grabber.model.TranscriptionStatus
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/** Frequenza, modo e ora di inizio del QSO a cui si riferisce una notifica. */
internal data class QsoAlertContext(
    val frequencyKhz: Double,
    val mode: String,
    val startedAt: String
)

private data class QsoAlertRecording(
    val context: QsoAlertContext,
    val filePath: String
)

internal class TelegramAlertCooldown {
    private var lastSentAtNanos: Long? = null

    fun allowsSend(nowNanos: Long, intervalMinutes: Int): Boolean {
        val lastSent = lastSentAtNanos ?: return true
        val intervalNanos = intervalMinutes.coerceAtLeast(0) * 60_000_000_000L
        return intervalNanos == 0L || nowNanos - lastSent >= intervalNanos
    }

    fun recordSent(sentAtNanos: Long) {
        lastSentAtNanos = sentAtNanos
    }
}

/**
 * Notifiche Telegram legate ai QSO: inizio e fine trasmissione e, a trascrizione conclusa,
 * parole chiave rilevate con l'eventuale audio e testo allegati. Rispetta il gate di licenza
 * e l'intervallo minimo tra messaggi configurato dall'utente.
 */
internal class QsoAlertCoordinator(
    private val scope: CoroutineScope,
    private val appSettings: AppSettings,
    private val hasAdvancedFeatures: () -> Boolean,
    private val currentTuning: () -> Pair<Double, String>,
    private val receiverAddress: () -> String,
    private val onDeliveryFailure: (String) -> Unit
) {
    private companion object {
        const val TELEGRAM_CAPTION_LIMIT = 1024
        val QSO_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    @Volatile var activeContext: QsoAlertContext? = null
        private set

    private val alertableRecordings = ConcurrentHashMap<Long, QsoAlertRecording>()
    private val sendMutex = Mutex()
    private val cooldown = TelegramAlertCooldown()

    fun onQsoStarted() {
        val (frequencyKhz, mode) = currentTuning()
        val context = QsoAlertContext(frequencyKhz, mode, LocalDateTime.now().format(QSO_TIME_FORMAT))
        activeContext = context
        val settings = appSettings.settings.value
        if (alertsAllowed(settings) && (settings.telegramNotifyAnyQso || settings.telegramNotifyQsoStart)) {
            val title = if (settings.telegramNotifyAnyQso) "QSO rilevato" else "Inizio QSO"
            launchAlert { send(context, title) }
        }
    }

    fun onQsoEnded() {
        val context = activeContext
        activeContext = null
        val settings = appSettings.settings.value
        if (context != null && alertsAllowed(settings) && settings.telegramNotifyQsoEnd) {
            launchAlert { send(context, "Fine trasmissione (Hang Time scaduto)") }
        }
    }

    /** Associa una registrazione al QSO in corso, per valutarne la trascrizione quando sarà pronta. */
    fun trackRecording(recordingId: Long, context: QsoAlertContext?, filePath: String, transcriptionRequested: Boolean) {
        if (context != null && transcriptionRequested && appSettings.settings.value.telegramAlertsEnabled) {
            alertableRecordings[recordingId] = QsoAlertRecording(context, filePath)
        }
    }

    fun onTranscriptionFinished(recordingId: Long, status: TranscriptionStatus, text: String?) {
        launchAlert { alertForTranscription(recordingId, status, text) }
    }

    private fun alertsAllowed(settings: SettingsData) = hasAdvancedFeatures() && settings.telegramAlertsEnabled

    private fun launchAlert(block: suspend () -> Unit) {
        scope.launch(Dispatchers.IO + CoroutineName("notifica Telegram")) { block() }
    }

    private suspend fun alertForTranscription(recordingId: Long, status: TranscriptionStatus, text: String?) {
        val recording = alertableRecordings.remove(recordingId) ?: return
        val settings = appSettings.settings.value
        if (!alertsAllowed(settings)) return

        val matches = if (status == TranscriptionStatus.COMPLETED) {
            KeywordMatcher.findMatches(text.orEmpty(), settings.telegramSensitiveWords)
        } else {
            emptyList()
        }
        if (!shouldNotifyTranscription(settings, matches)) return

        val reason = when {
            matches.isNotEmpty() -> "Parole chiave rilevate: ${matches.joinToString(", ")}"
            status == TranscriptionStatus.FAILED -> "QSO rilevato; trascrizione non disponibile."
            else -> "QSO rilevato"
        }
        val audioFile = File(recording.filePath).takeIf { settings.telegramSendAudioAttachment && it.isFile }
        val transcription = text.orEmpty()
            .takeIf { settings.telegramSendTranscription && status == TranscriptionStatus.COMPLETED && it.isNotBlank() }
        val message = buildString {
            append(reason)
            append(qsoDetails(recording.context))
            if (transcription != null) append("\n\n$transcription")
        }
        send(recording.context, message, audioFile)
    }

    private fun shouldNotifyTranscription(settings: SettingsData, matches: List<String>): Boolean = when {
        matches.isNotEmpty() -> true
        !settings.telegramNotifyAnyQso -> false
        else -> settings.telegramSendAudioAttachment || settings.telegramSendTranscription
    }

    private fun qsoDetails(context: QsoAlertContext) =
        "\nRicevitore: ${receiverAddress()}" +
            "\nFrequenza: ${context.frequencyKhz} kHz (${context.mode})" +
            "\nOra: ${context.startedAt}"

    private suspend fun send(context: QsoAlertContext, message: String, audioFile: File? = null) {
        val settings = appSettings.settings.value
        if (!alertsAllowed(settings)) return

        sendMutex.withLock {
            if (!cooldown.allowsSend(System.nanoTime(), settings.telegramMessageIntervalMinutes)) return
            val fullMessage = if (message.contains("Frequenza:")) message else message + qsoDetails(context)
            val (sent, result) = deliver(settings, fullMessage, audioFile)
            if (sent) {
                cooldown.recordSent(System.nanoTime())
            } else {
                onDeliveryFailure("Notifica Telegram non inviata: $result")
            }
        }
    }

    /** Con un allegato, un testo oltre il limite della didascalia viene inviato anche come messaggio separato. */
    private suspend fun deliver(settings: SettingsData, message: String, audioFile: File?): Pair<Boolean, String> {
        val token = settings.telegramBotToken
        val chatId = settings.telegramChatId
        if (audioFile == null) return TelegramAlertService.sendMessage(token, chatId, message)

        val exceedsCaption = message.length > TELEGRAM_CAPTION_LIMIT
        val caption = if (exceedsCaption) message.substringBefore("\n\n") else message
        val audioResult = TelegramAlertService.sendAudio(token, chatId, audioFile, caption)
        return if (audioResult.first && exceedsCaption) {
            TelegramAlertService.sendMessage(token, chatId, message)
        } else {
            audioResult
        }
    }
}
