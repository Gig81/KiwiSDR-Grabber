package com.grabber.model.digital

import com.grabber.kiwi.ExtProtocol
import com.grabber.model.ModulationMode
import com.grabber.model.Passband
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/**
 * Frequenza nota di un modo. [frequencyKhz] è la frequenza pubblicata: dial per FT8/FT4/SSTV/ALE,
 * frequenza centrale per WSPR, frequenza assegnata per FAX, centro del segnale per RTTY/NAVTEX/DSC.
 */
data class DigitalPreset(val label: String, val frequencyKhz: Double)

/** Sintonia richiesta da un modo: modulazione, banda passante e frequenza da mostrare (come [com.grabber.kiwi.KiwiProtocol.tune]). */
data class DigitalTuning(val modulation: ModulationMode, val passband: Passband, val frequencyKhz: Double)

/** Frequenze note e sintonia consigliata per ogni modo digitale. */
object DigitalModeCatalog {

    private val ft8 = listOf(1840.0, 3573.0, 5357.0, 7074.0, 10136.0, 14074.0, 18100.0, 21074.0, 24915.0, 28074.0)
    private val ft4 = listOf(3575.5, 7047.5, 10140.0, 14080.0, 18104.0, 21140.0, 24919.0, 28180.0)
    private val wspr = listOf(
        "LF" to 137.5, "MF" to 475.7, "160m" to 1838.1, "80m" to 3594.1, "60m" to 5288.7, "40m" to 7040.1,
        "30m" to 10140.2, "20m" to 14097.1, "17m" to 18106.1, "15m" to 21096.1, "12m" to 24926.1, "10m" to 28126.1
    )

    fun presets(mode: DigitalMode): List<DigitalPreset> = when (mode) {
        DigitalMode.FT8 -> ft8.map { DigitalPreset("${bandOf(it)} ${formatKhz(it)}", it) }
        DigitalMode.FT4 -> ft4.map { DigitalPreset("${bandOf(it)} ${formatKhz(it)}", it) }
        DigitalMode.WSPR -> wspr.map { (band, f) -> DigitalPreset("$band ${formatKhz(f)}", f) }
        DigitalMode.CW, DigitalMode.CW_SKIMMER -> listOf(
            DigitalPreset("80m 3560", 3560.0), DigitalPreset("40m 7030", 7030.0), DigitalPreset("30m 10116", 10116.0),
            DigitalPreset("20m 14060", 14060.0), DigitalPreset("Beacon NCDXF 14100", 14100.0),
            DigitalPreset("17m 18086", 18086.0), DigitalPreset("15m 21060", 21060.0)
        )
        DigitalMode.FAX -> listOf(
            DigitalPreset("DWD Amburgo 3855", 3855.0), DigitalPreset("DWD Amburgo 7880", 7880.0),
            DigitalPreset("DWD Amburgo 13882,5", 13882.5), DigitalPreset("Met Office Northwood 2618,5", 2618.5),
            DigitalPreset("Met Office Northwood 4610", 4610.0), DigitalPreset("Met Office Northwood 8040", 8040.0),
            DigitalPreset("Met Office Northwood 11086,5", 11086.5), DigitalPreset("NOAA Boston 6340,5", 6340.5),
            DigitalPreset("NOAA Boston 9110", 9110.0), DigitalPreset("Atene SVJ4 8105", 8105.0)
        )
        DigitalMode.SSTV -> listOf(
            DigitalPreset("80m 3730 (LSB)", 3730.0), DigitalPreset("40m 7171 (LSB)", 7171.0),
            DigitalPreset("20m 14230", 14230.0), DigitalPreset("20m 14233", 14233.0),
            DigitalPreset("15m 21340", 21340.0), DigitalPreset("10m 28680", 28680.0)
        )
        DigitalMode.RTTY -> listOf(
            DigitalPreset("80m 3590", 3590.0), DigitalPreset("40m 7040", 7040.0), DigitalPreset("20m 14085", 14085.0),
            DigitalPreset("DWD DDK2 4583", 4583.0), DigitalPreset("DWD DDH7 7646", 7646.0),
            DigitalPreset("DWD DDK9 10100,8", 10100.8), DigitalPreset("DWD DDH9 11039", 11039.0),
            DigitalPreset("DWD DDH8 14467,3", 14467.3)
        )
        DigitalMode.NAVTEX -> listOf(
            DigitalPreset("Internazionale 518", 518.0), DigitalPreset("Nazionale 490", 490.0),
            DigitalPreset("HF 4209,5", 4209.5)
        )
        DigitalMode.HFDL -> listOf(
            DigitalPreset("8942", 8942.0), DigitalPreset("8927", 8927.0), DigitalPreset("10081", 10081.0),
            DigitalPreset("11384", 11384.0), DigitalPreset("13276", 13276.0), DigitalPreset("17919", 17919.0),
            DigitalPreset("21949", 21949.0)
        )
        DigitalMode.ALE -> listOf(
            DigitalPreset("Radioamatori 7102", 7102.0), DigitalPreset("Radioamatori 10145,5", 10145.5),
            DigitalPreset("Radioamatori 14109", 14109.0), DigitalPreset("Radioamatori 18106", 18106.0)
        )
        DigitalMode.DSC -> listOf(
            DigitalPreset("Soccorso 2187,5", 2187.5), DigitalPreset("Soccorso 4207,5", 4207.5),
            DigitalPreset("Soccorso 6312", 6312.0), DigitalPreset("Soccorso 8414,5", 8414.5),
            DigitalPreset("Soccorso 12577", 12577.0), DigitalPreset("Soccorso 16804,5", 16804.5)
        )
    }

