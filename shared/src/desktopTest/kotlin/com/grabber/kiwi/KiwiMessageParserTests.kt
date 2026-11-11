package com.grabber.kiwi

import com.grabber.model.ModulationMode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KiwiMessageParserTests {

    @Test
    fun parsesUserListAndSkipsFreeChannels() {
        val json = """[{"i":0,"n":"IK7ABC%20Mario","g":"Bari","f":27205000,"m":"am","z":0,"t":"0:12:34"},""" +
            """{"i":1},{"i":2,"n":"","f":7100000.5,"m":"lsb","t":"1:00:00"}]"""

        val users = KiwiMessageParser.parseUserList(json)

        assertEquals(
            listOf(
                KiwiUserEntry(0, "IK7ABC Mario", 27205000, "am", "0:12:34"),
                KiwiUserEntry(2, "", 7100000, "lsb", "1:00:00")
            ),
            users
        )
    }

    @Test
    fun userNamesContainingJsonDelimitersAreParsedCorrectly() {
        val json = """[{"i":3,"n":"a},{b \"quoted\"","f":14200000,"m":"usb","t":"0:00:05"}]"""

        val users = KiwiMessageParser.parseUserList(json)

        assertEquals(1, users.size)
        assertEquals("a},{b \"quoted\"", users.single().name)
    }

    @Test
    fun malformedUserNameEncodingIsKeptRaw() {
        val users = KiwiMessageParser.parseUserList("""[{"i":0,"n":"100%","f":1,"m":"am","t":""}]""")
        assertEquals("100%", users.single().name)
    }

    @Test
    fun parsesUrlEncodedDxLabelsPayload() {
        val payload = "%7B%22dx%22%3A%5B%7B%22f%22%3A27205.0%2C%22m%22%3A1%2C%22i%22%3A%22CB%20Ch%2020%22%2C%22g%22%3A%22CB%22%7D%5D%7D"

        val labels = KiwiMessageParser.parseDxLabels(payload)

        assertEquals(1, labels.size)
        assertEquals(27205.0, labels.single().f)
        assertEquals("CB Ch 20", labels.single().i)
        assertEquals(ModulationMode.AM, labels.single().mode)
    }

    @Test
    fun emptyDxPayloadProducesNoLabels() {
        assertTrue(KiwiMessageParser.parseDxLabels("").isEmpty())
        assertTrue(KiwiMessageParser.parseDxLabels(" [] ").isEmpty())
        assertTrue(KiwiMessageParser.parseDxLabels("nessun array").isEmpty())
    }

    @Test
    fun decodesBigEndianSoundFrame() {
        val frame = KiwiMessageParser.decodeSoundFrame(
            soundFrame(flags = 0x00, sequence = 0x01020304, sMeter = 270, payload = byteArrayOf(0x12, 0x34, 0x56, 0x78))
        )!!

        assertEquals(0x01020304L, frame.sequence)
        assertEquals(-100.0, frame.rssiDbm, 1e-9)
        assertContentEquals(byteArrayOf(0x34, 0x12, 0x78, 0x56), frame.pcmLittleEndian)
    }

    @Test
    fun keepsLittleEndianSoundFramePayloadUnchanged() {
        val payload = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val frame = KiwiMessageParser.decodeSoundFrame(
            soundFrame(flags = 0x80, sequence = 7, sMeter = 0, payload = payload)
        )!!

        assertContentEquals(payload, frame.pcmLittleEndian)
        assertEquals(-127.0, frame.rssiDbm, 1e-9)
    }

    @Test
    fun sequenceNumberIsUnsigned() {
        val frame = KiwiMessageParser.decodeSoundFrame(
            soundFrame(flags = 0x00, sequence = -1, sMeter = 0, payload = byteArrayOf())
        )!!

        assertEquals(0xFFFFFFFFL, frame.sequence)
        assertEquals(0, frame.pcmLittleEndian.size)
    }

    @Test
    fun truncatedSoundFrameIsIgnored() {
        assertNull(KiwiMessageParser.decodeSoundFrame(ByteArray(9)))
    }

    private fun soundFrame(flags: Int, sequence: Int, sMeter: Int, payload: ByteArray): ByteArray =
        "SND".encodeToByteArray() +
            byteArrayOf(flags.toByte()) +
            byteArrayOf(sequence.toByte(), (sequence shr 8).toByte(), (sequence shr 16).toByte(), (sequence shr 24).toByte()) +
            byteArrayOf((sMeter shr 8).toByte(), sMeter.toByte()) +
            payload
}
