package com.grabber.kiwi.ext

import com.grabber.kiwi.ExtProtocol
import com.grabber.kiwi.ReceiverConfig
import com.grabber.waterfall.parseExtensionList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fixture: messaggi reali catturati nella fase 0 (KiwiSDR v1.902, F4KIY, 24/11/2026).
 * Solo dati di protocollo, nessun codice KiwiSDR.
 */
class ExtMessageParserTests {

    private val ft8WithLinks =
        "EXT chars=16%3a50%3a15%20-04.0%20-1.08%20%20516%20%20%20%20%20%20%20%20%20%20%201.0%20%20%3ca%20style%3d%22color%3ablue%22%20href%3d%22https%3a%2f%2fwww.qrz.com%2flookup%2fD2ACE%22%20target%3d%22_blank%22%3eD2ACE%3c%2fa%3e%20%1b%5b30m%1b%5b102m%3ca%20style%3d%22color%3ablue%22%20href%3d%22https%3a%2f%2fwww.qrz.com%2flookup%2fUR5LY%22%20target%3d%22_blank%22%3eUR5LY%3c%2fa%3e%20%3ca%20style%3d%22color%3ablue%22%20href%3d%22http%3a%2f%2fwww.levinecentral.com%2fham%2fgrid_square.php%3fGrid%3dKN89%22%20target%3d%22_blank%22%3eKN89%3c%2fa%3e%20%1b%5b0m%0a"
    private val ft8Plain = "EXT chars=16%3a50%3a15%20-05.5%20-1.08%201484%20%20%20%20%20%20%20%20%20%20%201.0%20%20R4CGV%20EA1AM%20-17%0a"
    private val ft8Cq =
        "EXT chars=16%3a50%3a15%20-07.5%20-1.16%20%20803%20%20%20%20%20%20%20%20%20%20%201.0%20%20CQ%20%1b%5b30m%1b%5b102m%3ca%20style%3d%22color%3ablue%22%20href%3d%22https%3a%2f%2fwww.qrz.com%2flookup%2f9A4AA%22%20target%3d%22_blank%22%3e9A4AA%3c%2fa%3e%20%3ca%20style%3d%22color%3ablue%22%20href%3d%22http%3a%2f%2fwww.levinecentral.com%2fham%2fgrid_square.php%3fGrid%3dJN75%22%20target%3d%22_blank%22%3eJN75%3c%2fa%3e%20%1b%5b0m%0a"
    private val ft8Header = "EXT chars=%20%20%20%20%20UTC%20%20%20SNR%20%20%20%20dT%20Freq%20%20%20%20km%20age%20msg%20%20freq%3a%207074.00%20%20mode%3a%20FT8%0a"
    private val wspr =
        "EXT WSPR_DECODED=1654%20%20%201%20-0.1%20%207.040129%20%200%20%20%3ca%20href%3d%27https%3a%2f%2fwww.qrz.com%2flookup%2fEB1A%27%20target%3d%27_blank%27%3eEB1A%20%20%3c%2fa%3e%20%3ca%20href%3d%27http%3a%2f%2fwww.levinecentral.com%2fham%2fgrid_square.php%3fGrid%3dIN53%27%20target%3d%27_blank%27%3eIN53%3c%2fa%3e%20%204920%20%20%2027%20%28501%20mW%29"
    private val extensionList =
        "%5b%22ALE_2G%22%2c%22colormap%22%2c%22CW_decoder%22%2c%22CW_skimmer%22%2c%22FAX%22%2c%22FT8%22%2c%22HFDL%22%2c%22NAVTEX%22%2c%22SSTV%22%2c%22wspr%22%5d"

    private fun chars(frame: String): String =
        ExtMessageParser.decodeComponent(requireNotNull(ExtMessageParser.parseText(frame.toByteArray())?.value("chars")))

    @Test
    fun textFramesAreSplitIntoParameters() {
        val message = assertNotNull(ExtMessageParser.parseText("EXT WSPR_STATUS=1 WSPR_TIME_MSEC=1795539135=6".toByteArray()))
        assertEquals("EXT", message.tag)
        assertEquals("1", message.value("WSPR_STATUS"))
        assertEquals("1795539135=6", message.value("WSPR_TIME_MSEC"))
        assertTrue(assertNotNull(ExtMessageParser.parseText("EXT nbins=411 ready".toByteArray())).has("ready"))
    }

    @Test
    fun percentDecodingHandlesUtf8AndKeepsPlus() {
        assertEquals("a b+c", ExtMessageParser.decodeComponent("a%20b+c"))
        assertEquals("città", ExtMessageParser.decodeComponent("citt%C3%A0"))
        assertEquals("100%", ExtMessageParser.decodeComponent("100%"))
    }

