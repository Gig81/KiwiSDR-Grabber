package com.grabber.app

import com.grabber.model.UiThemeMode
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class UiThemeSettingsTests {

    @Test
    fun defaultThemeIsDark() = withSettingsDir { dir ->
        assertEquals(UiThemeMode.DARK, AppSettings(dir).settings.value.uiTheme)
    }

    @Test
    fun settingsFileWithoutThemeKeepsDarkTheme() = withSettingsDir { dir ->
        File(dir, "settings.json").writeText("""{ "callsign": "IZ0TEST", "setupCompleted": true }""")
        val loaded = AppSettings(dir).settings.value
        assertEquals("IZ0TEST", loaded.callsign)
        assertEquals(UiThemeMode.DARK, loaded.uiTheme)
    }

    @Test
    fun unknownThemeNameFallsBackToDark() = withSettingsDir { dir ->
        File(dir, "settings.json").writeText("""{ "uiThemeName": "PINK", "setupCompleted": true }""")
        assertEquals(UiThemeMode.DARK, AppSettings(dir).settings.value.uiTheme)
        assertEquals(UiThemeMode.DARK, UiThemeMode.fromName(null))
    }

    @Test
    fun selectedThemeIsPersisted() = withSettingsDir { dir ->
        val settings = AppSettings(dir)
        settings.setUiTheme(UiThemeMode.LIGHT)
        assertEquals(UiThemeMode.LIGHT, settings.settings.value.uiTheme)
        assertEquals(UiThemeMode.LIGHT, AppSettings(dir).settings.value.uiTheme)
        settings.setUiTheme(UiThemeMode.SYSTEM)
        assertEquals(UiThemeMode.SYSTEM, AppSettings(dir).settings.value.uiTheme)
    }
}

/** Esegue [block] con una cartella impostazioni temporanea, rimossa al termine. */
internal fun withSettingsDir(block: (File) -> Unit) {
    val dir = Files.createTempDirectory("kiwigrabber-settings-test").toFile()
    try {
        block(dir)
    } finally {
        check(dir.deleteRecursively()) { "Impossibile rimuovere ${dir.absolutePath}" }
    }
}
