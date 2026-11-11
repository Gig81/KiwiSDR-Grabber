package com.grabber.recording

import com.grabber.model.AudioFrame
import de.sciss.jump3r.lowlevel.LameEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat

enum class RecordingInterval(val minutes: Int, val label: String) {
    CONTINUOUS(0, "Manuale / Continuo"),
    MINUTES_5(5, "Ogni 5 minuti"),
    MINUTES_10(10, "Ogni 10 minuti"),
    MINUTES_15(15, "Ogni 15 minuti"),
    MINUTES_30(30, "Ogni 30 minuti"),
    MINUTES_60(60, "Ogni 60 minuti (1h)")
}

enum class AudioRecordingFormat(val extension: String, val label: String) {
    MP3("mp3", "MP3 (Compresso LAME)"),
    WAV("wav", "WAV (Linear PCM 16-bit)")
}

class AudioRecorder(
    var baseDirectory: File = File(System.getProperty("user.home"), "KiwiGrabberRecordings"),
    private val sampleRate: Int = 12000
) {
    private val isRecordingInternal = AtomicBoolean(false)
    private val queue = LinkedBlockingQueue<ByteArray>(256)
    private var writerThread: Thread? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isSquelchArmed = MutableStateFlow(false)
    val isSquelchArmed: StateFlow<Boolean> = _isSquelchArmed.asStateFlow()

    private val _currentFilePath = MutableStateFlow<String?>(null)
    val currentFilePath: StateFlow<String?> = _currentFilePath.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _recordedSegments = MutableStateFlow(0)
    val recordedSegments: StateFlow<Int> = _recordedSegments.asStateFlow()

    private val _format = MutableStateFlow(AudioRecordingFormat.MP3)
    val format: StateFlow<AudioRecordingFormat> = _format.asStateFlow()

    private var selectedInterval: RecordingInterval = RecordingInterval.CONTINUOUS
    val currentInterval: RecordingInterval get() = selectedInterval
    private var currentFile: File? = null
    @Volatile private var isSegmentFileOpen = false

    // Callback quando un segmento (o file QSO) viene salvato e finalizzato con successo
    var onSegmentCompleted: ((filePath: String, durationSec: Long) -> Unit)? = null

    // For WAV format
    private var currentRaf: RandomAccessFile? = null
    private var bytesWrittenInWav: Long = 0L

    // For MP3 format
    private var currentFos: FileOutputStream? = null
    private var currentLameEncoder: LameEncoder? = null
    private val mp3Buffer = ByteArray(16384)

    private var segmentStartTimeMs: Long = 0L
    private var totalStartTimeMs: Long = 0L

    fun setRecordingFormat(newFormat: AudioRecordingFormat) {
        if (!isRecordingInternal.get()) {
            _format.value = newFormat
        }
    }

    fun startRecording(
        interval: RecordingInterval = RecordingInterval.CONTINUOUS,
        recordingFormat: AudioRecordingFormat = _format.value,
        squelchMode: Boolean = false
    ) {
        if (isRecordingInternal.getAndSet(true)) return

        _format.value = recordingFormat
        selectedInterval = interval
        bytesWrittenInWav = 0L
        _elapsedSeconds.value = 0L
        _recordedSegments.value = 0
        totalStartTimeMs = System.currentTimeMillis()
        segmentStartTimeMs = totalStartTimeMs
        _isSquelchArmed.value = squelchMode
        isSegmentFileOpen = false

        if (!squelchMode) {
            openNewSegmentFile()
            isSegmentFileOpen = true
        }

        _isRecording.value = true

        writerThread = Thread({
            while (isRecordingInternal.get()) {
                try {
                    val chunk = queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                    val now = System.currentTimeMillis()

                    // Aggiornamento tempo trascorso totale di sessione
                    _elapsedSeconds.value = (now - totalStartTimeMs) / 1000

                    // Se non in modalità squelch oppure se un file di segmento è aperto, verifica timeout intervallo
                    if (isSegmentFileOpen && selectedInterval.minutes > 0) {
                        val maxSegmentMs = selectedInterval.minutes * 60 * 1000L
                        if (now - segmentStartTimeMs >= maxSegmentMs) {
                            closeCurrentSegment()
                            openNewSegmentFile()
                            segmentStartTimeMs = now
                        }
                    }

                    if (chunk != null) {
                        writeChunk(chunk)
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    println("[RECORDER SCRITTURA ERRORE] ${e.message}")
                }
            }

            // Svuotamento buffer residuo al termine
            while (queue.isNotEmpty()) {
                val chunk = queue.poll()
                if (chunk != null) {
                    writeChunk(chunk)
                }
            }
            if (isSegmentFileOpen) {
                closeCurrentSegment()
                isSegmentFileOpen = false
            }
        }, "KiwiAudioWriter").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Chiamato quando lo Squelch si apre (segnale RF sopra soglia).
     * Inserisce in testa il pre-buffer circolare (300-500ms) e avvia la registrazione del file.
     */
    fun onSquelchGateOpened(preBufferBytes: ByteArray?) {
        if (!isRecordingInternal.get() || !_isSquelchArmed.value) return

        synchronized(this) {
            if (!isSegmentFileOpen) {
                segmentStartTimeMs = System.currentTimeMillis()
                openNewSegmentFile()
                isSegmentFileOpen = true

                if (preBufferBytes != null && preBufferBytes.isNotEmpty()) {
                    queue.offer(preBufferBytes)
                }
            }
        }
    }

    /**
     * Chiamato quando lo Squelch si chiude (Hang Time scaduto).
     * Finalizza il file del QSO e ritorna in attesa della prossima trasmissione.
     */
    fun onSquelchGateClosed() {
        if (!isRecordingInternal.get() || !_isSquelchArmed.value) return

        synchronized(this) {
            if (isSegmentFileOpen) {
                closeCurrentSegment()
                isSegmentFileOpen = false
                _currentFilePath.value = null
            }
        }
    }

    private fun writeChunk(chunk: ByteArray) {
        when (_format.value) {
            AudioRecordingFormat.MP3 -> {
                val encoder = currentLameEncoder
                val fos = currentFos
                if (encoder != null && fos != null) {
                    val encodedBytes = encoder.encodeBuffer(chunk, 0, chunk.size, mp3Buffer)
                    if (encodedBytes > 0) {
                        fos.write(mp3Buffer, 0, encodedBytes)
                    }
                }
            }
            AudioRecordingFormat.WAV -> {
                val raf = currentRaf
                if (raf != null) {
                    raf.write(chunk)
                    bytesWrittenInWav += chunk.size
                }
            }
        }
    }

    fun submitAudio(frame: AudioFrame) {
        if (!isRecordingInternal.get()) return

        if (_isSquelchArmed.value) {
            // In modalità squelch, scriviamo nel file solo quando il gate è aperto (o in Hang Time)
            if (isSegmentFileOpen) {
                queue.offer(frame.pcmBytes)
            }
        } else {
            queue.offer(frame.pcmBytes)
        }
    }

    private fun openNewSegmentFile() {
        val now = LocalDateTime.now()
        val dateDirName = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val ext = _format.value.extension
        val timeFileName = now.format(DateTimeFormatter.ofPattern("HH-mm-ss")) + ".$ext"

        val targetDir = File(baseDirectory, dateDirName)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val file = File(targetDir, timeFileName)
        currentFile = file
        _currentFilePath.value = file.absolutePath

        when (_format.value) {
            AudioRecordingFormat.MP3 -> {
                val audioFormat = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
                currentLameEncoder = LameEncoder(
                    audioFormat,
                    64, // 64 kbps mono is optimal for 12kHz KiwiSDR audio
                    LameEncoder.CHANNEL_MODE_MONO,
                    LameEncoder.QUALITY_MIDDLE,
                    false
                )
                currentFos = FileOutputStream(file)
            }
            AudioRecordingFormat.WAV -> {
                val raf = RandomAccessFile(file, "rw")
                raf.setLength(0)
                writeWavHeader(raf, 0)
                bytesWrittenInWav = 0L
                currentRaf = raf
            }
        }

        _recordedSegments.value = _recordedSegments.value + 1
    }

    private fun closeCurrentSegment() {
        val completedFile = currentFile
        val durationSec = maxOf(1L, (System.currentTimeMillis() - segmentStartTimeMs) / 1000)

        when (_format.value) {
            AudioRecordingFormat.MP3 -> {
                try {
                    val encoder = currentLameEncoder
                    val fos = currentFos
                    if (encoder != null && fos != null) {
                        val finishBytes = encoder.encodeFinish(mp3Buffer)
                        if (finishBytes > 0) {
                            fos.write(mp3Buffer, 0, finishBytes)
                        }
                        encoder.close()
                        fos.flush()
                        fos.close()
                    }
                } catch (e: Exception) {
                    println("[RECORDER MP3 CHIUSURA] ${e.message}")
                } finally {
                    currentLameEncoder = null
                    currentFos = null
                    currentFile = null
                }
            }
            AudioRecordingFormat.WAV -> {
                val raf = currentRaf
                try {
                    if (raf != null) {
                        val totalDataLen = bytesWrittenInWav
                        val totalFileLen = totalDataLen + 36

                        raf.seek(4)
                        raf.write(intToByteArrayLE(totalFileLen.toInt()))

                        raf.seek(40)
                        raf.write(intToByteArrayLE(totalDataLen.toInt()))

                        raf.close()
                    }
                } catch (e: Exception) {
                    println("[RECORDER WAV CHIUSURA] ${e.message}")
                } finally {
                    currentRaf = null
                    currentFile = null
                }
            }
        }

        if (completedFile != null && completedFile.exists() && completedFile.length() > 44) {
            onSegmentCompleted?.invoke(completedFile.absolutePath, durationSec)
        }
    }

    private fun writeWavHeader(raf: RandomAccessFile, dataSize: Int) {
        val header = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put("RIFF".toByteArray())
            putInt(dataSize + 36)
            put("WAVE".toByteArray())

            put("fmt ".toByteArray())
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2.toShort())
            putShort(16.toShort())

            put("data".toByteArray())
            putInt(dataSize)
        }
        raf.seek(0)
        raf.write(header.array())
    }

    private fun intToByteArrayLE(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    fun stopRecording() {
        if (!isRecordingInternal.getAndSet(false)) return
        writerThread?.interrupt()
        writerThread = null
        _isRecording.value = false
    }
}
