package com.grabber.waterfall

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WaterfallTests {

    @Test
    fun testWaterfallFrameCalculations() {
        val bins = ByteArray(1024) { (it % 256).toByte() }
        val frame = WaterfallFrame(
            sequenceNumber = 42L,
            bins = bins,
            zoom = 0,
            centerFreqKhz = 15000.0,
            startFreqKhz = 0.0,
            spanKhz = 30000.0,
            stopFreqKhz = 30000.0
        )

        assertEquals(1024, frame.binCount)
        assertEquals(0.0, frame.frequencyAtBin(0), 0.01)
        assertEquals(30000.0, frame.frequencyAtBin(1023), 0.01)
        assertEquals(15000.0, frame.frequencyAtBin(511), 30.0)

        // Verifica Click-to-Tune da frazione orizzontale
        assertEquals(0.0, frame.frequencyAtFraction(0.0f), 0.01)
        assertEquals(15000.0, frame.frequencyAtFraction(0.5f), 0.01)
        assertEquals(30000.0, frame.frequencyAtFraction(1.0f), 0.01)

        // Verifica conversione inversa da frequenza a bin e frazione
        assertEquals(0, frame.binAtFrequency(0.0))
        assertEquals(1023, frame.binAtFrequency(30000.0))
        assertEquals(0.5f, frame.fractionAtFrequency(15000.0), 0.001f)

        // Verifica calcolo dBm: byte 255 -> 0 dBm, byte 155 -> -100 dBm
        bins[10] = 255.toByte()
        assertEquals(0, frame.getDbm(10))

        bins[20] = 155.toByte()
        assertEquals(-100, frame.getDbm(20))

        bins[30] = 55.toByte()
        assertEquals(-200, frame.getDbm(30))
    }

    @Test
    fun testSpanCalculationAcrossZooms() {
        // Zoom 0: 30 MHz (30000 kHz)
        assertEquals(30000.0, WaterfallClient.calculateSpanKhz(0), 0.001)

        // Zoom 1: 15 MHz (15000 kHz)
        assertEquals(15000.0, WaterfallClient.calculateSpanKhz(1), 0.001)

        // Zoom 2: 7.5 MHz (7500 kHz)
        assertEquals(7500.0, WaterfallClient.calculateSpanKhz(2), 0.001)

        // Zoom 4: 1.875 MHz (1875 kHz)
        assertEquals(1875.0, WaterfallClient.calculateSpanKhz(4), 0.001)

        // Zoom 10: ~29.296 kHz
        assertEquals(30000.0 / 1024.0, WaterfallClient.calculateSpanKhz(10), 0.001)
    }

    @Test
    fun testWaterfallBinaryFrameParsing() {
        val client = WaterfallClient()
        client.setCenterFrequency(7100.0)
        client.setZoom(2) // span = 7500 kHz

        val rawPacket = ByteArray(4 + 12 + 1024)

        // Prefisso "W/F " (4 byte)
        rawPacket[0] = 'W'.code.toByte()
        rawPacket[1] = '/'.code.toByte()
        rawPacket[2] = 'F'.code.toByte()
        rawPacket[3] = ' '.code.toByte()

        // Header 12 byte: x_bin=0, flags_zoom=2, seq=1001
        val headerBuf = ByteBuffer.wrap(rawPacket, 4, 12).order(ByteOrder.LITTLE_ENDIAN)
        headerBuf.putInt(0)
        headerBuf.putInt(2)
        headerBuf.putInt(1001)

        // 1024 bin: bin 500 = 235 (pari a -20 dBm)
        rawPacket[16 + 500] = 235.toByte()

        client.parseWaterfallFrame(rawPacket)

        val frame = client.latestFrame.value
        assertTrue(frame != null, "Il frame decodificato non deve essere nullo")
        assertEquals(1001L, frame.sequenceNumber)
        assertEquals(1024, frame.binCount)
        assertEquals(2, frame.zoom)
        assertEquals(7500.0, frame.spanKhz, 0.01)
        assertEquals(-20, frame.getDbm(500))
    }

    @Test
    fun testWaterfallResumesAfterPause() {
        val client = WaterfallClient()
        val rawPacket = ByteArray(4 + 12 + 1024)
        rawPacket[0] = 'W'.code.toByte()
        rawPacket[1] = '/'.code.toByte()
        rawPacket[2] = 'F'.code.toByte()
        rawPacket[3] = ' '.code.toByte()

        fun setSequence(sequence: Int) {
            val header = ByteBuffer.wrap(rawPacket, 4, 12).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(0)
            header.putInt(0)
            header.putInt(sequence)
        }

        setSequence(1)
        client.parseWaterfallFrame(rawPacket)
        assertEquals(1L, client.latestFrame.value?.sequenceNumber)

        client.setPaused(true)
        setSequence(2)
        client.parseWaterfallFrame(rawPacket)
        assertEquals(1L, client.latestFrame.value?.sequenceNumber)

        client.setPaused(false)
        client.parseWaterfallFrame(rawPacket)
        assertEquals(2L, client.latestFrame.value?.sequenceNumber)
    }

    @Test
    fun testWaterfallPaletteLutMapping() {
        for (palette in WaterfallPaletteType.entries) {
            val lut = WaterfallPalette.getLut(palette)
            assertEquals(256, lut.size, "Ogni LUT palette deve avere esattamente 256 colori")

            // Tutti i colori devono avere canale alpha = 0xFF
            for (color in lut) {
                val alpha = (color ushr 24) and 0xFF
                assertEquals(0xFF, alpha, "Il canale alpha deve essere 255")
            }

            // Min dBm (-110) deve mappare su lut[0]
            val colorMin = WaterfallPalette.getColorForDbm(-110, minDb = -110, maxDb = -10, palette = palette)
            assertEquals(lut[0], colorMin)

            // Max dBm (-10) deve mappare su lut[255]
            val colorMax = WaterfallPalette.getColorForDbm(-10, minDb = -110, maxDb = -10, palette = palette)
            assertEquals(lut[255], colorMax)
        }
    }

    @Test
    fun testWaterfallBufferScrollingAndRecolor() {
        val width = 8
        val height = 4
        val buffer = WaterfallBuffer(width = width, height = height)

        val initialVersion = buffer.version.value

        // Inserisci riga 1
        val bins1 = ByteArray(width) { 155.toByte() } // -100 dBm
        val frame1 = WaterfallFrame(1L, bins1, 0, 15000.0, 0.0, 30000.0, 30000.0)
        buffer.addLine(frame1, minDb = -110, maxDb = -10, palette = WaterfallPaletteType.MONOCHROME)

        assertTrue(buffer.version.value > initialVersion)
        val lutMono = WaterfallPalette.getLut(WaterfallPaletteType.MONOCHROME)
        val expectedColor1 = WaterfallPalette.getColorForDbm(-100, -110, -10, WaterfallPaletteType.MONOCHROME)
        assertEquals(expectedColor1, buffer.pixels[0])

        // Inserisci riga 2
        val bins2 = ByteArray(width) { 255.toByte() } // 0 dBm
        val frame2 = WaterfallFrame(2L, bins2, 0, 15000.0, 0.0, 30000.0, 30000.0)
        buffer.addLine(frame2, minDb = -110, maxDb = -10, palette = WaterfallPaletteType.MONOCHROME)

        val expectedColor2 = WaterfallPalette.getColorForDbm(0, -110, -10, WaterfallPaletteType.MONOCHROME)

        // La riga 0 ora contiene i campioni della riga 2
        assertEquals(expectedColor2, buffer.pixels[0])
        // La riga 1 ora contiene i campioni della riga 1 (scivolata verso il basso)
        assertEquals(expectedColor1, buffer.pixels[width])

        // Test ricolorazione istantanea dello storico (cambio in Kiwi Classic)
        buffer.recolor(minDb = -110, maxDb = -10, palette = WaterfallPaletteType.KIWI_CLASSIC)
        val expectedClassic1 = WaterfallPalette.getColorForDbm(-100, -110, -10, WaterfallPaletteType.KIWI_CLASSIC)
        val expectedClassic2 = WaterfallPalette.getColorForDbm(0, -110, -10, WaterfallPaletteType.KIWI_CLASSIC)

        assertEquals(expectedClassic2, buffer.pixels[0])
        assertEquals(expectedClassic1, buffer.pixels[width])
    }
}
