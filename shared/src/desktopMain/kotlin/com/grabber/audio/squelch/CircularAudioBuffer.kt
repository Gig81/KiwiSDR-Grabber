package com.grabber.audio.squelch

/**
 * Buffer circolare ad anello in memoria RAM per campioni PCM.
 * Memorizza costantemente gli ultimi N millisecondi di audio (es. 300-500 ms)
 * per consentire il pre-buffering (anti-troncamento della prima sillaba o nominativo).
 */
class CircularAudioBuffer(
    val sampleRate: Int = 12000,
    val maxDurationMs: Int = 1000
) {
    // 16-bit mono = 2 byte per campione
    private val bytesPerSecond = sampleRate * 2
    private val maxCapacityBytes = (bytesPerSecond * (maxDurationMs / 1000.0)).toInt().coerceAtLeast(1024)

    private val ring = ByteArray(maxCapacityBytes)
    private var writePos = 0
    private var totalBytesWritten = 0L
    private val lock = Any()

    /**
     * Inserisce nuovi campioni PCM nel buffer circolare.
     */
    fun write(pcmBytes: ByteArray, offset: Int = 0, length: Int = pcmBytes.size) {
        if (length <= 0) return
        synchronized(lock) {
            var srcPos = offset
            var remaining = length

            while (remaining > 0) {
                val spaceUntilEnd = maxCapacityBytes - writePos
                val chunk = minOf(remaining, spaceUntilEnd)
                System.arraycopy(pcmBytes, srcPos, ring, writePos, chunk)
                writePos = (writePos + chunk) % maxCapacityBytes
                srcPos += chunk
                remaining -= chunk
            }
            totalBytesWritten += length
        }
    }

    /**
     * Estrae un'istantanea (snapshot) degli ultimi [durationMs] millisecondi di audio
     * senza azzerare o consumare il buffer.
     * I dati restituiti sono ordinati cronologicamente (dal più vecchio al più recente).
     */
    fun snapshot(durationMs: Int): ByteArray {
        synchronized(lock) {
            val desiredBytes = (bytesPerSecond * (durationMs / 1000.0)).toInt().coerceIn(0, maxCapacityBytes)
            val availableBytes = minOf(totalBytesWritten, maxCapacityBytes.toLong()).toInt()
            val bytesToRead = minOf(desiredBytes, availableBytes)

            if (bytesToRead <= 0) {
                return ByteArray(0)
            }

            val out = ByteArray(bytesToRead)
            var startPos = (writePos - bytesToRead) % maxCapacityBytes
            if (startPos < 0) {
                startPos += maxCapacityBytes
            }

            val firstChunk = minOf(bytesToRead, maxCapacityBytes - startPos)
            System.arraycopy(ring, startPos, out, 0, firstChunk)

            val secondChunk = bytesToRead - firstChunk
            if (secondChunk > 0) {
                System.arraycopy(ring, 0, out, firstChunk, secondChunk)
            }

            return out
        }
    }

    /**
     * Svuota completamente il buffer circolare.
     */
    fun clear() {
        synchronized(lock) {
            writePos = 0
            totalBytesWritten = 0L
            ring.fill(0)
        }
    }
}

