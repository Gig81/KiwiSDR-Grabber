package com.grabber.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordingNoiseFilterTests {

    private val defaults = RecordingNoiseFilterConfig()

    @Test
    fun shortSegmentsAreDiscardedOnlyWhenEnabled() {
        assertTrue(RecordingNoiseFilter.isTooShort(defaults, activeMillis = 400))
        assertFalse(RecordingNoiseFilter.isTooShort(defaults, activeMillis = 1500))
        assertFalse(RecordingNoiseFilter.isTooShort(defaults.copy(minDurationEnabled = false), activeMillis = 0))
        assertTrue(RecordingNoiseFilter.isTooShort(defaults.copy(minDurationSeconds = 3.0), activeMillis = 2999))
    }

    @Test
    fun emptyOrNoiseOnlyTranscriptionsAreDiscarded() {
        assertNotNull(RecordingNoiseFilter.discardReason(defaults, "", null))
        assertNotNull(RecordingNoiseFilter.discardReason(defaults, "uso", 0.9))
        assertNotNull(RecordingNoiseFilter.discardReason(defaults, " Uso  uso ", 0.9))
        assertNull(RecordingNoiseFilter.discardReason(defaults, "uso il canale nove", 0.9))
        assertNull(RecordingNoiseFilter.discardReason(defaults, "cq cq da ik8", 0.9))
    }

    @Test
    fun noiseWordListAcceptsCommasSpacesAndCase() {
        val config = defaults.copy(noiseWords = "Uso, eh;  ah")
        assertEquals(setOf("uso", "eh", "ah"), RecordingNoiseFilter.parseNoiseWords(config.noiseWords))
        assertNotNull(RecordingNoiseFilter.discardReason(config, "eh ah uso", 0.9))
        assertNull(RecordingNoiseFilter.discardReason(config, "eh ciao", 0.9))
    }

    @Test
    fun disabledWordFilterKeepsEmptyTranscriptions() {
        val config = defaults.copy(noiseWordsEnabled = false)
        assertNull(RecordingNoiseFilter.discardReason(config, "", null))
        assertNull(RecordingNoiseFilter.discardReason(config, "uso", 0.9))
    }

    @Test
    fun lowConfidenceIsDiscardedOnlyWhenEnabled() {
        assertNull(RecordingNoiseFilter.discardReason(defaults, "buongiorno a tutti", 0.2))
        val config = defaults.copy(minConfidenceEnabled = true, minConfidence = 0.5)
        assertNotNull(RecordingNoiseFilter.discardReason(config, "buongiorno a tutti", 0.2))
        assertNull(RecordingNoiseFilter.discardReason(config, "buongiorno a tutti", 0.8))
        assertNull(RecordingNoiseFilter.discardReason(config.copy(noiseWordsEnabled = false), "", null))
    }
}
