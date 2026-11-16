package com.grabber.recording

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals

class WavWriterTests {

    @Test
    fun finishWritesRiffAndDataSizes() {
        val file = File.createTempFile("wav_writer", ".wav").also { it.deleteOnExit() }
        RandomAccessFile(file, "rw").channel.use { channel ->
            val writer = WavWriter(channel, sampleRate = 12000)
            writer.write(ByteArray(1000) { it.toByte() })
            writer.write(ByteArray(24))
            writer.finish()
        }

        val bytes = file.readBytes()
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(WavWriter.HEADER_SIZE + 1024, bytes.size)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(1024 + 36, header.getInt(4))
        assertEquals(12000, header.getInt(24))
        assertEquals(24000, header.getInt(28))
        assertEquals(1024, header.getInt(40))
    }

    @Test
    fun repairHeaderRestoresSizesOfInterruptedRecording() {
        val file = File.createTempFile("wav_repair", ".wav").also { it.deleteOnExit() }
        RandomAccessFile(file, "rw").channel.use { channel ->
            val writer = WavWriter(channel, sampleRate = 12000)
            writer.write(ByteArray(501))
            // Nessun finish(): simula un'interruzione con header a dimensione 0.
        }

        val repaired = RandomAccessFile(file, "rw").channel.use { WavWriter.repairHeader(it) }

        val header = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(500L, repaired)
        assertEquals(500, header.getInt(40))
        assertEquals(536, header.getInt(4))
    }

    @Test
    fun repairHeaderRejectsNonWavFiles() {
        val file = File.createTempFile("not_wav", ".bin").also { it.deleteOnExit() }
        file.writeBytes(ByteArray(100) { 1 })
        assertEquals(-1L, RandomAccessFile(file, "rw").channel.use { WavWriter.repairHeader(it) })
    }
}
