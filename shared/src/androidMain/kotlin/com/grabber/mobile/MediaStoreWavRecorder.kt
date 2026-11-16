package com.grabber.mobile

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.MediaStore
import com.grabber.app.AppLogger
import com.grabber.model.ModulationMode
import com.grabber.recording.WavWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Registrazione manuale in WAV PCM 16 bit mono, salvata in MediaStore (`Recordings/KiwiGrabber`
 * da Android 12, `Music/KiwiGrabber` prima) senza permessi di storage.
 *
 * Il file resta `IS_PENDING` durante la scrittura: se l'app viene terminata, l'URI rimane nelle
 * impostazioni e [repairPending] ricostruisce l'header e pubblica il file al successivo avvio.
 */
class MediaStoreWavRecorder(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onPendingUriChanged: (String?) -> Unit,
    private val onStoppedForLowSpace: () -> Unit
) {
    private class Session(
        val uri: Uri,
        val descriptor: ParcelFileDescriptor,
        val writer: WavWriter,
        val startedAtMs: Long
    )

    private val chunks = Channel<ByteArray>(capacity = 512)
    @Volatile private var session: Session? = null
    private var writerJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _bytesWritten = MutableStateFlow(0L)
    val bytesWritten: StateFlow<Long> = _bytesWritten.asStateFlow()

    private val _fileName = MutableStateFlow<String?>(null)
    val fileName: StateFlow<String?> = _fileName.asStateFlow()

    /** @return null se avviata, altrimenti il messaggio d'errore da mostrare. */
    @Synchronized
    fun start(sampleRate: Int, frequencyKhz: Double, mode: ModulationMode): String? {
        if (session != null) return null
        if (freeBytes() < MIN_FREE_BYTES_TO_START) {
            return "Spazio libero insufficiente per avviare la registrazione."
        }
        val name = fileNameFor(frequencyKhz, mode)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/x-wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativeDirectory())
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = try {
            resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        } catch (e: Exception) {
            AppLogger.error(TAG, "Creazione del file in MediaStore non riuscita", e)
            null
        } ?: return "Impossibile creare il file di registrazione."

        return try {
            val descriptor = resolver.openFileDescriptor(uri, "rw")
                ?: throw IllegalStateException("Descrittore non disponibile per $uri")
            val writer = WavWriter(FileOutputStream(descriptor.fileDescriptor).channel, sampleRate)
            session = Session(uri, descriptor, writer, System.currentTimeMillis())
            onPendingUriChanged(uri.toString())
            _fileName.value = name
            _elapsedSeconds.value = 0
            _bytesWritten.value = 0
            _isRecording.value = true
            writerJob = scope.launch(Dispatchers.IO) { drainLoop() }
            AppLogger.info(TAG, "Registrazione avviata: $name")
            null
        } catch (e: Exception) {
            AppLogger.error(TAG, "Apertura del file di registrazione non riuscita", e)
            runCatching { resolver.delete(uri, null, null) }
            "Impossibile aprire il file di registrazione."
        }
    }

    fun submit(pcm: ByteArray) {
        if (session == null || pcm.isEmpty()) return
        if (chunks.trySend(pcm).isFailure) AppLogger.warn(TAG, "Coda di registrazione piena: blocco audio scartato")
    }

    /** Chiude il file in modo pulito (header aggiornato) e lo rende visibile alle altre app. */
    fun stop() {
        val current = synchronized(this) { session.also { session = null } } ?: return
        _isRecording.value = false
        val job = writerJob
        writerJob = null
        scope.launch(Dispatchers.IO) {
            // Il ciclo di scrittura è sospeso in attesa di dati: va annullato, non atteso.
            job?.cancelAndJoin()
            while (session == null) { // una nuova registrazione già avviata tiene i propri blocchi
                val pending = chunks.tryReceive().getOrNull() ?: break
                runCatching { current.writer.write(pending) }
            }
            finalize(current)
        }
    }

    private suspend fun drainLoop() {
        var lastSpaceCheckMs = System.currentTimeMillis()
        while (true) {
            val active = session ?: return
            val pcm = chunks.receiveCatching().getOrNull() ?: return
            try {
                active.writer.write(pcm)
                _bytesWritten.value = active.writer.dataBytes
                _elapsedSeconds.value = (System.currentTimeMillis() - active.startedAtMs) / 1000
            } catch (e: Exception) {
                AppLogger.error(TAG, "Scrittura della registrazione non riuscita", e)
                stop()
                return
            }
            val now = System.currentTimeMillis()
            if (now - lastSpaceCheckMs >= SPACE_CHECK_INTERVAL_MS) {
                lastSpaceCheckMs = now
                if (freeBytes() < MIN_FREE_BYTES_TO_CONTINUE) {
                    AppLogger.warn(TAG, "Spazio quasi esaurito: registrazione interrotta")
                    stop()
                    onStoppedForLowSpace()
                    return
                }
            }
        }
    }

    private fun finalize(current: Session) {
        try {
            current.writer.finish()
        } catch (e: Exception) {
            AppLogger.error(TAG, "Aggiornamento dell'header WAV non riuscito", e)
        } finally {
            runCatching { current.writer.close() }
            runCatching { current.descriptor.close() }
        }
        publish(current.uri)
        onPendingUriChanged(null)
        AppLogger.info(TAG, "Registrazione salvata (${current.writer.dataBytes} byte audio)")
    }

    private fun publish(uri: Uri) {
        try {
            val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            context.contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            AppLogger.error(TAG, "Pubblicazione della registrazione non riuscita: $uri", e)
        }
    }

    /**
     * Ripara una registrazione rimasta `IS_PENDING` dopo una chiusura forzata: ricalcola le
     * dimensioni dell'header e pubblica il file; un file vuoto o non valido viene eliminato.
     */
    fun repairPending(uriString: String) {
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver
        try {
            val isWav = resolver.openInputStream(uri)?.use { input ->
                val riff = ByteArray(4)
                input.read(riff) == 4 && String(riff, Charsets.US_ASCII) == "RIFF"
            } ?: false
            val audioBytes = if (isWav) {
                resolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
                    FileOutputStream(descriptor.fileDescriptor).channel.use { channel ->
                        WavWriter.repairHeader(channel, verifyRiff = false)
                    }
                } ?: -1L
            } else {
                -1L
            }
            if (audioBytes > 0) {
                publish(uri)
                AppLogger.info(TAG, "Registrazione interrotta riparata: $uri ($audioBytes byte audio)")
            } else {
                resolver.delete(uri, null, null)
                AppLogger.warn(TAG, "Registrazione interrotta vuota o non valida eliminata: $uri")
            }
        } catch (e: Exception) {
            AppLogger.error(TAG, "Riparazione della registrazione interrotta non riuscita: $uri", e)
        } finally {
            onPendingUriChanged(null)
        }
    }

    private fun relativeDirectory(): String {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Environment.DIRECTORY_RECORDINGS
        } else {
            Environment.DIRECTORY_MUSIC
        }
        return "$base/KiwiGrabber"
    }

    @Suppress("DEPRECATION") // Volume condiviso: è quello su cui MediaStore scrive i file.
    private fun freeBytes(): Long =
        try {
            StatFs(Environment.getExternalStorageDirectory().path).availableBytes
        } catch (e: IllegalArgumentException) {
            AppLogger.warn(TAG, "Spazio libero non determinabile", e)
            Long.MAX_VALUE
        }

    private fun fileNameFor(frequencyKhz: Double, mode: ModulationMode): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val frequency = String.format(Locale.US, "%.1f", frequencyKhz).removeSuffix(".0")
        return "KiwiGrabber_${timestamp}_${frequency}kHz_${mode.displayName}.wav"
    }

    private companion object {
        const val TAG = "MediaStoreWavRecorder"
        const val MIN_FREE_BYTES_TO_START = 50L * 1024 * 1024
        const val MIN_FREE_BYTES_TO_CONTINUE = 20L * 1024 * 1024
        const val SPACE_CHECK_INTERVAL_MS = 10_000L
    }
}
