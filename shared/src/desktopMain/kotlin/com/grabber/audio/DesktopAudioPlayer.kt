package com.grabber.audio

import com.grabber.model.AudioFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.*
import kotlin.math.sqrt

class DesktopAudioPlayer(
    private val sampleRate: Float = 12000f
) {
    private var line: SourceDataLine? = null
    private val queue = LinkedBlockingQueue<ByteArray>(128)
    private val isRunning = AtomicBoolean(false)
    private var playbackThread: Thread? = null

    private val _volume = MutableStateFlow(0.8f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    private var lastLevelEmitTime = 0L

    fun start() {
        if (isRunning.getAndSet(true)) return

        try {
            // Formato standard KiwiSDR: PCM signed 16-bit little-endian mono
            val format = AudioFormat(sampleRate, 16, 1, true, false)
            val info = DataLine.Info(SourceDataLine::class.java, format)
            
            val sourceLine = AudioSystem.getLine(info) as SourceDataLine
            sourceLine.open(format, 16384)
            sourceLine.start()
            line = sourceLine

            playbackThread = Thread({
                val buffer = ByteArray(1024)
                while (isRunning.get()) {
                    try {
                        val chunk = queue.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS)
                        if (chunk != null) {
                            val processed = processVolumeAndMeter(chunk)
                            line?.write(processed, 0, processed.size)
                        }
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }, "KiwiAudioPlayback").apply {
                isDaemon = true
                priority = Thread.MAX_PRIORITY
                start()
            }
        } catch (e: Exception) {
            println("[AUDIO PLAYER ERRORE] ${e.message}")
            isRunning.set(false)
        }
    }

    fun submitAudio(frame: AudioFrame) {
        submitPcm(frame.pcmBytes)
    }

    fun submitPcm(pcmBytes: ByteArray) {
        if (!isRunning.get()) return
        // Se la coda è troppo piena (ritardi di rete), scartiamo i frame più vecchi per evitare latenza accumulata
        while (queue.size > 20) {
            queue.poll()
        }
        queue.offer(pcmBytes)
    }

    fun setVolume(vol: Float) {
        _volume.value = vol.coerceIn(0f, 1f)
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    private fun processVolumeAndMeter(rawBytes: ByteArray): ByteArray {
        val out = ByteArray(rawBytes.size)
        val muted = _isMuted.value
        val vol = if (muted) 0f else _volume.value

        var sumSq = 0.0
        val sampleCount = rawBytes.size / 2

        for (i in 0 until sampleCount) {
            val low = rawBytes[i * 2].toInt() and 0xFF
            val high = rawBytes[i * 2 + 1].toInt()
            val sample = (high shl 8) or low

            val scaled = (sample * vol).toInt().coerceIn(-32768, 32767)
            sumSq += scaled * scaled

            out[i * 2] = (scaled and 0xFF).toByte()
            out[i * 2 + 1] = ((scaled shr 8) and 0xFF).toByte()
        }

        // Calcolo livello RMS normalizzato [0, 1] per la visualizzazione grafica
        val rms = if (sampleCount > 0) sqrt(sumSq / sampleCount) / 32768.0 else 0.0
        val rawLevel = (rms * 3.5).toFloat().coerceIn(0f, 1f)
        val current = _audioLevel.value
        val smoothed = if (rawLevel > current) {
            current + (rawLevel - current) * 0.7f
        } else {
            current * 0.82f
        }
        // Aggiorna la UI solo se la variazione è significativa (≥ 0.03) ad intervallo minimo 40ms (~25 FPS)
        val now = System.currentTimeMillis()
        if (now - lastLevelEmitTime >= 40L && kotlin.math.abs(smoothed - current) >= 0.03f) {
            _audioLevel.value = smoothed
            lastLevelEmitTime = now
        }

        return out
    }

    fun stop() {
        isRunning.set(false)
        playbackThread?.interrupt()
        playbackThread = null
        queue.clear()

        try {
            line?.stop()
            line?.close()
        } catch (_: Exception) {}
        line = null
        _audioLevel.value = 0f
    }
}

