package com.grabber.recording

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Scrive un file WAV PCM 16 bit mono su un canale con posizionamento (file locale su desktop,
 * descrittore MediaStore su Android). L'header viene scritto subito con dimensione 0 e
 * aggiornato da [finish]; [repairHeader] ricostruisce le dimensioni dopo un'interruzione.
 */
class WavWriter(
    private val channel: FileChannel,
    val sampleRate: Int
) : Closeable {
    var dataBytes: Long = 0L
        private set

    init {
        channel.truncate(0)
        channel.position(0)
        writeFully(ByteBuffer.wrap(header(sampleRate, 0)))
    }

    fun write(pcm: ByteArray, offset: Int = 0, length: Int = pcm.size) {
        if (length <= 0) return
        writeFully(ByteBuffer.wrap(pcm, offset, length))
        dataBytes += length
    }

    /** Aggiorna le dimensioni RIFF e `data` nell'header; il canale resta aperto. */
    fun finish() {
        writeSizes(channel, dataBytes)
        channel.force(false)
    }

    override fun close() {
        channel.close()
    }

    private fun writeFully(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) channel.write(buffer)
    }

    companion object {
        const val HEADER_SIZE = 44
        private const val MAX_DATA_BYTES = 0xFFFFFFFFL - 36

        fun header(sampleRate: Int, dataSize: Int): ByteArray =
            ByteBuffer.allocate(HEADER_SIZE).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(dataSize + 36)
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1.toShort())
                putShort(1.toShort())
                putInt(sampleRate)
                putInt(sampleRate * 2)
                putShort(2.toShort())
                putShort(16.toShort())
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataSize)
            }.array()

        /**
         * Ricalcola le dimensioni dell'header in base alla lunghezza effettiva del file, per
         * rendere riproducibile una registrazione interrotta (crash, processo terminato).
         * @param verifyRiff false se il chiamante ha già verificato l'intestazione `RIFF`
         *   (necessario con canali in sola scrittura, come quelli dei descrittori MediaStore)
         * @return byte audio presenti, oppure -1 se il file non ha un header WAV valido
         */
        fun repairHeader(channel: FileChannel, verifyRiff: Boolean = true): Long {
            val size = channel.size()
            if (size < HEADER_SIZE) return -1
            if (verifyRiff) {
                val riff = ByteBuffer.allocate(4)
                channel.read(riff, 0)
                if (String(riff.array(), Charsets.US_ASCII) != "RIFF") return -1
            }
            val dataBytes = (size - HEADER_SIZE).let { it - it % 2 }
            writeSizes(channel, dataBytes)
            channel.force(false)
            return dataBytes
        }

        private fun writeSizes(channel: FileChannel, dataBytes: Long) {
            val data = dataBytes.coerceAtMost(MAX_DATA_BYTES)
            // ByteBuffer.wrap invece di flip(): il flip() covariante non esiste su Android 10.
            channel.write(ByteBuffer.wrap(uint32LittleEndian(data + 36)), 4)
            channel.write(ByteBuffer.wrap(uint32LittleEndian(data)), 40)
        }

        private fun uint32LittleEndian(value: Long): ByteArray = byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }
}
