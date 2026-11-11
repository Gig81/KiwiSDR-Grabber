package com.grabber.bookmarks

import com.grabber.app.AppSettings
import com.grabber.app.FavoriteFrequency
import com.grabber.app.defaultFavorites
import com.grabber.model.KiwiDxLabel
import com.grabber.model.ModulationMode
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.*

class BookmarksAndDxTests {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun testParseKiwiDxLabelsJson() {
        val rawJson = """
            [
                {"f":198,"o":0,"m":1,"w":0,"i":"BBC Radio 4 (Droitwich)","g":"LW","p":0,"e":0},
                {"f":7100,"o":0,"m":2,"w":0,"i":"40m LSB Calling","g":"HAM","p":0,"e":0},
                {"f":14200,"o":0,"m":3,"w":0,"i":"20m USB Calling","g":"HAM","p":0,"e":0},
                {"f":27205,"o":0,"m":1,"w":0,"i":"CB Ch 20 Puglia","g":"CB","p":0,"e":0}
            ]
        """.trimIndent()

        val labels = json.decodeFromString<List<KiwiDxLabel>>(rawJson)
        assertEquals(4, labels.size)

        assertEquals(198.0, labels[0].f)
        assertEquals("BBC Radio 4 (Droitwich)", labels[0].i)
        assertEquals("LW", labels[0].g)
        assertEquals(ModulationMode.AM, labels[0].mode)

        assertEquals(7100.0, labels[1].f)
        assertEquals(ModulationMode.LSB, labels[1].mode)

        assertEquals(14200.0, labels[2].f)
        assertEquals(ModulationMode.USB, labels[2].mode)

        assertEquals(27205.0, labels[3].f)
        assertEquals("CB Ch 20 Puglia", labels[3].i)
        assertEquals("CB", labels[3].g)
        assertEquals(ModulationMode.AM, labels[3].mode)
    }

    @Test
    fun testDefaultFavoritesContainsServerChannels() {
        val defaults = defaultFavorites()
        assertTrue(defaults.size >= 25, "I canali predefiniti devono contenere i canali del server (almeno 25)")
        assertTrue(defaults.any { it.name.contains("CB Ch 20 Puglia") })
        assertTrue(defaults.any { it.name.contains("40m LSB Calling") })
        assertTrue(defaults.any { it.name.contains("BBC Radio 4") })
        assertTrue(defaults.any { it.name.contains("Radio Rai 1") })
    }

    @Test
    fun testAppSettingsFavoritesPersistenceAndReset() {
        val tempDir = Files.createTempDirectory("kiwi_test_settings").toFile()
        try {
            val appSettings = AppSettings(tempDir)
            val initial = appSettings.settings.value.favorites
            assertTrue(initial.isNotEmpty())

            // Aggiungi un preferito personalizzato
            val custom = FavoriteFrequency("Stazione Test", 12345.0, "usb", "TEST")
            appSettings.addFavorite(custom)
            assertTrue(appSettings.settings.value.favorites.any { it.name == "Stazione Test" })

            // Modifica il preferito
            val modified = custom.copy(name = "Stazione Modificata", frequencyKhz = 12346.0)
            appSettings.editFavorite(custom, modified)
            assertTrue(appSettings.settings.value.favorites.any { it.name == "Stazione Modificata" && it.frequencyKhz == 12346.0 })
            assertFalse(appSettings.settings.value.favorites.any { it.name == "Stazione Test" })

            // Rimuovi il preferito
            appSettings.removeFavorite(modified)
            assertFalse(appSettings.settings.value.favorites.any { it.name == "Stazione Modificata" })

            // Ripristina i default del server
            appSettings.resetToDefaultFavorites()
            val resetList = appSettings.settings.value.favorites
            assertFalse(resetList.any { it.name == "Stazione Test" })
            assertTrue(resetList.any { it.name.contains("CB Ch 20 Puglia") })
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