    /** Sintonia per [mode] alla frequenza pubblicata [frequencyKhz], come la imposta il client web KiwiSDR. */
    fun tuningFor(mode: DigitalMode, frequencyKhz: Double, params: DigitalModeParams = DigitalModeParams()): DigitalTuning =
        when (mode) {
            DigitalMode.FT8, DigitalMode.FT4 -> DigitalTuning(ModulationMode.USB, Passband(100, 3100), frequencyKhz)
            DigitalMode.WSPR -> {
                val bfo = ExtProtocol.Wspr.DEFAULT_BFO_HZ
                val half = ExtProtocol.Wspr.FILTER_WIDTH_HZ / 2
                DigitalTuning(ModulationMode.USB, Passband(bfo - half, bfo + half), frequencyKhz - bfo / 1000.0)
            }
            DigitalMode.CW -> DigitalTuning(ModulationMode.CW, Passband.defaultFor(ModulationMode.CW), frequencyKhz)
            // Banda larga centrata sulla frequenza indicata: lo skimmer decodifica tutti i segnali.
            DigitalMode.CW_SKIMMER -> DigitalTuning(ModulationMode.USB, Passband(200, 2800), frequencyKhz - 1.5)
            DigitalMode.FAX -> DigitalTuning(
                ModulationMode.USB,
                Passband(ExtProtocol.Fax.PASSBAND_LOW, ExtProtocol.Fax.PASSBAND_HIGH),
                frequencyKhz - ExtProtocol.Fax.CARRIER_OFFSET_KHZ
            )
            DigitalMode.SSTV -> if (frequencyKhz < 10000.0) {
                DigitalTuning(ModulationMode.LSB, Passband(-2500, -1000), frequencyKhz)
            } else {
                DigitalTuning(ModulationMode.USB, Passband(1000, 2500), frequencyKhz)
            }
            DigitalMode.RTTY -> fskTuning(frequencyKhz, params.rtty.centerHz, params.rtty.shiftHz, params.rtty.baud)
            DigitalMode.NAVTEX, DigitalMode.DSC -> fskTuning(frequencyKhz, SITOR_CENTER_HZ, SITOR_SHIFT_HZ, SITOR_BAUD, skirtHz = SITOR_SKIRT_HZ)
            DigitalMode.HFDL -> DigitalTuning(
                ModulationMode.USB,
                Passband(ExtProtocol.Hfdl.PASSBAND_LOW, ExtProtocol.Hfdl.PASSBAND_HIGH),
                frequencyKhz
            )
            DigitalMode.ALE -> DigitalTuning(
                ModulationMode.USB,
                Passband(ExtProtocol.Ale.PASSBAND_LOW, ExtProtocol.Ale.PASSBAND_HIGH),
                frequencyKhz
            )
        }

    /**
     * Sintonia FSK in modo CW con la banda centrata sul segnale: la frequenza mostrata coincide con il
     * centro del segnale e i toni cadono a [centerHz] ± shift/2 (es. NAVTEX 518 kHz: 365–635 Hz).
     */
    fun fskTuning(frequencyKhz: Double, centerHz: Int, shiftHz: Int, baud: Double, skirtHz: Int? = null): DigitalTuning {
        val halfSignal = max(shiftHz.toDouble(), baud) / 2.0
        val skirt = skirtHz?.toDouble() ?: (round((halfSignal * 0.2 + 10.0) / 10.0) * 10.0)
        val half = (halfSignal + skirt).toInt()
        return DigitalTuning(ModulationMode.CW, Passband(centerHz - half, centerHz + half), frequencyKhz)
    }

    /**
     * Vero se la sintonia corrente è compatibile con il modo (stessa modulazione e banda che contiene
     * quella richiesta): altrimenti la scheda propone di ripristinarla.
     */
    fun isTuningCompatible(mode: DigitalMode, modulation: ModulationMode, passband: Passband, params: DigitalModeParams): Boolean {
        val required = tuningFor(mode, 0.0, params)
        if (mode == DigitalMode.SSTV) {
            return (modulation == ModulationMode.USB || modulation == ModulationMode.LSB) &&
                abs(passband.lowCut) <= 1100 && abs(passband.highCut) >= 2400 || passband == Passband(-2500, -1000)
        }
        if (mode == DigitalMode.CW_SKIMMER) return modulation != ModulationMode.AM && modulation != ModulationMode.NFM
        return modulation == required.modulation &&
            passband.lowCut <= required.passband.lowCut + PASSBAND_TOLERANCE_HZ &&
            passband.highCut >= required.passband.highCut - PASSBAND_TOLERANCE_HZ
    }

    const val SITOR_CENTER_HZ = 500
    const val SITOR_SHIFT_HZ = 170
    const val SITOR_BAUD = 100.0
    /** Margine fisso dei menu NAVTEX/DSC del client web (365–635 Hz a 518 kHz). */
    const val SITOR_SKIRT_HZ = 50
    private const val PASSBAND_TOLERANCE_HZ = 50

    private fun formatKhz(khz: Double): String {
        val whole = khz.toLong()
        val decimals = round((khz - whole) * 10).toInt()
        return if (decimals == 0) "$whole" else "$whole,$decimals"
    }

    private fun bandOf(khz: Double): String = when {
        khz < 2000 -> "160m"
        khz < 4000 -> "80m"
        khz < 6000 -> "60m"
        khz < 8000 -> "40m"
        khz < 11000 -> "30m"
        khz < 15000 -> "20m"
        khz < 19000 -> "17m"
        khz < 22000 -> "15m"
        khz < 25000 -> "12m"
        else -> "10m"
    }
}
