package com.grabber.alerting

import com.grabber.app.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

object TelegramAlertService {
    private const val TAG = "TelegramAlertService"
    private const val MAX_AUDIO_BYTES = 50L * 1024 * 1024
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    suspend fun sendMessage(
        botToken: String,
        chatId: String,
        text: String
    ): Pair<Boolean, String> {
        val validationError = validateCredentials(botToken, chatId)
        if (validationError != null) return false to validationError

        return withContext(Dispatchers.IO) {
            try {
                val parts = text.chunked(4000).ifEmpty { listOf("") }
                for (part in parts) {
                    val form = "chat_id=${encode(botId(chatId))}&text=${encode(part)}"
                    val request = HttpRequest.newBuilder()
                        .uri(apiUri(botToken, "sendMessage"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                        .build()
                    val result = sendRequest(request)
                    if (!result.first) return@withContext result
                }
                true to "Messaggio inviato con successo!"
            } catch (e: Exception) {
                reportException("messaggio", e)
            }
        }
    }

    suspend fun sendAudio(
        botToken: String,
        chatId: String,
        audioFile: File,
        caption: String
    ): Pair<Boolean, String> {
        val validationError = validateCredentials(botToken, chatId)
        if (validationError != null) return false to validationError
        if (!audioFile.isFile) return false to "File audio non trovato."
        if (audioFile.length() > MAX_AUDIO_BYTES) {
            return false to "Il file supera il limite Telegram di 50 MB."
        }

        return withContext(Dispatchers.IO) {
            try {
                val boundary = "KiwiGrabber-${UUID.randomUUID()}"
                val safeFileName = audioFile.name.replace("\"", "_").replace("\r", "_").replace("\n", "_")
                val isMusicFile = audioFile.extension.equals("mp3", ignoreCase = true) ||
                    audioFile.extension.equals("m4a", ignoreCase = true)
                val method = if (isMusicFile) "sendAudio" else "sendDocument"
                val fieldName = if (isMusicFile) "audio" else "document"
                val prefix = buildString {
                    append("--$boundary\r\n")
                    append("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n")
                    append("${botId(chatId)}\r\n")
                    append("--$boundary\r\n")
                    append("Content-Disposition: form-data; name=\"caption\"\r\n\r\n")
                    append("${caption.take(1024)}\r\n")
                    append("--$boundary\r\n")
                    append("Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$safeFileName\"\r\n")
                    append("Content-Type: application/octet-stream\r\n\r\n")
                }.toByteArray(StandardCharsets.UTF_8)
                val suffix = "\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8)
                val body = HttpRequest.BodyPublishers.concat(
                    HttpRequest.BodyPublishers.ofByteArray(prefix),
                    HttpRequest.BodyPublishers.ofFile(audioFile.toPath()),
                    HttpRequest.BodyPublishers.ofByteArray(suffix)
                )
                val request = HttpRequest.newBuilder()
                    .uri(apiUri(botToken, method))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "multipart/form-data; boundary=$boundary")
                    .POST(body)
                    .build()
                sendRequest(request)
            } catch (e: Exception) {
                reportException("file audio", e)
            }
        }
    }

    private fun validateCredentials(botToken: String, chatId: String): String? = when {
        botToken.trim().isBlank() -> "Token del Bot Telegram non specificato."
        chatId.trim().isBlank() -> "Chat ID Telegram non specificato."
        else -> null
    }

    private fun botId(chatId: String): String = chatId.trim()

    private fun apiUri(botToken: String, method: String): URI {
        val token = botToken.trim()
        require(token.matches(Regex("[0-9]+:[A-Za-z0-9_-]+"))) { "Token Bot Telegram non valido." }
        return URI.create("https://api.telegram.org/bot$token/$method")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun sendRequest(request: HttpRequest): Pair<Boolean, String> {
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val statusCode = response.statusCode()
        if (statusCode in 200..299) {
            AppLogger.info(TAG, "Invio Telegram completato.")
            return true to "Messaggio inviato con successo!"
        }
        val details = response.body().take(500)
        AppLogger.warn(TAG, "Errore invio Telegram (HTTP $statusCode): $details")
        return false to "Errore Telegram (HTTP $statusCode): $details"
    }

    private fun reportException(action: String, error: Exception): Pair<Boolean, String> {
        val errorMessage = (error.message ?: "Errore di rete sconosciuto")
            .replace(Regex("bot[0-9]+:[A-Za-z0-9_-]+"), "bot<redacted>")
        AppLogger.error(TAG, "Eccezione invio $action Telegram: $errorMessage")
        return false to "Errore di connessione a Telegram: $errorMessage"
    }
}
