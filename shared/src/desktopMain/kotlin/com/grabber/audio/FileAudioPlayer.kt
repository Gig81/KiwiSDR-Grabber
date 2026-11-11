package com.grabber.audio

import com.grabber.app.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

class FileAudioPlayer {
    private class PlaybackSession(val file: File) {
        val lock = Object()
        @Volatile var stopped = false
        @Volatile var paused = false
        @Volatile var restartRequested = false
        @Volatile var startPositionMs = 0L
        @Volatile var line: SourceDataLine? = null
        @Volatile var stream: AudioInputStream? = null
        @Volatile var durationMs: Long? = null
        @Volatile var thread: Thread? = null
    }

    private val _currentlyPlayingPath = MutableStateFlow<String?>(null)
    val currentlyPlayingPath: StateFlow<String?> = _currentlyPlayingPath.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    @Volatile private var activeSession: PlaybackSession? = null

    fun play(filePath: String) {
        if (_isPlaying.value && _currentlyPlayingPath.value == filePath) {
            stop()
            return
        }

        stop()
        val file = File(filePath)
        if (!file.isFile) return

        val session = PlaybackSession(file)
        activeSession = session
        _currentlyPlayingPath.value = filePath
        _isPlaying.value = true
        _isPaused.value = false
        startPlaybackThread(session)
    }

    fun pause() {
        val session = activeSession ?: return
        if (session.stopped || session.paused) return
        session.paused = true
        session.line?.stop()
        _isPaused.value = true
    }

    fun resume() {
        val session = activeSession ?: return
        if (session.stopped || !session.paused) return
        synchronized(session.lock) {
            session.paused = false
            session.line?.start()
            session.lock.notifyAll()
        }
        _isPaused.value = false
    }

    fun seekBy(deltaMs: Long) {
        val session = activeSession ?: return
        if (session.stopped) return

        synchronized(session.lock) {
            val currentPosition = session.startPositionMs + (session.line?.microsecondPosition?.div(1000) ?: 0L)
            val target = (currentPosition + deltaMs).coerceAtLeast(0L)
            session.startPositionMs = session.durationMs?.let { target.coerceAtMost(it) } ?: target
            session.restartRequested = true
            closeActiveResources(session)
            session.lock.notifyAll()
        }
    }

    fun stop() {
        val session = activeSession ?: return
        activeSession = null
        synchronized(session.lock) {
            session.stopped = true
            closeActiveResources(session)
            session.lock.notifyAll()
        }
        session.thread?.interrupt()
        _isPlaying.value = false
        _isPaused.value = false
        _currentlyPlayingPath.value = null
    }

    private fun startPlaybackThread(session: PlaybackSession) {
        session.thread = Thread({ runPlayback(session) }, "FileAudioPlaybackThread").apply {
            isDaemon = true
            start()
        }
    }

    private fun runPlayback(session: PlaybackSession) {
        try {
            while (!session.stopped) {
                session.restartRequested = false
                val startPositionMs = session.startPositionMs
                val source = AudioSystem.getAudioInputStream(session.file)
                try {
                    val baseFormat = source.format
                    val decodedFormat = AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        baseFormat.sampleRate,
                        16,
                        baseFormat.channels,
                        baseFormat.channels * 2,
                        baseFormat.sampleRate,
                        false
                    )
                    val decoded = if (AudioSystem.isConversionSupported(decodedFormat, baseFormat)) {
                        AudioSystem.getAudioInputStream(decodedFormat, source)
                    } else if (
                        baseFormat.encoding == AudioFormat.Encoding.PCM_SIGNED &&
                        baseFormat.sampleSizeInBits == 16 &&
                        !baseFormat.isBigEndian
                    ) {
                        source
                    } else {
                        error("Formato audio non supportato: $baseFormat")
                    }

                    session.stream = decoded
                    val frameRate = decoded.format.frameRate
                    if (decoded.frameLength != AudioSystem.NOT_SPECIFIED.toLong() && frameRate > 0) {
                        session.durationMs = (decoded.frameLength * 1000.0 / frameRate).toLong()
                    }
                    skipToPosition(decoded, startPositionMs, frameRate)

                    if (session.stopped || session.restartRequested) continue

                    val info = DataLine.Info(SourceDataLine::class.java, decoded.format)
                    val line = AudioSystem.getLine(info) as SourceDataLine
                    session.line = line
                    line.open(decoded.format, 8192)
                    if (session.paused) line.stop() else line.start()

                    val buffer = ByteArray(4096)
                    var reachedEnd = false
                    while (!session.stopped && !session.restartRequested) {
                        synchronized(session.lock) {
                            while (session.paused && !session.stopped && !session.restartRequested) {
                                session.lock.wait()
                            }
                            if (!session.paused && !session.stopped && !session.restartRequested) {
                                line.start()
                            }
                        }
                        if (session.stopped || session.restartRequested) break
                        val count = decoded.read(buffer)
                        if (count < 0) {
                            reachedEnd = true
                            break
                        }
                        if (count > 0) line.write(buffer, 0, count)
                    }

                    if (reachedEnd && !session.stopped && !session.restartRequested) {
                        line.drain()
                        break
                    }
                } catch (e: Exception) {
                    if (!session.restartRequested || session.stopped) throw e
                } finally {
                    closeActiveResources(session)
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            if (!session.stopped) {
                AppLogger.error("FileAudioPlayer", "Impossibile riprodurre ${session.file.absolutePath}", e)
            }
        } finally {
            if (activeSession === session) {
                activeSession = null
                _isPlaying.value = false
                _isPaused.value = false
                _currentlyPlayingPath.value = null
            }
        }
    }

    private fun skipToPosition(stream: AudioInputStream, positionMs: Long, frameRate: Float) {
        val frameSize = stream.format.frameSize
        if (frameSize <= 0 || frameRate <= 0 || positionMs <= 0) return
        var bytesRemaining = (positionMs * frameRate / 1000.0 * frameSize).toLong()
        val buffer = ByteArray(8192)
        while (bytesRemaining > 0) {
            val skipped = stream.skip(bytesRemaining)
            if (skipped > 0) {
                bytesRemaining -= skipped
            } else {
                val read = stream.read(buffer, 0, minOf(buffer.size.toLong(), bytesRemaining).toInt())
                if (read < 0) break
                bytesRemaining -= read
            }
        }
    }

    private fun closeActiveResources(session: PlaybackSession) {
        try {
            session.line?.let { line ->
                line.stop()
                line.close()
            }
        } catch (_: Exception) {
        } finally {
            session.line = null
        }
        try {
            session.stream?.close()
        } catch (_: Exception) {
        } finally {
            session.stream = null
        }
    }
}