    @Test
    fun ft8LinesWithLinksAndAnsiCodesAreParsed() {
        val message = assertNotNull(ExtMessageParser.parseFt8Line(chars(ft8WithLinks), 1L))
        assertEquals("16:50:15", message.utc)
        assertEquals(-4.0, message.snrDb)
        assertEquals(-1.08, message.dtSeconds)
        assertEquals(516, message.audioHz)
        assertEquals("D2ACE UR5LY KN89", message.message)
        assertEquals("UR5LY", message.callsign)
        assertEquals("KN89", message.locator)
    }

    @Test
    fun ft8PlainAndCqLinesAreParsed() {
        val plain = assertNotNull(ExtMessageParser.parseFt8Line(chars(ft8Plain), 1L))
        assertEquals("R4CGV EA1AM -17", plain.message)
        assertEquals("EA1AM", plain.callsign)
        assertNull(plain.locator)

        val cq = assertNotNull(ExtMessageParser.parseFt8Line(chars(ft8Cq), 1L))
        assertEquals("CQ 9A4AA JN75", cq.message)
        assertTrue(cq.isCq)
        assertEquals("9A4AA", cq.callsign)
        assertEquals("JN75", cq.locator)
    }

    @Test
    fun ft8HeadersAreNotMessages() {
        assertNull(ExtMessageParser.parseFt8Line(chars(ft8Header), 1L))
        assertNull(ExtMessageParser.parseFt8Line("16:50:15 FT8 decoded 19/19, new spots 11, hashtable 2%", 1L))
    }

    @Test
    fun wsprSpotIsParsed() {
        val value = ExtMessageParser.parseText(wspr.toByteArray())?.value("WSPR_DECODED")
        val spot = assertNotNull(ExtMessageParser.parseWsprSpot(ExtMessageParser.decodeComponent(requireNotNull(value)), 1L))
        assertEquals("1654", spot.utc)
        assertEquals(1, spot.snrDb)
        assertEquals(7.040129, spot.frequencyMhz)
        assertEquals("EB1A", spot.callsign)
        assertEquals("IN53", spot.locator)
        assertEquals(27, spot.powerDbm)
        assertEquals("501 mW", spot.powerLabel)
    }

    @Test
    fun skimmerCharactersAreParsed() {
        val char = assertNotNull(ExtMessageParser.parseSkimmerChar(ExtMessageParser.decodeComponent("N1400%2c16")))
        assertEquals('N', char.char)
        assertEquals(1400, char.audioHz)
        assertEquals(16, char.wpm)
        assertEquals(' ', assertNotNull(ExtMessageParser.parseSkimmerChar(ExtMessageParser.decodeComponent("%201800%2c35"))).char)
    }

    @Test
    fun dataFramesExposeCommandAndPayload() {
        val bytes = "DAT ".toByteArray() + byteArrayOf(0xFE.toByte(), 1, 2, 3)
        val frame = assertNotNull(ExtMessageParser.parseData(bytes))
        assertEquals(254, frame.command)
        assertEquals(3, frame.payload.size)
    }

    @Test
    fun installedExtensionsAndReceiverConfigAreRead() {
        val installed = assertNotNull(parseExtensionList(extensionList))
        assertTrue("FT8" in installed && "wspr" in installed && "CW_skimmer" in installed)

        val config = ReceiverConfig.parse(
            "%7b%22ext_api_nchans%22%3a%204%2c%22ft8%22%3a%7b%22enable%22%3atrue%7d%2c%22WSPR%22%3a%7b%22enable%22%3afalse%7d%7d"
        )
        assertEquals(4, config.extApiChannels)
        assertTrue(config.isExtensionEnabled("ft8"))
        assertTrue(!config.isExtensionEnabled("WSPR"))
        assertTrue(config.isExtensionEnabled("sstv"))
    }

    @Test
    fun extensionCommandsMatchTheWebClient() {
        assertEquals("SET ext_switch_to_client=FT8 first_time=1 rx_chan=-1", ExtProtocol.switchToClient("FT8"))
        assertEquals("SET ft8_start=1 debug=0", ExtProtocol.Ft8.start(1))
        assertEquals("SET dialfreq=7039.35 centerfreq=7040.10 cf_offset=100", ExtProtocol.Wspr.frequencies(7040.1))
        assertEquals("SET dialfreq=14096.35 centerfreq=14097.10 cf_offset=100", ExtProtocol.Wspr.frequencies(14097.1))
        assertEquals("SET cw_threshold=50119", ExtProtocol.Cw.threshold(47))
        assertEquals("SET fax_start lpm=120 phasing=1 autostop=0 debug=0", ExtProtocol.Fax.start(120, phasing = true, autoStop = false))
    }
}
