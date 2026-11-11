package com.grabber.kiwi

import com.grabber.model.ModulationMode
import java.util.Locale
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class KiwiProtocolTests {

    @Test
    fun alphanumericCredentialsAreSentUnchanged() {
        assertEquals("SET auth t=kiwi p=Secret123", KiwiProtocol.auth("Secret123"))
        assertEquals("SET ident_user=IK7ABC", KiwiProtocol.identUser("IK7ABC"))
        assertEquals("SET auth t=kiwi p=", KiwiProtocol.auth(""))
    }

    @Test
    fun specialCharactersAreEncodedLikeEncodeUriComponent() {
        assertEquals("SET auth t=kiwi p=my%20pass%25word%3D1", KiwiProtocol.auth("my pass%word=1"))
        assertEquals("SET ident_user=Mario%20Rossi%20%C3%A8", KiwiProtocol.identUser("Mario Rossi è"))
        assertEquals("-_.!~*'()", KiwiProtocol.encodeComponent("-_.!~*'()"))
    }

    @Test
    fun identUserCannotInjectAdditionalParameters() {
        val command = KiwiProtocol.identUser("x mod=am freq=1")
        assertEquals("SET ident_user=x%20mod%3Dam%20freq%3D1", command)
        assertFalse(command.removePrefix("SET ").contains(' '))
    }

    @Test
    fun authCommandPasswordIsRedacted() {
        val redacted = KiwiProtocol.redactSecrets(KiwiProtocol.auth("TopSecret"))
        assertEquals("SET auth t=kiwi p=***", redacted)
        assertFalse(redacted.contains("TopSecret"))
    }

    @Test
    fun nonAuthCommandsAreNotRedacted() {
        assertEquals("SET keepalive", KiwiProtocol.redactSecrets(KiwiProtocol.keepalive))
        assertEquals("SET ident_user=IK7ABC", KiwiProtocol.redactSecrets("SET ident_user=IK7ABC"))
    }

    @Test
    fun frequencyFormattingMatchesPreviousLocaleIndependentFormat() {
        val samples = listOf(0.0, 7100.0, 27205.0, 14074.5, 7123.45678, 29999.9995, 0.0004) +
            List(2000) { Random(42 + it).nextDouble(0.0, 30000.0) }
        for (freqKhz in samples) {
            assertEquals(String.format(Locale.US, "%.3f", freqKhz), KiwiProtocol.formatKhz(freqKhz), "freq=$freqKhz")
        }
        assertEquals("SET mod=usb low_cut=300 high_cut=2700 freq=14200.000", KiwiProtocol.tune(ModulationMode.USB, 14200.0))
    }
}
