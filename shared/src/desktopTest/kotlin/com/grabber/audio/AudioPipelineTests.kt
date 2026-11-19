package com.grabber.audio

import com.grabber.audio.dsp.AudioDspConfig
import com.grabber.audio.dsp.DspTarget
import com.grabber.audio.dsp.sinePcm
import com.grabber.audio.squelch.SquelchController
import com.grabber.model.AudioFrame
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AudioPipelineTests {

    private class Harness(target: DspTarget, licensed: Boolean = true) {
        val squelch = SquelchController(initialThresholdDbm = -100.0, vad = null)
        val played = mutableListOf<ByteArray>()
        val recorded = mutableListOf<AudioFrame>()
        val pipeline = AudioPipeline(squelch, playbackSink = { played += it }, recordSink = { recorded += it })

        init {
            pipeline.configure(
                AudioDspConfig(targetName = target.name, highPassHz = 300, lowPassHz = 2500, notchHz = 1000),
                licensed = licensed
            )
        }
    }

    private fun frame(seq: Long, rssi: Double = -120.0) =
        AudioFrame(sequence = seq, rssi = rssi, pcmBytes = sinePcm(1000.0 + seq, 0.4, 512))

    @Test
    fun offPassesOriginalAudioEverywhere() {
        val h = Harness(DspTarget.OFF)
        val input = frame(1)
        h.pipeline.process(input)
        assertSame(input.pcmBytes, h.played.single())
        assertSame(input, h.recorded.single())
    }

    @Test
    fun listenOnlyFiltersPlaybackButNotRecording() {
        val h = Harness(DspTarget.LISTEN)
        val input = frame(1)
        h.pipeline.process(input)
        assertFalse(h.played.single().contentEquals(input.pcmBytes), "l'ascolto deve essere filtrato")
        assertSame(input, h.recorded.single(), "il registratore riceve l'originale")
    }

    @Test
    fun listenAndRecordFiltersBothWithSameAudio() {
        val h = Harness(DspTarget.LISTEN_AND_RECORD)
        val input = frame(1)
        h.pipeline.process(input)
        val recorded = h.recorded.single()
        assertFalse(recorded.pcmBytes.contentEquals(input.pcmBytes))
        assertContentEquals(recorded.pcmBytes, h.played.single(), "ascolto e registrazione uguali (squelch d'ascolto spento)")
        assertEquals(input.rssi, recorded.rssi)
        assertEquals(input.sequence, recorded.sequence)
    }

    @Test
    fun withoutLicenseDspStaysOff() {
        val h = Harness(DspTarget.LISTEN_AND_RECORD, licensed = false)
        val input = frame(1)
        h.pipeline.process(input)
        assertEquals(DspTarget.OFF, h.pipeline.effectiveTarget)
        assertSame(input.pcmBytes, h.played.single())
        assertSame(input, h.recorded.single())
    }

    @Test
    fun framesKeepFifoOrder() {
        val h = Harness(DspTarget.LISTEN_AND_RECORD)
        (1L..20L).forEach { h.pipeline.process(frame(it)) }
        assertEquals((1L..20L).toList(), h.recorded.map { it.sequence })
        assertEquals(20, h.played.size)
    }

    @Test
    fun rfGateIsUnchangedByDsp() {
        val withDsp = Harness(DspTarget.LISTEN_AND_RECORD)
        val withoutDsp = Harness(DspTarget.OFF)
        val rssiSequence = listOf(-120.0, -90.0, -90.0, -130.0)
        rssiSequence.forEachIndexed { i, rssi ->
            withDsp.pipeline.process(frame(i.toLong(), rssi))
            withoutDsp.pipeline.process(frame(i.toLong(), rssi))
            assertEquals(withoutDsp.squelch.isGateOpen.value, withDsp.squelch.isGateOpen.value)
            assertEquals(withoutDsp.squelch.state.value, withDsp.squelch.state.value)
        }
        assertTrue(withDsp.squelch.isGateOpen.value || withDsp.squelch.state.value.name.contains("HANG"))
    }

    @Test
    fun preBufferContainsFilteredAudio() {
        val h = Harness(DspTarget.LISTEN_AND_RECORD)
        var preBuffer: ByteArray? = null
        h.squelch.onGateOpenCallback = { preBuffer = it }
        h.pipeline.process(frame(1))
        h.pipeline.process(frame(2))
        h.pipeline.process(frame(3, rssi = -80.0))

        val buffered = assertNotNull(preBuffer)
        val filteredHistory = h.recorded.take(2).fold(ByteArray(0)) { acc, f -> acc + f.pcmBytes }
        // Il pre-buffer ha una rampa anti-clic iniziale: si confronta la parte finale.
        val tail = 400
        assertContentEquals(
            filteredHistory.copyOfRange(filteredHistory.size - tail, filteredHistory.size),
            buffered.copyOfRange(buffered.size - tail, buffered.size)
        )
    }
}
