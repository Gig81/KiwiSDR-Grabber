package com.grabber.mobile

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.grabber.app.AppLogger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

/**
 * Riproduzione PCM 16 bit mono con `AudioTrack` in streaming.
 *
 * I blocchi arrivano dalla pipeline (FIFO, già passati per lo squelch) e vengono scritti da un
 * thread dedicato con scrittura bloccante. La coda è limitata: se la rete recapita a raffica,
 * i blocchi più vecchi vengono scartati invece di accumulare ritardo. La riproduzione parte
 * solo dopo [PREBUFFER_MS] di audio, come buffer di jitter per la rete mobile.
 */
class AudioTrackOutput {
    private class Chunk(val pcm: ByteArray, val sampleRate: Int)

    private val queue = Channel<Chunk>(capacity = QUEUE_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    @Volatile private var writerThread: Thread? = null
    @Volatile private var track: AudioTrack? = null
    private var trackSampleRate = 0
    private var bufferedBeforePlay = 0

    private val _volume = MutableStateFlow(0.8f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    /** Attenuazione temporanea richiesta dall'audio focus (1 = nessuna, 0 = silenzio). */
    @Volatile private var focusGain = 1f

    fun start() {
        if (writerThread != null) return
        writerThread = Thread(::writeLoop, "KiwiAudioTrack").apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
    }

    fun submit(pcm: ByteArray, sampleRate: Int) {
        if (pcm.isEmpty()) return
        queue.trySend(Chunk(pcm, sampleRate))
    }

    fun setVolume(value: Float) {
        _volume.value = value.coerceIn(0f, 1f)
        applyGain()
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        applyGain()
    }

    fun setFocusGain(gain: Float) {
        focusGain = gain.coerceIn(0f, 1f)
        applyGain()
    }

    /** Svuota la coda e ferma la riproduzione, ad esempio alla disconnessione. */
    fun flush() {
        @Suppress("ControlFlowWithEmptyBody")
        while (queue.tryReceive().isSuccess) {
            // scarta i blocchi in coda
        }
        synchronized(this) {
            track?.let {
                runCatching { it.pause(); it.flush() }
                    .onFailure { e -> AppLogger.warn(TAG, "Flush AudioTrack non riuscito", e) }
            }
            bufferedBeforePlay = 0
        }
    }

    fun release() {
        writerThread?.interrupt()
        writerThread = null
        synchronized(this) { releaseTrack() }
    }

    private fun writeLoop() {
        try {
            while (!Thread.currentThread().isInterrupted) {
                val chunk = runBlocking { queue.receive() }
                synchronized(this) { write(chunk) }
            }
        } catch (_: InterruptedException) {
            AppLogger.debug(TAG, "Thread audio interrotto")
        } catch (e: Exception) {
            AppLogger.error(TAG, "Errore nella riproduzione audio", e)
        }
    }

    private fun write(chunk: Chunk) {
        val audioTrack = trackFor(chunk.sampleRate) ?: return
        audioTrack.write(chunk.pcm, 0, chunk.pcm.size, AudioTrack.WRITE_BLOCKING)
        if (audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
            bufferedBeforePlay += chunk.pcm.size
            if (bufferedBeforePlay >= chunk.sampleRate * 2 * PREBUFFER_MS / 1000) {
                audioTrack.play()
                bufferedBeforePlay = 0
            }
        }
    }

    private fun trackFor(sampleRate: Int): AudioTrack? {
        track?.takeIf { trackSampleRate == sampleRate }?.let { return it }
        releaseTrack()
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) {
            AppLogger.error(TAG, "Sample rate non supportato da AudioTrack: $sampleRate Hz")
            return null
        }
        val bufferBytes = maxOf(minBuffer, sampleRate * 2 * BUFFER_MS / 1000)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also {
                track = it
                trackSampleRate = sampleRate
                bufferedBeforePlay = 0
                applyGain()
                AppLogger.info(TAG, "AudioTrack creato: $sampleRate Hz, buffer $bufferBytes byte")
            }
    }

    private fun applyGain() {
        val gain = if (_isMuted.value) 0f else _volume.value * focusGain
        track?.setVolume(gain)
    }

    private fun releaseTrack() {
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
        trackSampleRate = 0
    }

    private companion object {
        const val TAG = "AudioTrackOutput"
        const val QUEUE_CAPACITY = 24
        const val PREBUFFER_MS = 250
        const val BUFFER_MS = 600
    }
}
