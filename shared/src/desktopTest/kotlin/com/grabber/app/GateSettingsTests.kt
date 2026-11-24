package com.grabber.app

import com.grabber.audio.squelch.GateMode
import com.grabber.recording.RecordingNoiseFilterConfig
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GateSettingsTests {

    @Test
    fun legacySwitchesAreMappedToExplicitModes() {
        // Prima delle modalità esplicite, con squelch e VAD attivi decideva solo lo squelch RF.
        assertEquals(GateMode.RF, SettingsData(squelchListenEnabled = true, vadListenEnabled = true).listenGateMode)
        assertEquals(GateMode.VOICE, SettingsData(vadListenEnabled = true).listenGateMode)
        assertEquals(GateMode.OFF, SettingsData().listenGateMode)

        assertEquals(GateMode.RF, SettingsData(squelchRecordEnabled = true, vadRecordEnabled = true).recordGateMode)
        // Il vecchio VAD di registrazione da solo attendeva la voce con il gate RF aperto.
        assertEquals(GateMode.RF_AND_VOICE, SettingsData(vadRecordEnabled = true).recordGateMode)
        assertEquals(GateMode.OFF, SettingsData().recordGateMode)
    }

    @Test
    fun explicitModeWinsOverLegacySwitches() {
        val data = SettingsData(squelchListenEnabled = true, listenGateModeName = GateMode.VOICE.name)
        assertEquals(GateMode.VOICE, data.listenGateMode)
        val unknown = SettingsData(squelchRecordEnabled = true, recordGateModeName = "SCONOSCIUTA")
        assertEquals(GateMode.RF, unknown.recordGateMode)
    }

    @Test
    fun legacySettingsFileIsReadAndModesArePersisted() = withSettingsDir { dir ->
        File(dir, "settings.json").writeText(
            """{"setupCompleted":true,"squelchRecordEnabled":false,"vadRecordEnabled":true,"vadListenEnabled":true}"""
        )
        val settings = AppSettings(dir)
        assertEquals(GateMode.VOICE, settings.settings.value.listenGateMode)
        assertEquals(GateMode.RF_AND_VOICE, settings.settings.value.recordGateMode)

        settings.setListenGateMode(GateMode.RF_AND_VOICE)
        settings.setRecordGateMode(GateMode.VOICE)
        val reloaded = AppSettings(dir).settings.value
        assertEquals(GateMode.RF_AND_VOICE, reloaded.listenGateMode)
        assertEquals(GateMode.VOICE, reloaded.recordGateMode)
    }

    @Test
    fun voiceSilenceCloseIsClampedAndCanBeDisabled() = withSettingsDir { dir ->
        val settings = AppSettings(dir)
        assertTrue(settings.settings.value.voiceSilenceCloseEnabled)
        assertEquals(5.0, settings.settings.value.effectiveVoiceSilenceCloseSeconds)

        settings.setVoiceSilenceClose(enabled = true, seconds = 120.0)
        assertEquals(30.0, settings.settings.value.voiceSilenceCloseSeconds)

        settings.setVoiceSilenceClose(enabled = false, seconds = 8.0)
        val reloaded = AppSettings(dir).settings.value
        assertFalse(reloaded.voiceSilenceCloseEnabled)
        assertEquals(8.0, reloaded.voiceSilenceCloseSeconds)
        assertEquals(0.0, reloaded.effectiveVoiceSilenceCloseSeconds)
    }

    @Test
    fun noiseFilterIsNormalizedAndPersisted() = withSettingsDir { dir ->
        val settings = AppSettings(dir)
        assertEquals(RecordingNoiseFilterConfig(), settings.settings.value.recordingNoiseFilter)

        settings.setRecordingNoiseFilter(
            RecordingNoiseFilterConfig(
                minDurationSeconds = 50.0,
                noiseWords = "  uso, eh ",
                minConfidenceEnabled = true,
                minConfidence = 0.0
            )
        )
        val reloaded = AppSettings(dir).settings.value.recordingNoiseFilter
        assertEquals(10.0, reloaded.minDurationSeconds)
        assertEquals("uso, eh", reloaded.noiseWords)
        assertTrue(reloaded.minConfidenceEnabled)
        assertEquals(0.1, reloaded.minConfidence)
    }

    private fun withSettingsDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("kiwigrabber-gate-settings-test").toFile()
        try {
            block(dir)
        } finally {
            check(dir.deleteRecursively()) { "Unable to remove test directory ${dir.absolutePath}" }
        }
    }
}
