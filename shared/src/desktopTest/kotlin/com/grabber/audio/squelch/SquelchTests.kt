package com.grabber.audio.squelch

import com.grabber.model.AudioFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SquelchTests {

    @Test
    fun testSMeterUtils() {
        assertEquals("S0", SMeterUtils.dbmToSUnits(-127.0))
        assertEquals("S1", SMeterUtils.dbmToSUnits(-121.0))
        assertEquals("S4", SMeterUtils.dbmToSUnits(-103.0))
        assertEquals("S9", SMeterUtils.dbmToSUnits(-73.0))
        assertEquals("S9+10dB", SMeterUtils.dbmToSUnits(-63.0))
        assertEquals("S9+20dB", SMeterUtils.dbmToSUnits(-53.0))

        assertEquals(-127.0, SMeterUtils.sIndexToDbm(0))
        assertEquals(-103.0, SMeterUtils.sIndexToDbm(4))
        assertEquals(-73.0, SMeterUtils.sIndexToDbm(9))
        assertEquals(-53.0, SMeterUtils.sIndexToDbm(11))

        val normS0 = SMeterUtils.dbmToNormalized(-127.0)
        assertEquals(0.0f, normS0, 0.001f)
        val normMax = SMeterUtils.dbmToNormalized(-43.0)
        assertEquals(1.0f, normMax, 0.001f)
    }

    @Test
    fun testCircularAudioBuffer() {
        // 12000 Hz, 16-bit mono = 24 bytes/ms
        val buffer = CircularAudioBuffer(sampleRate = 12000, maxDurationMs = 500)

        // Scriviamo 240 bytes (10 ms)
        val chunk1 = ByteArray(240) { it.toByte() }
        buffer.write(chunk1)

        val snapshot = buffer.snapshot(10)
        assertEquals(240, snapshot.size)
        assertEquals(chunk1[0], snapshot[0])
        assertEquals(chunk1[239], snapshot[239])

        // Scrittura multipla
        val chunk2 = ByteArray(240) { (it + 50).toByte() }
        buffer.write(chunk2)

        val snap20ms = buffer.snapshot(20)
        assertEquals(480, snap20ms.size)
        assertEquals(chunk1[0], snap20ms[0])
        assertEquals(chunk2[239], snap20ms[479])
    }

    @Test
    fun testSoftMuteFaderRamp() {
        // 12000 Hz, 10 ms = 120 samples = 240 bytes
        val fader = SoftMuteFader(sampleRate = 12000, fadeDurationMs = 10)
        fader.reset(open = false)
        assertTrue(fader.isFullyClosed)

        // Segnale costante a 1000
        val pcm = ByteArray(240)
        for (i in 0 until 120) {
            val s = 1000
            pcm[i * 2] = (s and 0xFF).toByte()
            pcm[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }

        // Apriamo il fader: fade-in
        fader.setTargetOpen(true)
        val faded = fader.process(pcm)

        // Il primo campione deve essere vicino a 0
        val firstSample = (faded[1].toInt() shl 8) or (faded[0].toInt() and 0xFF)
        assertTrue(firstSample < 50, "First sample should be close to 0, was $firstSample")

        // L'ultimo campione della rampa deve essere vicino a 1000
        val lastSample = (faded[239].toInt() shl 8) or (faded[238].toInt() and 0xFF)
        assertTrue(lastSample > 950, "Last sample should be close to 1000, was $lastSample")
        assertTrue(fader.isFullyOpen)
    }

    @Test
    fun testSquelchFsmTransitions() {
        val squelch = SquelchController(
            sampleRate = 12000,
            initialThresholdDbm = -100.0,
            initialHysteresisDb = 2.0,
            initialSoftMuteMs = 10,
            initialPreBufferMs = 200,
            initialHangTimeSeconds = 0.3 // Hang time breve per il test
        )
        squelch.setListenSquelchEnabled(true)
        squelch.setRecordSquelchEnabled(true)

        assertEquals(SquelchGateState.CLOSED, squelch.state.value)
        assertFalse(squelch.isGateOpen.value)

        // Frame con segnale sotto soglia (-110 dBm < -100 dBm)
        val silencePcm = ByteArray(240)
        val frameLow = AudioFrame(1, -110.0, silencePcm, 12000)
        val res1 = squelch.processFrame(frameLow)
        assertEquals(SquelchGateState.CLOSED, res1.state)
        assertFalse(res1.isGateOpen)
        assertNull(res1.flushedPreBuffer)

        // Frame con segnale sopra soglia (-95 dBm >= -100 dBm) -> GATE APRE!
        val frameHigh = AudioFrame(2, -95.0, silencePcm, 12000)
        val res2 = squelch.processFrame(frameHigh)
        assertEquals(SquelchGateState.OPEN_ACTIVE, res2.state)
        assertTrue(res2.isGateOpen)
        assertNotNull(res2.flushedPreBuffer)

        // Frame con segnale tra soglia e isteresi (-101 dBm >= -100 - 2.0) -> RMANE ACTIVE
        val frameMid = AudioFrame(3, -101.0, silencePcm, 12000)
        val res3 = squelch.processFrame(frameMid)
        assertEquals(SquelchGateState.OPEN_ACTIVE, res3.state)

        // Frame con segnale che scende sotto isteresi (-105 dBm) -> ENTRA IN HANG_TIME
        val frameDrop = AudioFrame(4, -105.0, silencePcm, 12000)
        val res4 = squelch.processFrame(frameDrop)
        assertEquals(SquelchGateState.OPEN_HANG_TIME, res4.state)
        assertTrue(res4.isGateOpen)

        // Se un nuovo segnale arriva durante l'hang time -> TORNA AD ACTIVE
        val frameReturn = AudioFrame(5, -92.0, silencePcm, 12000)
        val res5 = squelch.processFrame(frameReturn)
        assertEquals(SquelchGateState.OPEN_ACTIVE, res5.state)

        // Riabbassiamo il segnale per far scadere l'hang time
        squelch.processFrame(frameDrop)
        assertEquals(SquelchGateState.OPEN_HANG_TIME, squelch.state.value)

        // Attendiamo la scadenza dell'hang time (0.3s)
        Thread.sleep(350)
        val res6 = squelch.processFrame(frameDrop)
        assertEquals(SquelchGateState.CLOSED, res6.state)
        assertFalse(res6.isGateOpen)
    }

    @Test
    fun testSquelchWithVadIntegration() {
        val squelch = SquelchController(
            sampleRate = 12000,
            initialThresholdDbm = -100.0,
            initialHangTimeSeconds = 0.5
        )
        squelch.setRecordSquelchEnabled(false)
        squelch.setVadRecordEnabled(true)

        var openedCount = 0
        squelch.onGateOpenCallback = { openedCount++ }

        // 1. Inviamo un frame sopra la soglia RF (-90 dBm) ma contenente solo silenzio/QRN (nessuna voce)
        val silencePcm = ByteArray(2400) // 100 ms
        val frameNoVoice = AudioFrame(1, -90.0, silencePcm, 12000)
        val res1 = squelch.processFrame(frameNoVoice)

        // Squelch RF è OPEN, ma Voice Gate REC non deve innescare l'apertura registrazione!
        assertTrue(res1.isGateOpen)
        assertEquals(0, openedCount, "La registrazione non deve partire su portante senza voce")

        // 2. Inviamo voce sintetica (fondamentale 150 Hz + armoniche)
        val voicePcm = ByteArray(2400)
        for (i in 0 until 1200) {
            val t = i.toDouble() / 12000
            val s = (kotlin.math.sin(2.0 * Math.PI * 150.0 * t) * 0.4 + kotlin.math.sin(2.0 * Math.PI * 600.0 * t) * 0.3) * 32767.0
            val v = s.toInt().coerceIn(-32768, 32767)
            voicePcm[i * 2] = (v and 0xFF).toByte()
            voicePcm[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        val frameVoice = AudioFrame(2, -90.0, voicePcm, 12000)
        val res2 = squelch.processFrame(frameVoice)

        // Ora la voce è rilevata: la registrazione deve aprirsi!
        assertTrue(res2.isGateOpen)
        assertEquals(1, openedCount, "La registrazione deve avviarsi quando la voce è confermata dal VAD")
    }

    @Test
    fun testRfSquelchTakesPriorityOverVoiceGate() {
        val squelch = SquelchController(
            sampleRate = 12000,
            initialThresholdDbm = -100.0
        )
        squelch.setListenSquelchEnabled(true)
        squelch.setVadListenEnabled(true)
        squelch.setRecordSquelchEnabled(true)
        squelch.setVadRecordEnabled(true)

        var recordingOpened = false
        squelch.onGateOpenCallback = { recordingOpened = true }

        val silence = ByteArray(2400)
        val result = squelch.processFrame(AudioFrame(1, -90.0, silence, 12000))

        assertTrue(result.isGateOpen, "The RF gate should open above its threshold")
        assertTrue(result.isVoiceGateOpen, "The voice gate must not override an enabled RF squelch")
        assertTrue(recordingOpened, "RF squelch should start recording without requiring VAD voice detection")
    }
}
