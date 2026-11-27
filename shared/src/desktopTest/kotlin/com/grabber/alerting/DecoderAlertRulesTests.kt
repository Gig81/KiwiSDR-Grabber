package com.grabber.alerting

import com.grabber.app.DecoderAlertRules
import com.grabber.decoder.DecodedEvent
import com.grabber.model.digital.DecodedTextLine
import com.grabber.model.digital.DigitalMode
import com.grabber.model.digital.NavtexMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecoderAlertRulesTests {
    private val words = "mayday, pan pan, IK7ABC"

    private fun navtex(id: String, body: String) =
        DecodedEvent(DigitalMode.NAVTEX, NavtexMessage(0, id[0], id[1], id.substring(2), body, 0))

    @Test
    fun matchingTextIsNotified() {
        val rules = DecoderAlertRules()
        val event = DecodedEvent(DigitalMode.RTTY, DecodedTextLine(0, "CQ CQ DE IK7ABC K"))
        assertEquals(listOf("IK7ABC"), rules.matches(event, words, 1_000))
        assertTrue(rules.matches(DecodedEvent(DigitalMode.RTTY, DecodedTextLine(0, "nothing here")), words, 2_000).isEmpty())
    }

    @Test
    fun keywordIsRateLimited() {
        val rules = DecoderAlertRules(minIntervalMs = 30_000)
        assertEquals(1, rules.matches(DecodedEvent(DigitalMode.CW, DecodedTextLine(0, "MAYDAY 1")), words, 0).size)
        assertTrue(rules.matches(DecodedEvent(DigitalMode.CW, DecodedTextLine(0, "MAYDAY 2")), words, 10_000).isEmpty())
        assertEquals(1, rules.matches(DecodedEvent(DigitalMode.CW, DecodedTextLine(0, "MAYDAY 3")), words, 31_000).size)
    }

    @Test
    fun sameNavtexMessageIsNotifiedOnce() {
        val rules = DecoderAlertRules(minIntervalMs = 0)
        assertEquals(1, rules.matches(navtex("GA12", "PAN PAN VESSEL ADRIFT"), words, 0).size)
        assertTrue(rules.matches(navtex("GA12", "PAN PAN VESSEL ADRIFT"), words, 60_000).isEmpty())
        assertEquals(1, rules.matches(navtex("GA13", "PAN PAN ANOTHER"), words, 120_000).size)
    }

    @Test
    fun statusLinesAreIgnored() {
        val rules = DecoderAlertRules()
        assertTrue(rules.matches(DecodedEvent(DigitalMode.FT8, DecodedTextLine(0, "MAYDAY", isStatus = true)), words, 0).isEmpty())
    }
}
