package com.grabber.bookmarks

import com.grabber.app.AppSettings
import com.grabber.app.FavoriteFrequency
import com.grabber.app.defaultFavorites
import com.grabber.model.KiwiDxLabel
import com.grabber.model.ModulationMode
import com.grabber.kiwi.KiwiMessageParser
import com.grabber.kiwi.KiwiProtocol
import java.nio.file.Files
import kotlin.test.*

class BookmarksAndDxTests {

    /** Estratto di una risposta reale `MSG mkr=` (KiwiSDR v1.902) a [KiwiProtocol.dxMarkers]. */
    @Test
    fun testParseDxMarkersResponse() {
        val payload = """[{"t":4,"n":1113,"s":1794835611,"m":805,"f":0,"pe":0,"fe":0,"tc":[242,831,31,0,2,7,0,0,0,0,0,0,0,0,0,0]},""" +
            """{"g":0,"f":11.900,"lo":0,"hi":0,"o":0,"s":0,"fl":65045,"b":0,"e":2400,"c":"","l":"","t":"","i":"Alpha%20F1%20RUS","n":"navigation%20system"},""" +
            """{"g":528,"f":6980.000,"lo":0,"hi":0,"o":0,"s":0,"fl":65066,"b":0,"e":2400,"c":"","l":"","t":"","i":"Pirati%2040%20metri"},""" +
            """{"g":531,"f":7040.000,"lo":0,"hi":0,"o":0,"s":0,"fl":65026,"b":0,"e":2400,"c":"","l":"","t":"","i":"PSK%2031"},""" +
            """{"g":532,"f":7055.000,"lo":0,"hi":0,"o":0,"s":0,"fl":327169,"b":0,"e":2400,"c":"","l":"","t":"","i":"7055%20-%20AM%20ex%207045"},""" +
            """{"g":158,"f":1008.000,"lo":0,"hi":0,"o":0,"s":0,"fl":65024,"b":0,"e":2400,"c":"","l":"","t":"","i":""}]"""

        val labels = assertNotNull(KiwiMessageParser.parseDxMarkers(payload))
        assertEquals(5, labels.size)

        assertEquals(KiwiDxLabel(0, 11.9, ModulationMode.CW, "Alpha F1 RUS", "navigation system"), labels[0])
        assertEquals("Pirati 40 metri", labels[1].name)
        assertEquals(ModulationMode.LSB, labels[1].mode)   // lsn
        assertEquals(ModulationMode.USB, labels[2].mode)   // usb
        assertEquals(ModulationMode.AM, labels[3].mode)    // amw (bit DX_MODE_16)
        assertEquals("", labels[4].name)
        assertEquals(1008.0, labels[4].frequencyKhz)
    }

    @Test
    fun testParseDxMarkersIgnoresOtherResponses() {
        assertEquals(emptyList(), KiwiMessageParser.parseDxMarkers("""[{"t":4}]"""))
        assertNull(KiwiMessageParser.parseDxMarkers("""[{"t":2},{"g":1,"f":7000.0,"fl":2,"i":"x"}]"""))
    }

    @Test
    fun testDxMarkersCommandMatchesServerSyntax() {
        // Il server riconosce solo la variante a 8 parametri con maschera `0x…`.
        assertEquals(
            "SET MARKER db=0 min=0.000 max=30000.000 zoom=0 width=1024 eibi_types_mask=0xffffffff filter_tod=0 anti_clutter=0",
            KiwiProtocol.dxMarkers()
        )
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
