package com.grabber.stt

import com.grabber.app.AppLogger
import com.grabber.model.TranscriptionStatus
import com.grabber.monitor.HistoryDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

class OfflineTranscriptionQueue(
    scope: CoroutineScope,
    private val database: HistoryDatabase,
    private val modelPathProvider: () -> String,
    private val onTranscriptionUpdated: (recordingId: Long, status: TranscriptionStatus, text: String?) -> Unit
) {
    private data class Job(val recordingId: Long, val filePath: String)

    private val jobs = Channel<Job>(Channel.UNLIMITED)
    private val transcriber = VoskTranscriber()

    init {
        scope.launch(Dispatchers.IO) {
            try {
                for (job in jobs) {
                    transcribe(job)
                }
            } finally {
                transcriber.close()
            }
        }
    }

    fun enqueue(recordingId: Long, filePath: String) {
        check(jobs.trySend(Job(recordingId, filePath)).isSuccess) {
            "La coda di trascrizione è stata chiusa"
        }
    }

    fun close() {
        jobs.close()
    }

    private fun transcribe(job: Job) {
        try {
            database.updateTranscription(job.recordingId, TranscriptionStatus.PROCESSING)
            onTranscriptionUpdated(job.recordingId, TranscriptionStatus.PROCESSING, null)
            val modelPath = modelPathProvider()
            val text = transcriber.transcribe(File(job.filePath), modelPath)
            database.updateTranscription(job.recordingId, TranscriptionStatus.COMPLETED, text)
            onTranscriptionUpdated(job.recordingId, TranscriptionStatus.COMPLETED, text)
        } catch (e: Exception) {
            val reason = e.message ?: e::class.simpleName ?: "Errore sconosciuto"
            markFailed(job, reason, e)
        } catch (e: LinkageError) {
            val reason = "Libreria nativa Vosk non disponibile: ${e.message ?: "errore di caricamento"}"
            markFailed(job, reason, e)
        }
    }

    private fun markFailed(job: Job, reason: String, cause: Throwable) {
        AppLogger.error("OfflineTranscriptionQueue", "Trascrizione fallita per ${job.filePath}: $reason", cause)
        try {
            database.updateTranscription(job.recordingId, TranscriptionStatus.FAILED, error = reason)
            onTranscriptionUpdated(job.recordingId, TranscriptionStatus.FAILED, reason)
        } catch (e: Exception) {
            AppLogger.error(
                "OfflineTranscriptionQueue",
                "Impossibile salvare l'esito negativo della trascrizione ${job.recordingId}",
                e
            )
        }
    }
}

private class VoskTranscriber : AutoCloseable {
    private var loadedModelPath: String? = null
    private var model: Model? = null

    @Synchronized
    fun transcribe(file: File, modelPath: String): String {
        require(file.isFile) { "File audio non trovato: ${file.absolutePath}" }
        val modelDirectory = File(modelPath)
        require(modelDirectory.isDirectory && File(modelDirectory, "am").exists()) {
            "Cartella modello Vosk non valida: seleziona la cartella estratta del modello italiano"
        }

        val activeModel = getModel(modelDirectory)
        val targetFormat = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 12000f, 16, 1, 2, 12000f, false)
        val phrases = mutableListOf<String>()
        val recognizer = Recognizer(activeModel, targetFormat.sampleRate)
        try {
            AudioSystem.getAudioInputStream(file).use { source ->
                val pcmStream = if (AudioSystem.isConversionSupported(targetFormat, source.format)) {
                    AudioSystem.getAudioInputStream(targetFormat, source)
                } else {
                    require(
                        source.format.encoding == AudioFormat.Encoding.PCM_SIGNED &&
                            source.format.sampleRate == targetFormat.sampleRate &&
                            source.format.sampleSizeInBits == 16 &&
                            source.format.channels == 1 &&
                            !source.format.isBigEndian
                    ) { "Formato audio non supportato per la trascrizione: ${source.format}" }
                    source
                }

                pcmStream.use { audio ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = audio.read(buffer)
                        if (count < 0) break
                        if (count > 0 && recognizer.acceptWaveForm(buffer, count)) {
                            extractText(recognizer.result)?.takeIf(String::isNotBlank)?.let(phrases::add)
                        }
                    }
                }
            }
            extractText(recognizer.finalResult)?.takeIf(String::isNotBlank)?.let(phrases::add)
        } finally {
            recognizer.close()
        }
        return phrases.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    }

    private fun getModel(directory: File): Model {
        val path = directory.canonicalPath
        if (loadedModelPath != path) {
            model?.close()
            model = Model(path)
            loadedModelPath = path
        }
        return checkNotNull(model)
    }

    private fun extractText(jsonText: String): String? =
        Json.parseToJsonElement(jsonText).jsonObject["text"]?.jsonPrimitive?.contentOrNull

    @Synchronized
    override fun close() {
        model?.close()
        model = null
        loadedModelPath = null
    }
}
