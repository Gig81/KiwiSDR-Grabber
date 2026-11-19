package com.grabber.app

import com.grabber.audio.dsp.AudioDspConfig
import com.grabber.audio.dsp.DspPreset
import com.grabber.audio.dsp.DspTarget
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DspSettingsTests {

    @Test
    fun missingDspSectionGivesNeutralConfiguration() = withSettingsDir { dir ->
        File(dir, "settings.json").writeText("""{ "callsign": "IZ0TEST", "setupCompleted": true }""")
        val dsp = AppSettings(dir).settings.value.audioDsp
        assertEquals(DspTarget.OFF, dsp.target)
        assertTrue(dsp.isNeutral)
    }

    @Test
    fun unknownValuesAreNormalizedOnSave() = withSettingsDir { dir ->
        val settings = AppSettings(dir)
        settings.setAudioDsp(AudioDspConfig(targetName = "BOH", highPassHz = 99999, presetName = "BOH"))
        val saved = AppSettings(dir).settings.value.audioDsp
        assertEquals(DspTarget.OFF, saved.target)
        assertEquals(1000, saved.highPassHz)
        assertEquals(DspPreset.CUSTOM.name, saved.presetName)
    }

    @Test
    fun configurationIsPersisted() = withSettingsDir { dir ->
        val config = AudioDspConfig(targetName = DspTarget.LISTEN_AND_RECORD.name).withPreset(DspPreset.QRM_REDUCTION)
        AppSettings(dir).setAudioDsp(config)
        assertEquals(config.normalized(), AppSettings(dir).settings.value.audioDsp)
    }
}
