package com.grabber.decoder

import com.grabber.kiwi.KiwiProtocol
import com.grabber.kiwi.ReceiverConfig
import com.grabber.model.ModulationMode
import com.grabber.model.Passband
import com.grabber.model.digital.DecodedTextLine
import com.grabber.model.digital.DigitalMode
import com.grabber.model.digital.DigitalModeCatalog
import com.grabber.model.digital.DigitalModeParams
import com.grabber.model.digital.SkimmerSignal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DigitalModelTests {

    @Test
    fun cwShowsTheSignalFrequencyLikeTheWebClient() {
        // D5: frequenza mostrata = portante + centro banda (500 Hz con la banda predefinita).
        assertEquals("SET mod=cw low_cut=300 high_cut=700 freq=7029.500", KiwiProtocol.tune(ModulationMode.CW, 7030.0))
        assertEquals("SET mod=cw low_cut=365 high_cut=635 freq=517.500", KiwiProtocol.tune(ModulationMode.CW, 518.0, 365, 635))
        assertEquals("SET mod=usb low_cut=100 high_cut=3100 freq=7074.000", KiwiProtocol.tune(ModulationMode.USB, 7074.0, 100, 3100))
        assertEquals(7030.0, KiwiProtocol.displayFrequencyKhz(ModulationMode.CW, 7029.5, 300, 700))
    }

    @Test
    fun catalogTuningsMatchTheWebClient() {
        val navtex = DigitalModeCatalog.tuningFor(DigitalMode.NAVTEX, 518.0)
        assertEquals(ModulationMode.CW, navtex.modulation)
        assertEquals(Passband(365, 635), navtex.passband)
        assertEquals(518.0, navtex.frequencyKhz)

        val wspr = DigitalModeCatalog.tuningFor(DigitalMode.WSPR, 7040.1)
        assertEquals(Passband(600, 900), wspr.passband)
        assertEquals(7039.35, wspr.frequencyKhz, 1e-9)

        val fax = DigitalModeCatalog.tuningFor(DigitalMode.FAX, 7880.0)
        assertEquals(7878.1, fax.frequencyKhz, 1e-9)
        assertEquals(Passband(1300, 2500), fax.passband)

        val dwd = DigitalModeCatalog.fskTuning(10100.8, 1000, 450, 50.0)
        assertEquals(Passband(715, 1285), dwd.passband)

        assertEquals(ModulationMode.LSB, DigitalModeCatalog.tuningFor(DigitalMode.SSTV, 7171.0).modulation)
    }

    @Test
    fun tuningCompatibilityDetectsNarrowPassbands() {
        val params = DigitalModeParams()
        assertTrue(DigitalModeCatalog.isTuningCompatible(DigitalMode.FT8, ModulationMode.USB, Passband(100, 3100), params))
        assertFalse(DigitalModeCatalog.isTuningCompatible(DigitalMode.FT8, ModulationMode.USB, Passband(300, 2700), params))
        assertFalse(DigitalModeCatalog.isTuningCompatible(DigitalMode.FAX, ModulationMode.AM, Passband(-4000, 4000), params))
    }

    @Test
    fun availabilityExplainsWhyAModeCannotStart() {
        val config = ReceiverConfig(mapOf("ft8" to true, "WSPR" to false), 4)
        val list = DigitalModeAvailabilityRules.compute(true, setOf("FT8", "wspr", "FAX"), config, 20250)
        fun reason(mode: DigitalMode) = list.first { it.mode == mode }.reason
        assertEquals(DigitalModeAvailabilityRules.REASON_REQUIRES_12K, reason(DigitalMode.FT8))
        assertEquals(DigitalModeAvailabilityRules.REASON_DISABLED, reason(DigitalMode.WSPR))
        assertNull(reason(DigitalMode.FAX))
        assertEquals(DigitalModeAvailabilityRules.REASON_NOT_INSTALLED, reason(DigitalMode.SSTV))
        assertNull(reason(DigitalMode.NAVTEX))
        assertEquals(
            DigitalModeAvailabilityRules.REASON_DISCONNECTED,
            DigitalModeAvailabilityRules.compute(false, null, null, null).first().reason
        )
    }

    @Test
    fun textStreamIsSplitIntoLinesAndCompletedLinesAreReported() {
        val buffer = DecoderOutputBuffer(clock = { 1000L })
        buffer.sink.appendText("CQ CQ DE")
        buffer.sink.appendText(" IK7ABC\nK")
        assertEquals(listOf("CQ CQ DE IK7ABC", "K"), buffer.items.map { (it as DecodedTextLine).text })
        assertEquals(listOf("CQ CQ DE IK7ABC"), buffer.drainCompleted().map { it.plainText })
        assertTrue(buffer.drainCompleted().isEmpty())
    }

    @Test
    fun skimmerCharactersAreGroupedBySignal() {
        val buffer = DecoderOutputBuffer(clock = { 1000L })
        "CQ".forEach { buffer.sink.skimmer(1400, 7021.4, 18, it) }
        "TEST".forEach { buffer.sink.skimmer(800, 7020.8, 25, it) }
        val signals = buffer.items.filterIsInstance<SkimmerSignal>()
        assertEquals(listOf(7020.8, 7021.4), signals.map { it.frequencyKhz })
        assertEquals(listOf("TEST", "CQ"), signals.map { it.text })
    }

    @Test
    fun imagesAreReportedWhenCompleted() {
        val buffer = DecoderOutputBuffer(clock = { 1000L })
        buffer.setImageMode(DigitalMode.FAX)
        repeat(30) { buffer.sink.imageRow(IntArray(1024)) }
        assertEquals(30, buffer.image?.height)
        buffer.sink.newImage(1024, "Fax")
        val completed = buffer.drainCompletedImages()
        assertEquals(1, completed.size)
        assertEquals(DigitalMode.FAX, completed.single().mode)
        assertEquals(0, buffer.image?.height)
    }
}
